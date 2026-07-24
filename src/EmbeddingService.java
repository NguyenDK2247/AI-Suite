import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Calls Ollama's local REST API to embed text using nomic-embed-text.
 * Ollama endpoint: POST http://localhost:11434/api/embeddings
 */
public class EmbeddingService {

    private static final String OLLAMA_URL = "http://localhost:11434/api/embeddings";
    private static final String MODEL      = "nomic-embed-text";

    private final HttpClient http;

    public EmbeddingService() {
        this.http = HttpClient.newHttpClient();
    }

    // ── Embed a single string → float list ───────────────────────────────────
    public List<Double> embed(String text) throws IOException, InterruptedException {
        String body = "{\"model\":\"" + MODEL + "\",\"prompt\":" + jsonStr(text) + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(OLLAMA_URL))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200)
            throw new IOException("Ollama error " + res.statusCode() + ": " + res.body());

        return parseEmbedding(res.body());
    }

    // ── Parse [ "embedding": [0.1, 0.2, ...] ] ───────────────────────────────
    private List<Double> parseEmbedding(String json) throws IOException {
        int start = json.indexOf("[");
        int end   = json.lastIndexOf("]");
        if (start == -1 || end == -1)
            throw new IOException("Could not parse embedding from: " + json);

        String[] parts = json.substring(start + 1, end).split(",");
        List<Double> vec = new ArrayList<>(parts.length);
        for (String p : parts) {
            try { vec.add(Double.parseDouble(p.trim())); }
            catch (NumberFormatException e) { throw new IOException("Bad float: " + p); }
        }
        return vec;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String jsonStr(String s) {
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "") + "\"";
    }
}
