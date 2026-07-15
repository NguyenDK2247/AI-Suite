import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;

/**
 * GET /history?page=weather → returns last 60 entries for this user+page
 * POST /history → saves a new entry
 * DELETE /history?id=X → deletes one entry
 * DELETE /history?page=weather → clears all entries for this user+page
 */
public class HistoryHandler implements HttpHandler {

    private final SessionManager sessions;

    public HistoryHandler(SessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        // ── Auth check ───────────────────────────────────────────────────────
        String token = sessions.extractToken(exchange);
        int userId;
        try {
            userId = sessions.validateSession(token);
        } catch (SQLException e) {
            send(exchange, 500, err("DB error"));
            return;
        }
        if (userId == -1) {
            send(exchange, 401, err("Not logged in."));
            return;
        }

        String method = exchange.getRequestMethod();
        try {
            switch (method.toUpperCase()) {
                case "GET" -> handleGet(exchange, userId);
                case "POST" -> handlePost(exchange, userId);
                case "DELETE" -> handleDelete(exchange, userId);
                default -> send(exchange, 405, err("Method not allowed"));
            }
        } catch (SQLException e) {
            send(exchange, 500, err("Database error: " + e.getMessage()));
        }
    }

    // ── GET /history?page=weather ─────────────────────────────────────────────
    private void handleGet(HttpExchange exchange, int userId) throws IOException, SQLException {
        String page = queryParam(exchange, "page");
        if (page == null || page.isBlank()) {
            send(exchange, 400, err("Missing page param"));
            return;
        }

        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT id, question, answer, extra_json, created_at " +
                        "FROM history WHERE user_id = ? AND page = ? " +
                        "ORDER BY created_at ASC LIMIT 60")) {
            ps.setInt(1, userId);
            ps.setString(2, page);
            ResultSet rs = ps.executeQuery();

            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first)
                    sb.append(",");
                first = false;
                String extra = rs.getString("extra_json");
                sb.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"question\":\"").append(escJson(rs.getString("question"))).append("\",")
                        .append("\"answer\":\"").append(escJson(rs.getString("answer"))).append("\",")
                        .append("\"time\":\"").append(escJson(rs.getString("created_at"))).append("\",")
                        .append("\"extra\":").append(extra != null ? extra : "null")
                        .append("}");
            }
            sb.append("]");
            send(exchange, 200, sb.toString());
        }
    }

    // ── POST /history ─────────────────────────────────────────────────────────
    private void handlePost(HttpExchange exchange, int userId) throws IOException, SQLException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String page = extractField(body, "page");
        String question = extractField(body, "question");
        String answer = extractField(body, "answer");
        String extraJson = extractRawField(body, "extra"); // raw JSON object or null

        if (page == null || question == null || answer == null) {
            send(exchange, 400, err("Missing fields: page, question, answer required"));
            return;
        }

        try (PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO history (user_id, page, question, answer, extra_json) VALUES (?,?,?,?,?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, userId);
            ps.setString(2, page);
            ps.setString(3, question);
            ps.setString(4, answer);
            ps.setString(5, extraJson);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            int newId = keys.next() ? keys.getInt(1) : -1;
            send(exchange, 201, "{\"ok\":true,\"id\":" + newId + "}");
        }
    }

    // ── DELETE /history?id=X or DELETE /history?page=weather ───────────────
    private void handleDelete(HttpExchange exchange, int userId) throws IOException, SQLException {
        String idParam = queryParam(exchange, "id");
        String pageParam = queryParam(exchange, "page");

        if (idParam != null) {
            // Delete single entry
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "DELETE FROM history WHERE id = ? AND user_id = ?")) {
                ps.setInt(1, Integer.parseInt(idParam));
                ps.setInt(2, userId);
                ps.executeUpdate();
                send(exchange, 200, "{\"ok\":true}");
            }
        } else if (pageParam != null) {
            // Clear all entries for this page
            try (PreparedStatement ps = Database.get().prepareStatement(
                    "DELETE FROM history WHERE user_id = ? AND page = ?")) {
                ps.setInt(1, userId);
                ps.setString(2, pageParam);
                ps.executeUpdate();
                send(exchange, 200, "{\"ok\":true}");
            }
        } else {
            send(exchange, 400, err("Provide id or page param"));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String queryParam(HttpExchange exchange, String key) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null)
            return null;
        for (String part : query.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key))
                return kv[1];
        }
        return null;
    }

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
        int start = json.indexOf('"', i + search.length() + 1) + 1;
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\')
                break;
            end++;
        }
        return json.substring(start, end).replace("\\\"", "\"").replace("\\n", "\n");
    }

    // Extracts a raw JSON value (object/array/null) rather than a string
    private String extractRawField(String json, String key) {
        String search = "\"" + key + "\":";
        int i = json.indexOf(search);
        if (i == -1)
            return null;
        int start = i + search.length();
        while (start < json.length() && json.charAt(start) == ' ')
            start++;
        if (start >= json.length())
            return null;
        char first = json.charAt(start);
        if (first == 'n')
            return "null"; // null literal
        if (first != '{' && first != '[')
            return null; // only accept objects/arrays
        // Find matching close
        char open = first;
        char close = (open == '{') ? '}' : ']';
        int depth = 0;
        boolean inStr = false;
        for (int j = start; j < json.length(); j++) {
            char c = json.charAt(j);
            if (c == '"' && (j == 0 || json.charAt(j - 1) != '\\'))
                inStr = !inStr;
            if (!inStr) {
                if (c == open)
                    depth++;
                else if (c == close) {
                    if (--depth == 0)
                        return json.substring(start, j + 1);
                }
            }
        }
        return null;
    }

    private String escJson(String s) {
        if (s == null)
            return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
