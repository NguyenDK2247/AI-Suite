package com.aisuite.service;

import com.aisuite.model.HistoryEntry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

@Service
public class HistoryService {

    private final JdbcTemplate db;
    private final ObjectMapper mapper;

    public HistoryService(JdbcTemplate db, ObjectMapper mapper) {
        this.db = db;
        this.mapper = mapper;
    }

    public List<HistoryEntry> getHistory(int userId, String page) {
        return db.query(
                "SELECT id, user_id, page, question, answer, extra_json, created_at " +
                        "FROM history WHERE user_id = ? AND page = ? " +
                        "ORDER BY created_at ASC LIMIT 60",
                (rs, row) -> {
                    HistoryEntry e = new HistoryEntry();
                    e.setId(rs.getInt("id"));
                    e.setUserId(rs.getInt("user_id"));
                    e.setPage(rs.getString("page"));
                    e.setQuestion(rs.getString("question"));
                    e.setAnswer(rs.getString("answer"));
                    e.setTime(rs.getString("created_at"));
                    String extraJson = rs.getString("extra_json");
                    if (extraJson != null) {
                        try {
                            e.setExtra(mapper.readTree(extraJson));
                        } catch (Exception ex) {
                            /* skip malformed extra */ }
                    }
                    return e;
                }, userId, page);
    }

    @SuppressWarnings("null")
    public int addEntry(int userId, String page, String question,
            String answer, JsonNode extra) {
        String extraJson = null;
        if (extra != null && !extra.isNull()) {
            try {
                extraJson = mapper.writeValueAsString(extra);
            } catch (Exception ex) {
                /* skip serialisation failure */ }
        }

        KeyHolder keys = new GeneratedKeyHolder();
        String finalExtraJson = extraJson;
        db.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO history (user_id, page, question, answer, extra_json) " +
                            "VALUES (?, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setInt(1, userId);
            ps.setString(2, page);
            ps.setString(3, question);
            ps.setString(4, answer);
            ps.setString(5, finalExtraJson);
            return ps;
        }, keys);

        return keys.getKey().intValue();
    }

    public void deleteEntry(int userId, int entryId) {
        db.update("DELETE FROM history WHERE id = ? AND user_id = ?", entryId, userId);
    }

    public void clearPage(int userId, String page) {
        db.update("DELETE FROM history WHERE user_id = ? AND page = ?", userId, page);
    }
}
