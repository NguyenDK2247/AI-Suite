import java.sql.*;

/**
 * Manages the SQLite database.
 * Tables:
 * users (id, username, password_hash, timezone, created_at)
 * history (id, user_id, page, question, answer, extra_json, created_at)
 * sessions (token, user_id, expires_at)
 */
public class Database {

    private static final String DB_FILE = "app.db";
    private static final String URL = "jdbc:sqlite:" + DB_FILE;

    private static Connection connection;

    // ── Init ─────────────────────────────────────────────────────────────────────
    public static void init() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new SQLException("SQLite driver not found", e);
        }

        connection = DriverManager.getConnection(URL);

        try (Statement st = connection.createStatement()) {
            st.execute("PRAGMA journal_mode=WAL");
            st.execute("PRAGMA foreign_keys=ON");
        }

        createTables();
        migrate();
        System.out.println("Database ready: " + DB_FILE);
    }

    public static Connection get() {
        return connection;
    }

    // ── Schema ───────────────────────────────────────────────────────────────────
    private static void createTables() throws SQLException {
        try (Statement st = connection.createStatement()) {

            st.execute("""
                        CREATE TABLE IF NOT EXISTS users (
                            id            INTEGER PRIMARY KEY AUTOINCREMENT,
                            username      TEXT    NOT NULL UNIQUE COLLATE NOCASE,
                            password_hash TEXT    NOT NULL,
                            timezone      TEXT    NOT NULL DEFAULT 'UTC',
                            created_at    TEXT    DEFAULT (datetime('now'))
                        )
                    """);

            st.execute("""
                        CREATE TABLE IF NOT EXISTS sessions (
                            token      TEXT    PRIMARY KEY,
                            user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            expires_at TEXT    NOT NULL
                        )
                    """);

            st.execute("""
                        CREATE TABLE IF NOT EXISTS history (
                            id         INTEGER PRIMARY KEY AUTOINCREMENT,
                            user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                            page       TEXT    NOT NULL,
                            question   TEXT    NOT NULL,
                            answer     TEXT    NOT NULL,
                            extra_json TEXT,
                            created_at TEXT    DEFAULT (datetime('now'))
                        )
                    """);
        }
    }

    // ── Migrations — safely add columns to existing databases ────────────────────
    private static void migrate() throws SQLException {
        try (Statement st = connection.createStatement()) {
            // Add timezone column if it doesn't exist yet (for existing app.db files)
            try {
                st.execute("ALTER TABLE users ADD COLUMN timezone TEXT NOT NULL DEFAULT 'UTC'");
            } catch (SQLException ignored) {
            } // column already exists — safe to ignore
        }
    }
}