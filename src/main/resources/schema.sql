-- Users table
CREATE TABLE IF NOT EXISTS users (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    username      TEXT    NOT NULL UNIQUE COLLATE NOCASE,
    password_hash TEXT    NOT NULL,
    timezone      TEXT    NOT NULL DEFAULT 'UTC',
    created_at    TEXT    DEFAULT (datetime('now'))
);

-- Sessions table
CREATE TABLE IF NOT EXISTS sessions (
    token      TEXT    PRIMARY KEY,
    user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TEXT    NOT NULL
);

-- History table
CREATE TABLE IF NOT EXISTS history (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    page       TEXT    NOT NULL,
    question   TEXT    NOT NULL,
    answer     TEXT    NOT NULL,
    extra_json TEXT,
    created_at TEXT    DEFAULT (datetime('now'))
);
