# 🤖🌤️💱🌐📖 AI Suite

A personal AI agent and web interface for weather forecasting, currency exchange, language translation, and word definitions, with a built-in knowledge base powered by RAG (Retrieval-Augmented Generation).

## 🚀 Current Features
* **Weather Agent:** fetches real-time weather and hourly forecasts via OpenWeatherMap, with Groq providing natural language commentary
* **Currency Agent:** fetches live exchange rates via ExchangeRate-API and converts between currencies, with topic-guarded responses (regex prevents off-topic questions)
* **Translation Agent:** translates text between 30+ languages via a locally hosted LibreTranslate instance; auto-detects source language; includes retry logic for connection resilience
* **Dictionary Agent:** looks up English word definitions, phonetics, part of speech, and usage examples via the Free Dictionary API (no API key required); renders a structured definition card per word
* **RAG Pipeline:** web scraper (Jsoup) ingests URLs into ChromaDB via local Ollama embeddings (`nomic-embed-text`), with hybrid search (semantic + keyword) and cosine-similarity reranking
* **Knowledge Base UI:** dedicated `/knowledge` page to add URLs per agent (Weather, Currency, Translation, Dictionary), with crawl depth control and real-time ingest feedback
* **Authentication:** BCrypt-hashed passwords, Oracle-backed user accounts, HttpOnly session cookies with optional 30-day "remember me"
* **Per-user history:** chat history stored server-side in Oracle Database, fully searchable, separate per agent and per account
* **Oracle Database:** users, sessions, chat history, and daily token usage live in a local Oracle Database 23ai Free instance, accessed through Spring `JdbcTemplate`; history timestamps are stored in UTC and shown in each user's chosen timezone
* **Token usage tracking:** daily Groq token consumption displayed per message and as a running total against the daily limit; persists across page switches and app restarts; resets automatically at midnight
* **Timezone selector:** IANA timezone dropdown (via WorldTimeAPI) synced across all pages and persisted per user; all timestamps respect the chosen timezone
* **Dark/light mode:** synced across login, signup, and all chat pages via `localStorage`
* **Multi-agent sidebar:** Weather, Currency, Translation, Dictionary, and Knowledge tabs with persistent navigation

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Vanilla HTML/CSS/JS (no framework) |
| Backend | Spring Boot 3.2 (Java 17) |
| LLM | Groq API - `openai/gpt-oss-120b` |
| Weather data | OpenWeatherMap API |
| Currency data | ExchangeRate-API (no key required) |
| Translation | LibreTranslate (local) |
| Dictionary | Free Dictionary API (no key required) |
| Embeddings | Ollama - `nomic-embed-text` (local) |
| Vector store | ChromaDB v2 (local) |
| Web scraping | Jsoup |
| Database | Oracle Database 23ai Free (local) via `ojdbc11` |
| Password hashing | BCrypt (jBCrypt via Maven) |
| Build tool | Maven 3.9+ |

## 📦 Getting Started

### Prerequisites

1. **Java 17+**
2. **Maven 3.9+** - https://maven.apache.org/download.cgi (add `bin/` to PATH; set `JAVA_HOME` to JDK root, not `bin\java.exe`)
3. **Ollama** - https://ollama.com/download
   ```
   ollama pull nomic-embed-text
   ```
4. **ChromaDB** - requires Python 3.11+
   ```
   pip install chromadb
   ```
5. **LibreTranslate** - requires Python 3.11+
   ```
   pip install libretranslate
   ```
6. **Oracle Database 23ai Free** - https://www.oracle.com/database/free/get-started/ (local install; default port 1521, service `FREEPDB1`). After installing, connect as the system user and create the app user:
   ```
   sqlplus system/<your_system_password>@localhost:1521/FREEPDB1
   ```
   ```
   CREATE USER aisuite IDENTIFIED BY YourPassword123;
   GRANT CONNECT, RESOURCE, UNLIMITED TABLESPACE TO aisuite;
   ```
   Use letters and numbers only in the password (see Notes). The tables are created automatically on startup from `schema.sql`.
7. **API keys and database credentials** - set in `.env` at project root:
   ```
   OPENWEATHER_API_KEY=your_key_here
   GROQ_API_KEY=your_key_here
   ORACLE_USER=aisuite
   ORACLE_PASSWORD=your_password_here
   ```

### Running

Oracle Database runs as a Windows service and starts automatically, so it does not need a terminal of its own.

Open four terminals:

**Terminal 1 - Ollama** (may already be running as a Windows service):
```
ollama serve
```

**Terminal 2 - ChromaDB:**
```
chroma run --host localhost --port 8000
```

**Terminal 3 - LibreTranslate** (downloads language models on first run, ~1-2 GB):
```
libretranslate --host 0.0.0.0 --port 5000
```

**Terminal 4 - App** (from project root):
```
run.bat
```

Maven downloads all dependencies automatically on first run, and the app creates its tables in Oracle from `schema.sql` on startup. Then open `http://localhost:8080/signup` to create an account.

### Project Structure

