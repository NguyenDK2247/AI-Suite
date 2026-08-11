package com.aisuite.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Tracks daily Groq token usage per user in SQLite.
 * Resets automatically when the date changes.
 * Table: token_usage (user_id, date, total_tokens)
 */
@Service
public class TokenService {

    private static final int DAILY_LIMIT = 500_000;

    private final JdbcTemplate db;

    public TokenService(JdbcTemplate db) {
        this.db = db;
        db.execute("""
                    CREATE TABLE IF NOT EXISTS token_usage (
                        user_id      INTEGER NOT NULL,
                        date         TEXT    NOT NULL,
                        total_tokens INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY (user_id, date),
                        FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
                    )
                """);
    }

    // ── Add tokens used by a single message ───────────────────────────────────
    public void addTokens(int userId, int tokens) {
        String today = LocalDate.now().toString();
        db.update("""
                INSERT INTO token_usage (user_id, date, total_tokens)
                VALUES (?, ?, ?)
                ON CONFLICT(user_id, date) DO UPDATE SET total_tokens = total_tokens + excluded.total_tokens
                """, userId, today, tokens);
    }

    // ── Get today's total for a user ──────────────────────────────────────────
    public int getTodayTotal(int userId) {
        String today = LocalDate.now().toString();
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT total_tokens FROM token_usage WHERE user_id = ? AND date = ?",
                userId, today);
        if (rows.isEmpty())
            return 0;
        return ((Number) rows.get(0).get("total_tokens")).intValue();
    }

    public int getDailyLimit() {
        return DAILY_LIMIT;
    }
}
