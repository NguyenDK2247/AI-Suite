import java.security.SecureRandom;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class SessionManager {

    private static final int TOKEN_BYTES = 32;
    private static final int SESSION_DAYS = 7;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SecureRandom rng = new SecureRandom();

    // ── Create a new session and return the token ─────────────────────────────
    public String createSession(int userId) throws SQLException {
        String token = generateToken();
        String expiresAt = LocalDateTime.now().plusDays(SESSION_DAYS).format(FMT);

        try (PreparedStatement ps = Database.get().prepareStatement(
                "INSERT INTO sessions (token, user_id, expires_at) VALUES (?, ?, ?)")) {
            ps.setString(1, token);
            ps.setInt(2, userId);
            ps.setString(3, expiresAt);
            ps.executeUpdate();
        }
        return token;
    }

    /**
     * Validates a session token.
     * 
     * @return the user_id if valid, or -1 if missing/expired.
     */
    public int validateSession(String token) throws SQLException {
        if (token == null || token.isBlank())
            return -1;

        try (PreparedStatement ps = Database.get().prepareStatement(
                "SELECT user_id, expires_at FROM sessions WHERE token = ?")) {
            ps.setString(1, token);
            ResultSet rs = ps.executeQuery();
            if (!rs.next())
                return -1;

            String expiresAt = rs.getString("expires_at");
            if (LocalDateTime.parse(expiresAt, FMT).isBefore(LocalDateTime.now())) {
                deleteSession(token); // clean up expired session
                return -1;
            }
            return rs.getInt("user_id");
        }
    }

    public void deleteSession(String token) throws SQLException {
        try (PreparedStatement ps = Database.get().prepareStatement(
                "DELETE FROM sessions WHERE token = ?")) {
            ps.setString(1, token);
            ps.executeUpdate();
        }
    }

    // ── Extract token from Cookie header ─────────────────────────────────────
    public String extractToken(com.sun.net.httpserver.HttpExchange exchange) {
        String cookieHeader = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null)
            return null;
        for (String part : cookieHeader.split(";")) {
            String[] kv = part.trim().split("=", 2);
            if (kv.length == 2 && kv[0].trim().equals("session"))
                return kv[1].trim();
        }
        return null;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        rng.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
