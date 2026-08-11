package com.aisuite.service;

import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.util.ArrayList;
import java.util.List;

// Not @Service — instantiated manually in AgentConfig
public class GroqService {

    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile";

    public static final String WEATHER_PROMPT = """
            You are a friendly weather assistant. Your job is to give clear,
            helpful weather commentary when given live weather data.
            When you receive weather data, respond naturally — mention the temperature,
            how it feels, conditions, humidity, wind, and give a practical tip.
            Keep responses to 3-5 sentences. Friendly, conversational tone.
            If no weather data is provided, ask the user which city they want.
            """;

    public static final String CURRENCY_PROMPT = """
            You are a knowledgeable currency and foreign exchange assistant.
            Your sole purpose is to help users with currency conversion questions,
            exchange rates, and forex topics.
            When asked about a conversion, give the approximate rate and converted amount,
            note that rates fluctuate and suggest the user verify with a live source.
            Keep responses to 3-5 sentences. Friendly, conversational tone.
            If the user asks about anything other than currency, politely redirect them.
            """;

    public static final String TRANSLATION_PROMPT = """
            You are a friendly and knowledgeable language and translation assistant.
            Your job is to help users translate text between languages and answer
            questions about languages, grammar, pronunciation, and culture.
            When given a translation result, present it naturally — mention the original
            text, the translation, and optionally a usage tip or cultural note.
            Keep responses to 3-5 sentences. Friendly, conversational tone.
            If asked something unrelated to language or translation, politely redirect.
            """;

    private final String apiKey;
    private final String systemPrompt;
    private final HttpClient http;
    private final List<String[]> history = new ArrayList<>();

    // Single-arg constructor — used by Spring only as base (AgentConfig)
    public GroqService(@Value("${app.groq.api-key}") String apiKey) {
        this(apiKey, WEATHER_PROMPT);
    }

    // Two-arg constructor — used by AgentConfig to create agent-specific instances
    public GroqService(String apiKey, String systemPrompt) {
        this.apiKey = apiKey;
        this.systemPrompt = systemPrompt;
        this.http = HttpClient.newHttpClient();
    }

    // ── Token usage tracking ──────────────────────────────────────────────────
    public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
    }

    public record ChatResult(String reply, TokenUsage usage) {
    }

    private int sessionTotalTokens = 0;

    public int getSessionTotalTokens() {
        return sessionTotalTokens;
    }

    public ChatResult chatWithUsage(String userMessage)
            throws IOException, InterruptedException {
        history.add(new String[] { "user", userMessage });

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody()))
                .build();

        HttpResponse<String> response = http.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
            throw new IOException("Groq API error: " + response.statusCode()
                    + " — " + response.body());

        String reply = extractReply(response.body());
        TokenUsage usage = extractUsage(response.body());

        sessionTotalTokens += usage.totalTokens();
        history.add(new String[] { "assistant", reply });
        return new ChatResult(reply, usage);
    }

    // Keep the old method for compatibility
    public String chat(String userMessage) throws IOException, InterruptedException {
        return chatWithUsage(userMessage).reply();
    }

    public void resetHistory() {
        history.clear();
        sessionTotalTokens = 0;
    }

    private String buildRequestBody() {
        StringBuilder messages = new StringBuilder("[");
        messages.append("{\"role\":\"system\",\"content\":\"")
                .append(escape(systemPrompt)).append("\"}");
        for (String[] entry : history) {
            messages.append(",{\"role\":\"").append(entry[0]).append("\",")
                    .append("\"content\":\"").append(escape(entry[1])).append("\"}");
        }
        messages.append("]");
        return "{\"model\":\"" + MODEL + "\","
                + "\"max_tokens\":1000,"
                + "\"messages\":" + messages + "}";
    }

    private TokenUsage extractUsage(String json) {
        int promptTokens = extractInt(json, "prompt_tokens");
        int completionTokens = extractInt(json, "completion_tokens");
        int totalTokens = extractInt(json, "total_tokens");
        return new TokenUsage(promptTokens, completionTokens, totalTokens);
    }

    private int extractInt(String json, String key) {
        String search = "\"" + key + "\":";
        int i = json.indexOf(search);
        if (i == -1)
            return 0;
        int start = i + search.length();
        while (start < json.length() && json.charAt(start) == ' ')
            start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end)))
            end++;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String extractReply(String json) {
        int idx = json.indexOf("\"content\":");
        if (idx == -1)
            return "Sorry, I could not understand the response.";
        int start = json.indexOf('"', idx + 10) + 1;
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\')
                break;
            end++;
        }
        return unescape(json.substring(start, end));
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String unescape(String s) {
        s = s.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\r", "\r")
                .replace("\\t", "\t");

        // Decode \\uXXXX Unicode escapes (e.g. Chinese, Japanese, Korean characters)
        if (!s.contains("\\u"))
            return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (i + 5 < s.length()
                    && s.charAt(i) == '\\'
                    && s.charAt(i + 1) == 'u'
                    && isHex(s.charAt(i + 2))
                    && isHex(s.charAt(i + 3))
                    && isHex(s.charAt(i + 4))
                    && isHex(s.charAt(i + 5))) {
                int codePoint = Integer.parseInt(s.substring(i + 2, i + 6), 16);
                sb.appendCodePoint(codePoint);
                i += 6;
            } else {
                sb.append(s.charAt(i));
                i++;
            }
        }
        return sb.toString();
    }

    private boolean isHex(char c) {
        return (c >= '0' && c <= '9')
                || (c >= 'a' && c <= 'f')
                || (c >= 'A' && c <= 'F');
    }
}
