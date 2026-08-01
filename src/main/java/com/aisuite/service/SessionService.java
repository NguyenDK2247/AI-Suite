package com.aisuite.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class SessionService {

    private static final int TOKEN_BYTES = 32;
    private static final int SESSION_DAYS = 7;
    private static final int REMEMBER_DAYS = 30;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate db;
    private final SecureRandom rng = new SecureRandom();

    public SessionService(JdbcTemplate db) {
        this.db = db;
    }

    public String createSession(int userId, boolean rememberMe) {
        String token = generateToken();
        int days = rememberMe ? REMEMBER_DAYS : SESSION_DAYS;
        String expiresAt = LocalDateTime.now().plusDays(days).format(FMT);
        db.update("INSERT INTO sessions (token, user_id, expires_at) VALUES (?, ?, ?)",
                token, userId, expiresAt);
        return token;
    }

    public int validateSession(String token) {
        if (token == null || token.isBlank())
            return -1;
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT user_id, expires_at FROM sessions WHERE token = ?", token);
        if (rows.isEmpty())
            return -1;

        String expiresAt = (String) rows.get(0).get("expires_at");
        if (LocalDateTime.parse(expiresAt, FMT).isBefore(LocalDateTime.now())) {
            db.update("DELETE FROM sessions WHERE token = ?", token);
            return -1;
        }
        return ((Number) rows.get(0).get("user_id")).intValue();
    }

    public void deleteSession(String token) {
        if (token != null)
            db.update("DELETE FROM sessions WHERE token = ?", token);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        rng.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
