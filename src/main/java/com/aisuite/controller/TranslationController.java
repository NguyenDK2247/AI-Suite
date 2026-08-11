package com.aisuite.controller;

import com.aisuite.service.GroqService;
import com.aisuite.service.RagService;
import com.aisuite.service.TokenService;
import com.aisuite.service.TranslationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.*;

@RestController
public class TranslationController {

    private static final String COLLECTION = "translation_knowledge";

    // ── Patterns ──────────────────────────────────────────────────────────────

    // Matches: translate "hello" to Vietnamese
    // translate 'bonjour' from French to English
    // how do you say "thank you" in Japanese
    // what is "xin chào" in English
    private static final Pattern TRANSLATE_PATTERN = Pattern.compile(
            "(?i)(?:translate|convert|say|mean|means|is)\\s+" +
                    "(?:\"([^\"]+)\"|'([^']+)'|([\\w\\s]{1,100}?))\\s+" +
                    "(?:(?:from\\s+([A-Za-z]+)\\s+)?(?:to|in|into)\\s+([A-Za-z]+(?:\\s+[A-Za-z]+)?))" +
                    "|(?:how\\s+(?:do\\s+you\\s+)?say\\s+)" +
                    "(?:\"([^\"]+)\"|'([^']+)'|([\\w\\s]{1,100}?))\\s+" +
                    "(?:in|to)\\s+([A-Za-z]+(?:\\s+[A-Za-z]+)?)");

    // Topic guard — must contain translation intent
    private static final Pattern TOPIC_PATTERN = Pattern.compile(
            "(?i)\\b(translate|translation|translating|convert|say|" +
                    "language|languages|mean|means|word|phrase|sentence|text|" +
                    "english|french|spanish|german|vietnamese|chinese|japanese|" +
                    "korean|portuguese|italian|russian|arabic|hindi|turkish|" +
                    "dutch|polish|swedish|indonesian|thai|greek|hebrew|" +
                    "how do you say|what is .+ in|what does .+ mean)\\b");

    private final TranslationService translationService;
    private final GroqService groqService;
    private final RagService ragService;
    private final TokenService tokenService;

    public TranslationController(TranslationService translationService,
            @Qualifier("translationGroq") GroqService groqService,
            RagService ragService,
            TokenService tokenService) {
        this.translationService = translationService;
        this.groqService = groqService;
        this.ragService = ragService;
        this.tokenService = tokenService;
    }

    @PostMapping("/translate-chat")
    public ResponseEntity<Map<String, Object>> translate(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String userMessage = body.get("message");
        if (userMessage == null || userMessage.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No message provided."));

        // Topic guard
        if (!TOPIC_PATTERN.matcher(userMessage).find()) {
            return ResponseEntity.ok(Map.of("reply",
                    "I can only help with translation and language questions. "
                            + "Try: \"Translate 'hello' to Vietnamese\" or "
                            + "\"How do you say 'thank you' in Japanese?\""));
        }

        try {
            // RAG retrieval first
            String ragContext = "";
            try {
                ragContext = ragService.retrieve(userMessage, COLLECTION);
            } catch (Exception e) {
                System.err.println("RAG retrieval skipped: " + e.getMessage());
            }

            // Try to extract text and languages from the message
            TranslationService.TranslationResult translation = detectAndTranslate(userMessage);

            String promptForGroq;
            Object translationData = null;

            if (translation != null) {
                promptForGroq = userMessage + "\n\n[Translation result: \""
                        + translation.originalText() + "\" ("
                        + translation.sourceLangName() + ") → \""
                        + translation.translatedText() + "\" ("
                        + translation.targetLangName() + ")]";

                translationData = Map.of(
                        "originalText", translation.originalText(),
                        "translatedText", translation.translatedText(),
                        "sourceLanguage", translation.sourceLanguage(),
                        "targetLanguage", translation.targetLanguage(),
                        "sourceLangName", translation.sourceLangName(),
                        "targetLangName", translation.targetLangName());
            } else {
                promptForGroq = userMessage;
            }

            if (!ragContext.isEmpty())
                promptForGroq = ragContext + "\n\n" + promptForGroq;

            GroqService.ChatResult chatResult = groqService.chatWithUsage(promptForGroq);
            String reply = chatResult.reply();
            GroqService.TokenUsage usage = chatResult.usage();

            int userId = (int) request.getAttribute("userId");
            tokenService.addTokens(userId, usage.totalTokens());
            int todayTotal = tokenService.getTodayTotal(userId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("reply", reply);
            if (translationData != null)
                response.put("translationData", translationData);
            response.put("tokenUsage", Map.of(
                    "promptTokens", usage.promptTokens(),
                    "completionTokens", usage.completionTokens(),
                    "totalTokens", usage.totalTokens(),
                    "todayTotal", todayTotal,
                    "dailyLimit", tokenService.getDailyLimit()));
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Translation failed.";
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", msg));
        }
    }

    // ── Extract text and languages from the message ───────────────────────────
    private TranslationService.TranslationResult detectAndTranslate(String message)
            throws Exception {

        Matcher m = TRANSLATE_PATTERN.matcher(message);
        if (!m.find())
            return null;

        // Extract the text to translate (quoted or unquoted)
        String text = firstNonNull(m.group(1), m.group(2), m.group(3),
                m.group(6), m.group(7), m.group(8));
        if (text == null || text.isBlank())
            return null;
        text = text.trim();

        // Extract source language (optional) and target language
        String sourceLangRaw = m.group(4);
        String targetLangRaw = firstNonNull(m.group(5), m.group(9));
        if (targetLangRaw == null)
            return null;

        String targetCode = translationService.resolveLanguageCode(targetLangRaw.trim());
        if (targetCode == null)
            return null;

        String sourceCode = sourceLangRaw != null
                ? translationService.resolveLanguageCode(sourceLangRaw.trim())
                : "auto";

        return translationService.translate(text, sourceCode, targetCode);
    }

    private String firstNonNull(String... values) {
        for (String v : values)
            if (v != null && !v.isBlank())
                return v;
        return null;
    }
}
