package com.aisuite.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Tracks daily Groq token usage per user in Oracle.
 * Resets automatically when the date changes.
 * Table: token_usage (user_id, date_key, total_tokens)
 */
@Service
public class TokenService {

    private static final int DAILY_LIMIT = 500_000;

    private final JdbcTemplate db;

    public TokenService(JdbcTemplate db) {
        this.db = db;
    }

    // ── Add tokens used by a single message ───────────────────────────────────
    public void addTokens(int userId, int tokens) {
        String today = LocalDate.now().toString();
        // Oracle MERGE replaces SQLite's INSERT ... ON CONFLICT DO UPDATE
        db.update("""
                MERGE INTO token_usage t
                USING (SELECT ? AS user_id, ? AS date_key, ? AS tokens FROM DUAL) src
                ON (t.user_id = src.user_id AND t.date_key = src.date_key)
                WHEN MATCHED THEN
                    UPDATE SET t.total_tokens = t.total_tokens + src.tokens
                WHEN NOT MATCHED THEN
                    INSERT (user_id, date_key, total_tokens)
                    VALUES (src.user_id, src.date_key, src.tokens)
                """, userId, today, tokens);
    }

    // ── Get today's total for a user ──────────────────────────────────────────
    public int getTodayTotal(int userId) {
        String today = LocalDate.now().toString();
        List<Map<String, Object>> rows = db.queryForList(
                "SELECT total_tokens FROM token_usage WHERE user_id = ? AND date_key = ?",
                userId, today);
        if (rows.isEmpty())
            return 0;
        return ((Number) rows.get(0).get("total_tokens")).intValue();
    }

    public int getDailyLimit() {
        return DAILY_LIMIT;
    }
}
