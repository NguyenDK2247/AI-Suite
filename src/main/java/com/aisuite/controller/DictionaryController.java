package com.aisuite.controller;

import com.aisuite.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.*;
import java.util.regex.*;

@RestController
public class DictionaryController {

    private static final String COLLECTION = "dictionary_knowledge";

    // Topic guard — must mention a word/definition intent
    private static final Pattern TOPIC_PATTERN = Pattern.compile(
            "(?i)\\b(define|definition|meaning|means|mean|what is|what's|" +
                    "dictionary|word|vocabulary|synonym|antonym|example|pronunciation|" +
                    "phonetic|spell|spelling|etymology|origin|usage|phrase|idiom|" +
                    "what does .+ mean|look up|lookup)\\b");

    // Extract the word being looked up
    // Matches: define "serendipity", what does "ephemeral" mean,
    // what is the meaning of resilience, look up "ubiquitous"
    private static final Pattern WORD_PATTERN = Pattern.compile(
            "(?i)(?:define|definition of|meaning of|what does|what is|look\\s*up|" +
                    "explain|tell me about|what's)\\s+" +
                    "(?:the\\s+(?:word|meaning|definition)\\s+(?:of\\s+)?)?" +
                    "(?:\"([^\"]+)\"|'([^']+)'|([A-Za-z][A-Za-z\\s\\-]{0,30}?))" +
                    "(?:\\s+mean(?:s)?|\\s+mean\\?|\\?|$)");

    private final DictionaryService dictionaryService;
    private final GroqService groqService;
    private final RagService ragService;
    private final TokenService tokenService;

    public DictionaryController(DictionaryService dictionaryService,
            @Qualifier("dictionaryGroq") GroqService groqService,
            RagService ragService,
            TokenService tokenService) {
        this.dictionaryService = dictionaryService;
        this.groqService = groqService;
        this.ragService = ragService;
        this.tokenService = tokenService;
    }

    @PostMapping("/dictionary-chat")
    public ResponseEntity<Map<String, Object>> dictionary(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String userMessage = body.get("message");
        if (userMessage == null || userMessage.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No message provided."));

        // Topic guard
        if (!TOPIC_PATTERN.matcher(userMessage).find()) {
            return ResponseEntity.ok(Map.of("reply",
                    "I can only help with word definitions and vocabulary questions. " +
                            "Try: \"Define serendipity\" or \"What does ephemeral mean?\""));
        }

        try {
            // RAG first
            String ragContext = "";
            try {
                ragContext = ragService.retrieve(userMessage, COLLECTION);
            } catch (Exception e) {
                System.err.println("RAG retrieval skipped: " + e.getMessage());
            }

            // Try to extract the word and look it up
            String word = detectWord(userMessage);
            DictionaryService.DictionaryResult dict = null;
            String promptForGroq;
            Object dictData = null;

            if (word != null) {
                try {
                    dict = dictionaryService.lookup(word);
                    promptForGroq = buildPrompt(userMessage, dict);
                    dictData = buildDictData(dict);
                } catch (IOException e) {
                    // Word not found — let Groq answer from training knowledge
                    promptForGroq = userMessage + "\n\n[Note: " + e.getMessage() + "]";
                }
            } else {
                promptForGroq = userMessage;
            }

            if (!ragContext.isEmpty())
                promptForGroq = ragContext + "\n\n" + promptForGroq;

            GroqService.ChatResult result = groqService.chatWithUsage(promptForGroq);
            String reply = result.reply();
            GroqService.TokenUsage usage = result.usage();

            int userId = (int) request.getAttribute("userId");
            tokenService.addTokens(userId, usage.totalTokens());
            int todayTotal = tokenService.getTodayTotal(userId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("reply", reply);
            if (dictData != null)
                response.put("dictData", dictData);
            response.put("tokenUsage", Map.of(
                    "promptTokens", usage.promptTokens(),
                    "completionTokens", usage.completionTokens(),
                    "totalTokens", usage.totalTokens(),
                    "todayTotal", todayTotal,
                    "dailyLimit", tokenService.getDailyLimit()));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() != null
                            ? e.getMessage()
                            : "Unknown error"));
        }
    }

    // ── Extract the word from the message ─────────────────────────────────────
    private String detectWord(String message) {
        Matcher m = WORD_PATTERN.matcher(message);
        if (m.find()) {
            String word = firstNonNull(m.group(1), m.group(2), m.group(3));
            if (word != null)
                return word.trim();
        }
        return null;
    }

    // ── Build the Groq prompt with dictionary data ────────────────────────────
    private String buildPrompt(String userMessage,
            DictionaryService.DictionaryResult dict) {
        StringBuilder sb = new StringBuilder(userMessage);
        sb.append("\n\n[Dictionary data for \"").append(dict.word()).append("\":\n");
        if (dict.phonetic() != null)
            sb.append("Phonetic: ").append(dict.phonetic()).append("\n");
        for (DictionaryService.Meaning m : dict.meanings()) {
            sb.append("(").append(m.partOfSpeech()).append(")\n");
            for (DictionaryService.Definition d : m.definitions()) {
                sb.append("  - ").append(d.definition());
                if (d.example() != null)
                    sb.append(" | Example: \"").append(d.example()).append("\"");
                sb.append("\n");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    // ── Build the dict data object for the frontend card ──────────────────────
    private Object buildDictData(DictionaryService.DictionaryResult dict) {
        List<Map<String, Object>> meanings = new ArrayList<>();
        for (DictionaryService.Meaning m : dict.meanings()) {
            List<Map<String, Object>> defs = new ArrayList<>();
            for (DictionaryService.Definition d : m.definitions()) {
                Map<String, Object> defMap = new LinkedHashMap<>();
                defMap.put("definition", d.definition());
                if (d.example() != null)
                    defMap.put("example", d.example());
                defs.add(defMap);
            }
            meanings.add(Map.of("partOfSpeech", m.partOfSpeech(), "definitions", defs));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("word", dict.word());
        if (dict.phonetic() != null)
            result.put("phonetic", dict.phonetic());
        result.put("meanings", meanings);
        return result;
    }

    private String firstNonNull(String... values) {
        for (String v : values)
            if (v != null && !v.isBlank())
                return v;
        return null;
    }
}
