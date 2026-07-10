import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class GroqService {

    // Groq uses an OpenAI-compatible API endpoint
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "llama-3.3-70b-versatile"; // fast, free on Groq

    private final String apiKey;
    private final HttpClient http;
    private final List<String[]> history; // each entry: [role, content]

    private static final String SYSTEM_PROMPT = """
            You are a friendly weather assistant. Your job is to give clear,
            helpful weather commentary when given live weather data.

            When you receive weather data, respond naturally — mention the temperature,
            how it feels, conditions, humidity, wind, and give a practical tip
            (e.g. "bring an umbrella", "great day for a walk", "stay hydrated").

            Keep responses to 3-5 sentences. Friendly, conversational tone.
            If no weather data is provided, ask the user which city they want.
            """;

    public GroqService(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newHttpClient();
        this.history = new ArrayList<>();
    }

    public void resetHistory() {
        history.clear();
    }

    public String chat(String userMessage) throws IOException, InterruptedException {
        history.add(new String[] { "user", userMessage });

        String body = buildRequestBody();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey) // Groq uses Bearer token, not x-api-key
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Groq API error: " + response.statusCode()
                    + " — " + response.body());
        }

        String reply = extractReply(response.body());
        history.add(new String[] { "assistant", reply });
        return reply;
    }

    // Groq uses OpenAI's format: system message goes inside the messages array
    private String buildRequestBody() {
        StringBuilder messages = new StringBuilder("[");

        // System message is the first entry in the messages array (not a top-level
        // field)
        messages.append("{\"role\":\"system\",\"content\":\"")
                .append(escape(SYSTEM_PROMPT))
                .append("\"}");

        for (String[] entry : history) {
            messages.append(",{\"role\":\"").append(entry[0]).append("\",")
                    .append("\"content\":\"").append(escape(entry[1])).append("\"}");
        }

        messages.append("]");

        return "{"
                + "\"model\":\"" + MODEL + "\","
                + "\"max_tokens\":1000,"
                + "\"messages\":" + messages
                + "}";
    }

    // Groq response format: choices[0].message.content
    private String extractReply(String json) {
        int contentIndex = json.indexOf("\"content\":");
        if (contentIndex == -1)
            return "Sorry, I could not understand the response.";
        int start = json.indexOf('"', contentIndex + 10) + 1;
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\')
                break;
            end++;
        }
        return unescape(json.substring(start, end));
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescape(String s) {
        return s.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}