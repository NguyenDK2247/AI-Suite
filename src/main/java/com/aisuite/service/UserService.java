package com.aisuite.service;

import com.aisuite.model.User;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.regex.Pattern;

@Service
public class UserService {

    private static final Pattern USERNAME_RE = Pattern.compile("^[A-Za-z0-9_]{3,20}$");
    private static final String DUMMY_HASH = "$2a$12$invalidhashfortimingsafety000000000000000000000000000";

    private final JdbcTemplate db;

    public UserService(JdbcTemplate db) {
        this.db = db;
    }

    // ── Lookup ────────────────────────────────────────────────────────────────
    public User findByUsername(String username) {
        try {
            return db.queryForObject(
                    "SELECT id, username, password_hash, timezone, created_at " +
                            "FROM users WHERE username = ? COLLATE NOCASE",
                    (rs, row) -> {
                        User u = new User();
                        u.setId(rs.getInt("id"));
                        u.setUsername(rs.getString("username"));
                        u.setPasswordHash(rs.getString("password_hash"));
                        u.setTimezone(rs.getString("timezone"));
                        u.setCreatedAt(rs.getString("created_at"));
                        return u;
                    }, username);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public User findById(int id) {
        try {
            return db.queryForObject(
                    "SELECT id, username, password_hash, timezone, created_at " +
                            "FROM users WHERE id = ?",
                    (rs, row) -> {
                        User u = new User();
                        u.setId(rs.getInt("id"));
                        u.setUsername(rs.getString("username"));
                        u.setPasswordHash(rs.getString("password_hash"));
                        u.setTimezone(rs.getString("timezone"));
                        u.setCreatedAt(rs.getString("created_at"));
                        return u;
                    }, id);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    // ── Signup ────────────────────────────────────────────────────────────────
    public record SignupResult(boolean ok, String error, int userId) {
    }

    public SignupResult signup(String username, String password) {
        if (username == null || username.isBlank())
            return new SignupResult(false, "Username is required.", -1);
        if (!USERNAME_RE.matcher(username).matches())
            return new SignupResult(false,
                    "Username must be 3–20 characters: letters, numbers, underscores only.", -1);
        if (password == null || password.length() < 8)
            return new SignupResult(false, "Password must be at least 8 characters.", -1);
        if (findByUsername(username) != null)
            return new SignupResult(false, "Username already taken.", -1);

        String hash = BCrypt.hashpw(password, BCrypt.gensalt(12));

        KeyHolder keys = new GeneratedKeyHolder();
        db.update(conn -> {
            PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO users (username, password_hash) VALUES (?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, hash);
            return ps;
        }, keys);

        @SuppressWarnings("null")
        int newId = keys.getKey().intValue();
        return new SignupResult(true, null, newId);
    }

    // ── Login ─────────────────────────────────────────────────────────────────
    public record LoginResult(boolean ok, String error, int userId) {
    }

    public LoginResult login(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank())
            return new LoginResult(false, "Username and password are required.", -1);

        User user = findByUsername(username);
        if (user == null) {
            // Timing-safe: run BCrypt even when user not found
            BCrypt.checkpw("dummy", DUMMY_HASH);
            return new LoginResult(false, "Invalid username or password.", -1);
        }

        if (!BCrypt.checkpw(password, user.getPasswordHash()))
            return new LoginResult(false, "Invalid username or password.", -1);

        return new LoginResult(true, null, user.getId());
    }

    // ── Timezone ──────────────────────────────────────────────────────────────
    public void updateTimezone(int userId, String timezone) {
        db.update("UPDATE users SET timezone = ? WHERE id = ?", timezone, userId);
    }

    public boolean isValidUsername(String username) {
        return username != null && USERNAME_RE.matcher(username).matches();
    }
}
