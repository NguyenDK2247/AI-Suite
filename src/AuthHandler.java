import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;

public class AuthHandler implements HttpHandler {

    private final SessionManager sessions;

    // Username: 3-20 chars, alphanumeric + underscore only
    private static final java.util.regex.Pattern USERNAME_RE = java.util.regex.Pattern.compile("^[A-Za-z0-9_]{3,20}$");

    public AuthHandler(SessionManager sessions) {
        this.sessions = sessions;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        try {
            switch (path) {
                case "/auth/signup" -> {
                    if (!method.equalsIgnoreCase("POST")) {
                        send(exchange, 405, err("Method not allowed"));
                        return;
                    }
                    handleSignup(exchange);
                }
                case "/auth/login" -> {
                    if (!method.equalsIgnoreCase("POST")) {
                        send(exchange, 405, err("Method not allowed"));
                        return;
                    }
                    handleLogin(exchange);
                }
                case "/auth/logout" -> {
                    if (!method.equalsIgnoreCase("POST")) {
                        send(exchange, 405, err("Method not allowed"));
                        return;
                    }
                    handleLogout(exchange);
                }
                case "/auth/me" -> {
                    if (!method.equalsIgnoreCase("GET")) {
                        send(exchange, 405, err("Method not allowed"));
                        return;
                    }
                    handleMe(exchange);
                }
                default -> send(exchange, 404, err("Not found"));
            }
        } catch (SQLException e) {
            send(exchange, 500, err("Database error: " + e.getMessage()));
        }
    }

    // ── /auth/signup ──────────────────────────────────────────────────────────
    private void handleSignup(HttpExchange exchange) throws IOException, SQLException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String username = extractField(body, "username");
        String password = extractField(body, "password");

        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            send(exchange, 400, err("Username and password are required."));
            return;
        }
        if (!USERNAME_RE.matcher(username).matches()) {
            send(exchange, 400, err("Username must be 3-20 characters: letters, numbers, underscores only."));
            return;
        }
        if (password.length() < 8) {
            send(exchange, 400, err("Password must be at least 8 characters."));
            return;
        }

        // Check username taken
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT id FROM users WHERE username = ?")) {
            ps.setString(1, username);
            if (ps.executeQuery().next()) {
                send(exchange, 409, err("Username already taken."));
                return;
            }
        }

        // Hash with BCrypt (cost factor 12)
        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));

        int userId;
        try (PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO users (username, password_hash) VALUES (?, ?)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.executeUpdate();
            ResultSet keys = ps.getGeneratedKeys();
            if (!keys.next()) {
                send(exchange, 500, err("Failed to create user."));
                return;
            }
            userId = keys.getInt(1);
        }

        String token = sessions.createSession(userId);
        setSessionCookie(exchange, token);
        send(exchange, 201, "{\"ok\":true,\"username\":\"" + escJson(username) + "\"}");
    }

    // ── /auth/login ───────────────────────────────────────────────────────────
    private void handleLogin(HttpExchange exchange) throws IOException, SQLException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String username = extractField(body, "username");
        String password = extractField(body, "password");

        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            send(exchange, 400, err("Username and password are required."));
            return;
        }

        String hash;
        int userId;
        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT id, password_hash FROM users WHERE username = ?")) {
            ps.setString(1, username);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                // Timing-safe: still run BCrypt even on missing user
                BCrypt.checkpw("dummy", "$2a$12$invalidhashfortimingsafety000000000000000000000000000");
                send(exchange, 401, err("Invalid username or password."));
                return;
            }
            userId = rs.getInt("id");
            hash = rs.getString("password_hash");
        }

        if (!BCrypt.checkpw(password, hash)) {
            send(exchange, 401, err("Invalid username or password."));
            return;
        }

        String token = sessions.createSession(userId);
        setSessionCookie(exchange, token);
        send(exchange, 200, "{\"ok\":true,\"username\":\"" + escJson(username) + "\"}");
    }

    // ── /auth/logout ──────────────────────────────────────────────────────────
    private void handleLogout(HttpExchange exchange) throws IOException, SQLException {
        String token = sessions.extractToken(exchange);
        if (token != null)
            sessions.deleteSession(token);
        // Clear cookie
        exchange.getResponseHeaders().add("Set-Cookie",
                "session=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
        send(exchange, 200, "{\"ok\":true}");
    }

    // ── /auth/me ─────────────────────────────────────────────────────────────
    private void handleMe(HttpExchange exchange) throws IOException, SQLException {
        String token = sessions.extractToken(exchange);
        int userId = sessions.validateSession(token);
        if (userId == -1) {
            send(exchange, 401, err("Not logged in."));
            return;
        }

        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT username FROM users WHERE id = ?")) {
            ps.setInt(1, userId);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) {
                send(exchange, 404, err("User not found."));
                return;
            }
            send(exchange, 200, "{\"ok\":true,\"username\":\"" + escJson(rs.getString("username")) + "\"}");
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private void setSessionCookie(HttpExchange exchange, String token) {
        exchange.getResponseHeaders().add("Set-Cookie",
                "session=" + token + "; Path=/; HttpOnly; SameSite=Strict; Max-Age=604800");
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

    private String escJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
