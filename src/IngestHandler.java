import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * POST /ingest — scrapes a URL and stores chunks in ChromaDB
 * GET /ingest/collections — lists known collections
 *
 * Request body:
 * {
 * "url": "https://example.com/article",
 * "collection": "weather_knowledge",
 * "depth": 0 // 0 = single page, 1 = page + linked pages
 * }
 */
public class IngestHandler implements HttpHandler {

    private final RagService rag;
    private final SessionManager sessions;

    public IngestHandler(RagService rag, SessionManager sessions) {
        this.rag = rag;
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        // Auth check
        try {
            String token = sessions.extractToken(exchange);
            if (sessions.validateSession(token) == -1) {
                send(exchange, 401, err("Not logged in."));
                return;
            }
        } catch (java.sql.SQLException e) {
            send(exchange, 500, err("Session error"));
            return;
        }

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (method.equalsIgnoreCase("GET") && path.equals("/ingest/collections")) {
            // Return the list of predefined collections
            send(exchange, 200,
                    "{\"collections\":[\"weather_knowledge\",\"currency_knowledge\"]}");
            return;
        }

        if (!method.equalsIgnoreCase("POST")) {
            send(exchange, 405, err("Method not allowed"));
            return;
        }

        String body = new String(
                exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        String url = extractField(body, "url");
        String collection = extractField(body, "collection");
        int depth = extractInt(body, "depth", 0);

        if (url == null || url.isBlank()) {
            send(exchange, 400, err("Missing 'url' field"));
            return;
        }
        if (collection == null || collection.isBlank()) {
            send(exchange, 400, err("Missing 'collection' field"));
            return;
        }
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            send(exchange, 400, err("URL must start with http:// or https://"));
            return;
        }
        if (depth < 0 || depth > 1) {
            send(exchange, 400, err("depth must be 0 or 1"));
            return;
        }

        // Run ingest in a background thread so the HTTP response isn't held open
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        // Run synchronously so errors are returned to the client
        try {
            RagService.IngestResult result = rag.ingest(url, collection, depth);
            System.out.println("Ingest complete: " + result.pagesIngested()
                    + " page(s), " + result.chunksIngested()
                    + " chunks -> " + result.collection());
            send(exchange, 200,
                    "{\"ok\":true,"
                            + "\"pagesIngested\":" + result.pagesIngested() + ","
                            + "\"chunksIngested\":" + result.chunksIngested() + ","
                            + "\"collection\":\"" + escJson(result.collection()) + "\"}");
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            System.err.println("Ingest failed for " + url + ": " + msg);
            send(exchange, 500, "{\"ok\":false,\"error\":\"" + escJson(msg) + "\"}");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String err(String msg) {
        return "{\"ok\":false,\"error\":\"" + escJson(msg) + "\"}";
    }

    private String extractField(String json, String key) {
        String search = "\"" + key + "\"";
        int i = json.indexOf(search);
        if (i == -1)
            return null;
        int colon = json.indexOf(':', i + search.length());
        if (colon == -1)
            return null;
        int quote = json.indexOf('"', colon + 1);
        if (quote == -1)
            return null;
        int end = quote + 1;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\')
                break;
            end++;
        }
        return json.substring(quote + 1, end);
    }

    private int extractInt(String json, String key, int defaultVal) {
        String search = "\"" + key + "\":";
        int i = json.indexOf(search);
        if (i == -1)
            return defaultVal;
        int start = i + search.length();
        while (start < json.length() && json.charAt(start) == ' ')
            start++;
        int end = start;
        while (end < json.length() && Character.isDigit(json.charAt(end)))
            end++;
        try {
            return Integer.parseInt(json.substring(start, end));
        } catch (NumberFormatException e) {
            return defaultVal;
        }
    }

    private String escJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
