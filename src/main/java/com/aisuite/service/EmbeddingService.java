package com.aisuite.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    private static final String MODEL = "nomic-embed-text";

    private final String ollamaUrl;
    private final HttpClient http;

    public EmbeddingService(@Value("${app.ollama.url}") String ollamaUrl) {
        this.ollamaUrl = ollamaUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public List<Double> embed(String text) throws IOException, InterruptedException {
        String body = "{\"model\":\"" + MODEL + "\",\"prompt\":" + jsonStr(text) + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(ollamaUrl + "/api/embeddings"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200)
            throw new IOException("Ollama error " + res.statusCode() + ": " + res.body());

        String responseBody = res.body();
        if (!responseBody.contains("\"embedding\""))
            throw new IOException("Ollama returned no embedding field. Response: "
                    + responseBody.substring(0, Math.min(200, responseBody.length())));

        return parseEmbedding(responseBody);
    }

    private List<Double> parseEmbedding(String json) throws IOException {
        int start = json.indexOf("[");
        int end = json.lastIndexOf("]");
        if (start == -1 || end == -1)
            throw new IOException("Could not parse embedding from: " + json);
        String[] parts = json.substring(start + 1, end).split(",");
        List<Double> vec = new ArrayList<>(parts.length);
        for (String p : parts) {
            try {
                vec.add(Double.parseDouble(p.trim()));
            } catch (NumberFormatException e) {
                throw new IOException("Bad float: " + p);
            }
        }
        return vec;
    }

    private String jsonStr(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "") + "\"";
    }
}