```
ai-suite/
├── src/main/
│   ├── java/com/aisuite/
│   │   ├── AiSuiteApplication.java
│   │   ├── config/
│   │   │   ├── AgentConfig.java
│   │   │   ├── AuthInterceptor.java
│   │   │   ├── DatabaseConfig.java
│   │   │   ├── GlobalExceptionHandler.java
│   │   │   └── WebConfig.java
│   │   ├── controller/
│   │   │   ├── AuthController.java
│   │   │   ├── ChatController.java
│   │   │   ├── CurrencyController.java
│   │   │   ├── DictionaryController.java
│   │   │   ├── HistoryController.java
│   │   │   ├── IngestController.java
│   │   │   ├── PageController.java
│   │   │   ├── TokenController.java
│   │   │   ├── TranslationController.java
│   │   │   └── UserController.java
│   │   ├── model/
│   │   │   ├── HistoryEntry.java
│   │   │   └── User.java
│   │   └── service/
│   │       ├── CurrencyService.java
│   │       ├── DictionaryService.java
│   │       ├── EmbeddingService.java
│   │       ├── GroqService.java
│   │       ├── HistoryService.java
│   │       ├── RagService.java
│   │       ├── Reranker.java
│   │       ├── SessionService.java
│   │       ├── TokenService.java
│   │       ├── TranslationService.java
│   │       ├── UserService.java
│   │       ├── VectorStore.java
│   │       ├── WeatherService.java
│   │       └── WebScraper.java
│   └── resources/
│       ├── application.properties
│       ├── schema.sql              # Oracle tables (run at startup)
│       └── static/
│           ├── auth.css
│           ├── currency.html
│           ├── dictionary.html
│           ├── index.html
│           ├── ingest.html
│           ├── login.html
│           ├── signup.html
│           ├── styles.css
│           └── translation.html
├── .env                    # API keys and Oracle credentials (git-ignored)
├── .gitignore
├── chroma/                 # ChromaDB data (git-ignored)
├── pom.xml
└── run.bat
```

### Notes
- ChromaDB on Windows binds to IPv6 (`::1`) rather than IPv4 (`127.0.0.1`). The app is pre-configured for this in `application.properties`
- `JAVA_HOME` must point to the JDK root folder (e.g. `C:\Program Files\Eclipse Adoptium\jdk-17.0.13`), not `bin\java.exe`
- If user-level and system-level `JAVA_HOME` conflict, user-level takes priority on Windows
- LibreTranslate downloads language models (~1-2 GB) on first run only; subsequent starts take a few seconds
- The Groq model is configured in `GroqService.java` - update `MODEL` if you switch models or Groq retires the current one
- Oracle Database must be running before the app starts. The app connects to `localhost:1521/FREEPDB1` as the user in `ORACLE_USER`; if it cannot connect, check that the database and listener services are running and that the `.env` credentials match the app user
- Use a simple password (letters and numbers) for the Oracle app user: special characters such as `!` cause `ORA-00922` in SQL*Plus unless quoted, and characters such as `&` or `^` can break `.env` loading in `run.bat`
- `schema.sql` runs on every startup with `continue-on-error` enabled, so existing tables are left alone. This also means a genuine schema error would not stop the app, so check the logs if a table seems to be missing
- History timestamps are stored in UTC and converted to the user's selected timezone in the browser
- The old SQLite `app.db` is no longer used and its data is not migrated, so accounts must be created again

## 🚧 Roadmap
- [x] **Weather Agent** - real-time weather and hourly forecasts
- [x] **Currency Agent** - live exchange rates with topic guard
- [x] **Translation Agent** - 30+ languages via local LibreTranslate
- [x] **Dictionary Agent** - English definitions, phonetics, examples via Free Dictionary API
- [x] **Interactive UI** - multi-column chat interface with history panel
- [x] **Dark/light mode** - synced across all pages including login/signup
- [x] **Authentication** - signup, login, BCrypt passwords, session cookies, remember me
- [x] **Persistent memory** - per-user chat history in Oracle Database, searchable, deletable
- [x] **Timezone support** - IANA timezone selector, all times consistent across the UI
- [x] **RAG pipeline** - web scraping → chunking → local embeddings → ChromaDB
- [x] **Hybrid semantic search** - vector search + keyword search merged and deduplicated
- [x] **Reranking** - cosine similarity reranking of retrieved candidates
- [x] **Knowledge base UI** - `/knowledge` page to manage ingested sources per agent
- [x] **Spring Boot migration** - Maven dependency management, embedded Tomcat, JdbcTemplate
- [x] **Token usage tracking** - per-message token count and daily total vs. limit
- [x] **Oracle Database migration** - SQLite replaced by a local Oracle Database 23ai Free instance
- [ ] **Agent Capability Expansion** - add more agents (news, schedules, etc.)
- [ ] **Streaming responses** - stream Groq output token by token instead of waiting
- [ ] **User preferences** - per-user system prompt customization
- [ ] **RAG source attribution** - show which source each answer came from in the UI