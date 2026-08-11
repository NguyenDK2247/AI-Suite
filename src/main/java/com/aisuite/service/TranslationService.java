package com.aisuite.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Calls a local LibreTranslate instance for language detection and translation.
 * Run locally with: libretranslate --host 0.0.0.0 --port 5000
 */
@Service
public class TranslationService {

    private final String baseUrl;
    private final HttpClient http;

    // ── Language name → ISO 639-1 code map ───────────────────────────────────
    private static final Map<String, String> LANG_MAP;
    static {
        LANG_MAP = new java.util.HashMap<>();
        LANG_MAP.put("english", "en");
        LANG_MAP.put("eng", "en");
        LANG_MAP.put("vietnamese", "vi");
        LANG_MAP.put("viet", "vi");
        LANG_MAP.put("french", "fr");
        LANG_MAP.put("français", "fr");
        LANG_MAP.put("spanish", "es");
        LANG_MAP.put("español", "es");
        LANG_MAP.put("german", "de");
        LANG_MAP.put("deutsch", "de");
        LANG_MAP.put("chinese", "zh");
        LANG_MAP.put("mandarin", "zh");
        LANG_MAP.put("japanese", "ja");
        LANG_MAP.put("korean", "ko");
        LANG_MAP.put("portuguese", "pt");
        LANG_MAP.put("italian", "it");
        LANG_MAP.put("italiano", "it");
        LANG_MAP.put("russian", "ru");
        LANG_MAP.put("arabic", "ar");
        LANG_MAP.put("hindi", "hi");
        LANG_MAP.put("turkish", "tr");
        LANG_MAP.put("dutch", "nl");
        LANG_MAP.put("polish", "pl");
        LANG_MAP.put("swedish", "sv");
        LANG_MAP.put("indonesian", "id");
        LANG_MAP.put("thai", "th");
        LANG_MAP.put("greek", "el");
        LANG_MAP.put("hebrew", "he");
        LANG_MAP.put("czech", "cs");
        LANG_MAP.put("romanian", "ro");
        LANG_MAP.put("hungarian", "hu");
        LANG_MAP.put("danish", "da");
        LANG_MAP.put("finnish", "fi");
        LANG_MAP.put("norwegian", "no");
        LANG_MAP.put("ukrainian", "uk");
    }

    public TranslationService(@Value("${app.libretranslate.url}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();
    }

    // ── Translate with retry ──────────────────────────────────────────────────
    public TranslationResult translate(String text, String sourceLang, String targetLang)
            throws IOException, InterruptedException {

        if (sourceLang == null || sourceLang.equals("auto")) {
            DetectResult detected = detectWithRetry(text);
            sourceLang = detected.languageCode();
        }

        String body = "{\"q\":" + jsonStr(text)
                + ",\"source\":\"" + sourceLang + "\""
                + ",\"target\":\"" + targetLang + "\""
                + ",\"format\":\"text\"}";

        String responseBody = postWithRetry(baseUrl + "/translate", body, 45);

        String translated = extractString(responseBody, "translatedText");
        if (translated == null || translated.isBlank())
            throw new IOException("Empty translation response from LibreTranslate.");

        return new TranslationResult(
                text, translated, sourceLang, targetLang,
                langName(sourceLang), langName(targetLang));
    }

    // ── Detect with retry ─────────────────────────────────────────────────────
    public DetectResult detect(String text) throws IOException, InterruptedException {
        return detectWithRetry(text);
    }

    private DetectResult detectWithRetry(String text)
            throws IOException, InterruptedException {
        String body = "{\"q\":" + jsonStr(text) + "}";
        String responseBody = postWithRetry(baseUrl + "/detect", body, 20);
        String lang = extractString(responseBody, "language");
        double confidence = extractDouble(responseBody, "confidence");
        return new DetectResult(lang != null ? lang : "en", confidence);
    }

    // ── POST with up to 2 retries on connection reset ─────────────────────────
    private String postWithRetry(String url, String body, int timeoutSeconds)
            throws IOException, InterruptedException {

        IOException lastException = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                        .POST(HttpRequest.BodyPublishers.ofString(body,
                                StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> res = http.send(req,
                        HttpResponse.BodyHandlers.ofString());

                if (res.statusCode() != 200)
                    throw new IOException("LibreTranslate error "
                            + res.statusCode() + ": " + res.body());

                return res.body();

            } catch (IOException e) {
                lastException = e;
                if (attempt < 3) {
                    System.out.println("LibreTranslate attempt " + attempt
                            + " failed (" + e.getMessage()
                            + "), retrying in 2s...");
                    Thread.sleep(2000);
                }
            }
        }
        throw new IOException("LibreTranslate failed after 3 attempts: "
                + (lastException != null ? lastException.getMessage() : "unknown"), lastException);
    }

    // ── Result records ────────────────────────────────────────────────────────
    public record TranslationResult(
            String originalText,
            String translatedText,
            String sourceLanguage,
            String targetLanguage,
            String sourceLangName,
            String targetLangName) {
    }

    public record DetectResult(String languageCode, double confidence) {
    }

    // ── Resolve language name to ISO code ─────────────────────────────────────
    public String resolveLanguageCode(String raw) {
        if (raw == null)
            return null;
        String lower = raw.toLowerCase().trim();

        // Already an ISO code (2 letters)
        if (raw.matches("[A-Za-z]{2}"))
            return raw.toLowerCase();

        return LANG_MAP.getOrDefault(lower, null);
    }

    // ── Language code → display name ──────────────────────────────────────────
    @SuppressWarnings("null")
    private String langName(String code) {
        return LANG_MAP.entrySet().stream()
                .filter(e -> e.getValue().equals(code)
                        && !e.getKey().contains(" ") // prefer single-word names
                        && e.getKey().equals(e.getKey().toLowerCase()))
                .map(Map.Entry::getKey)
                .findFirst()
                .map(s -> s.substring(0, 1).toUpperCase() + s.substring(1))
                .orElse(code.toUpperCase());
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────
    private String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int i = json.indexOf(search);
        if (i == -1)
            return null;
        int start = i + search.length();
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\')
                break;
            end++;
        }
        return unescapeJson(json.substring(start, end));
    }

    private String unescapeJson(String s) {
        s = s.replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\r", "\r")
                .replace("\\t", "\t");

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

    private double extractDouble(String json, String key) {
        String search = "\"" + key + "\":";
        int i = json.indexOf(search);
        if (i == -1)
            return 0.0;
        int start = i + search.length();
        while (start < json.length() && json.charAt(start) == ' ')
            start++;
        int end = start;
        while (end < json.length() &&
                (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.'))
            end++;
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String jsonStr(String s) {
        return "\"" + s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "") + "\"";
    }
}
