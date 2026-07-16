import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;

/**
 * GET /user/settings → returns { timezone }
 * PATCH /user/settings → updates { timezone }
 */
public class UserHandler implements HttpHandler {

    private final SessionManager sessions;

    public UserHandler(SessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

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

        try {
            switch (exchange.getRequestMethod().toUpperCase()) {
                case "GET" -> handleGet(exchange, userId);
                case "PATCH" -> handlePatch(exchange, userId);
                default -> send(exchange, 405, err("Method not allowed"));
            }
        } catch (SQLException e) {
            send(exchange, 500, err("Database error: " + e.getMessage()));
        }
    }

    // ── GET /user/settings ────────────────────────────────────────────────────
    private void handleGet(HttpExchange exchange, int userId) throws IOException, SQLException {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT timezone FROM users WHERE id = ?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                send(exchange, 404, err("User not found"));
                return;
            }
            String tz = rs.getString("timezone");
            send(exchange, 200, "{\"ok\":true,\"timezone\":\"" + escJson(tz) + "\"}");
        }
    }

    // ── PATCH /user/settings ──────────────────────────────────────────────────
    private void handlePatch(HttpExchange exchange, int userId) throws IOException, SQLException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String tz = extractField(body, "timezone");

        if (tz == null || tz.isBlank()) {
            send(exchange, 400, err("Missing timezone field"));
            return;
        }

        // Basic sanity check — valid IANA timezone identifiers contain only these chars
        if (!tz.matches("[A-Za-z0-9/_+\\-]+")) {
            send(exchange, 400, err("Invalid timezone format"));
            return;
        }

        try (PreparedStatement ps = Database.get().prepareStatement(
                "UPDATE users SET timezone = ? WHERE id = ?")) {
            ps.setString(1, tz);
            ps.setInt(2, userId);
            ps.executeUpdate();
            send(exchange, 200, "{\"ok\":true,\"timezone\":\"" + escJson(tz) + "\"}");
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
        int start = json.indexOf('"', i + search.length() + 1) + 1;
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\')
                break;
            end++;
        }
        return json.substring(start, end);
    }

    private String escJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
