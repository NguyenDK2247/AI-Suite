package com.aisuite.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls the Free Dictionary API (no key required).
 * Endpoint: https://api.dictionaryapi.dev/api/v2/entries/en/{word}
 */
@Service
public class DictionaryService {

    private static final String BASE_URL = "https://api.dictionaryapi.dev/api/v2/entries/en/";
    private static final int MAX_DEFS = 3; // definitions per meaning to show

    private final HttpClient http;

    public DictionaryService() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .build();
    }

    // ── Result records ────────────────────────────────────────────────────────
    public record Definition(String definition, String example) {
    }

    public record Meaning(String partOfSpeech, List<Definition> definitions) {
    }

    public record DictionaryResult(
            String word,
            String phonetic,
            List<Meaning> meanings) {
    }

    // ── Look up a word ────────────────────────────────────────────────────────
    public DictionaryResult lookup(String word) throws IOException, InterruptedException {
        String encoded = word.trim().toLowerCase().replace(" ", "%20");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + encoded))
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(10))
                .GET()
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() == 404)
            throw new IOException("Word not found: \"" + word + "\". Check spelling and try again.");
        if (res.statusCode() != 200)
            throw new IOException("Dictionary API error: HTTP " + res.statusCode());

        return parse(res.body(), word);
    }

    // ── Parse the JSON response ───────────────────────────────────────────────
    private DictionaryResult parse(String json, String originalWord) {
        // Extract word
        String word = extractString(json, "\"word\"");
        if (word == null)
            word = originalWord;

        // Extract first phonetic that isn't empty
        String phonetic = extractPhonetic(json);

        // Extract meanings
        List<Meaning> meanings = extractMeanings(json);

        return new DictionaryResult(word, phonetic, meanings);
    }

    private String extractPhonetic(String json) {
        // Try top-level "phonetic" field first
        String phonetic = extractString(json, "\"phonetic\"");
        if (phonetic != null && !phonetic.isBlank())
            return phonetic;

        // Fall back to first phonetic in "phonetics" array that has text
        int phoneticsIdx = json.indexOf("\"phonetics\"");
        if (phoneticsIdx == -1)
            return null;
        int arrStart = json.indexOf("[", phoneticsIdx);
        int arrEnd = json.indexOf("]", arrStart);
        if (arrStart == -1 || arrEnd == -1)
            return null;
        String arr = json.substring(arrStart, arrEnd);

        // Find "text":"..." entries inside
        int i = 0;
        while (i < arr.length()) {
            int textIdx = arr.indexOf("\"text\"", i);
            if (textIdx == -1)
                break;
            String text = extractString(arr.substring(textIdx), "\"text\"");
            if (text != null && !text.isBlank())
                return text;
            i = textIdx + 6;
        }
        return null;
    }

    private List<Meaning> extractMeanings(String json) {
        List<Meaning> meanings = new ArrayList<>();
        int meaningsIdx = json.indexOf("\"meanings\"");
        if (meaningsIdx == -1)
            return meanings;

        int arrStart = json.indexOf("[", meaningsIdx);
        if (arrStart == -1)
            return meanings;

        // Walk through each meaning object
        int cursor = arrStart + 1;
        while (true) {
            int objStart = json.indexOf("{", cursor);
            if (objStart == -1)
                break;
            int objEnd = findClosingBrace(json, objStart);
            if (objEnd == -1)
                break;

            String meaning = json.substring(objStart, objEnd + 1);
            String pos = extractString(meaning, "\"partOfSpeech\"");
            List<Definition> defs = extractDefinitions(meaning);

            if (pos != null && !defs.isEmpty())
                meanings.add(new Meaning(pos, defs));

            cursor = objEnd + 1;
            // Stop after 3 meanings to keep response concise
            if (meanings.size() >= 3)
                break;
        }
        return meanings;
    }

    private List<Definition> extractDefinitions(String meaningJson) {
        List<Definition> defs = new ArrayList<>();
        int defsIdx = meaningJson.indexOf("\"definitions\"");
        if (defsIdx == -1)
            return defs;

        int arrStart = meaningJson.indexOf("[", defsIdx);
        if (arrStart == -1)
            return defs;

        int cursor = arrStart + 1;
        while (defs.size() < MAX_DEFS) {
            int objStart = meaningJson.indexOf("{", cursor);
            if (objStart == -1)
                break;
            int objEnd = findClosingBrace(meaningJson, objStart);
            if (objEnd == -1)
                break;

            String defObj = meaningJson.substring(objStart, objEnd + 1);
            String definition = extractString(defObj, "\"definition\"");
            String example = extractString(defObj, "\"example\"");

            if (definition != null && !definition.isBlank())
                defs.add(new Definition(definition, example));

            cursor = objEnd + 1;
        }
        return defs;
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────
    private String extractString(String json, String key) {
        String search = key + ":\"";
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
        s = s.replace("\\n", "\n").replace("\\\"", "\"")
                .replace("\\\\", "\\").replace("\\r", "\r").replace("\\t", "\t");
        if (!s.contains("\\u"))
            return s;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (i + 5 < s.length() && s.charAt(i) == '\\' && s.charAt(i + 1) == 'u'
                    && isHex(s.charAt(i + 2)) && isHex(s.charAt(i + 3))
                    && isHex(s.charAt(i + 4)) && isHex(s.charAt(i + 5))) {
                sb.appendCodePoint(Integer.parseInt(s.substring(i + 2, i + 6), 16));
                i += 6;
            } else {
                sb.append(s.charAt(i++));
            }
        }
        return sb.toString();
    }

    private boolean isHex(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private int findClosingBrace(String s, int open) {
        int depth = 0;
        boolean inStr = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\'))
                inStr = !inStr;
            if (!inStr) {
                if (c == '{')
                    depth++;
                else if (c == '}' && --depth == 0)
                    return i;
            }
        }
        return -1;
    }
}
