-- ── Users table ───────────────────────────────────────────────────────────────
CREATE TABLE users (
    id            NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    username      VARCHAR2(20)  NOT NULL,
    password_hash VARCHAR2(100) NOT NULL,
    timezone      VARCHAR2(50)  DEFAULT 'UTC' NOT NULL,
    created_at    TIMESTAMP     DEFAULT SYSTIMESTAMP
)
/

CREATE UNIQUE INDEX uq_users_username ON users (UPPER(username))
/

-- ── Sessions table ────────────────────────────────────────────────────────────
CREATE TABLE sessions (
    token      VARCHAR2(100) PRIMARY KEY,
    user_id    NUMBER        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at VARCHAR2(20)  NOT NULL
)
/

-- ── History table ─────────────────────────────────────────────────────────────
CREATE TABLE history (
    id         NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id    NUMBER         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    page       VARCHAR2(50)   NOT NULL,
    question   VARCHAR2(4000) NOT NULL,
    answer     CLOB           NOT NULL,
    extra_json CLOB,
    created_at TIMESTAMP      DEFAULT SYSTIMESTAMP
)
/

-- ── Token usage table ─────────────────────────────────────────────────────────
CREATE TABLE token_usage (
    user_id      NUMBER       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date_key     VARCHAR2(10) NOT NULL,
    total_tokens NUMBER       DEFAULT 0 NOT NULL,
    CONSTRAINT pk_token_usage PRIMARY KEY (user_id, date_key)
)
/
