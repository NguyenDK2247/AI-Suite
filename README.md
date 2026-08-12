# 🤖🌤️💱🌐 AI Suite

A personal AI agent and web interface for weather forecasting, currency exchange, and language translation, with a built-in knowledge base powered by RAG (Retrieval-Augmented Generation).

## 🚀 Current Features (WIP)
* **Weather Agent:** fetches real-time weather and hourly forecasts via OpenWeatherMap, with Groq (LLaMA 3.3 70B Versatile) providing natural language commentary
* **Currency Agent:** fetches live exchange rates via ExchangeRate-API and converts between currencies, with topic-guarded responses (regex prevents off-topic questions)
* **Translation Agent:** translates text between 30+ languages via a locally hosted LibreTranslate instance; auto-detects source language; includes retry logic for connection resilience
* **RAG Pipeline:** web scraper (Jsoup) ingests URLs into ChromaDB via local Ollama embeddings (`nomic-embed-text`), with hybrid search (semantic + keyword) and cosine-similarity reranking
* **Knowledge Base UI:** dedicated `/knowledge` page to add URLs per agent (Weather, Currency, Translation), with crawl depth control and real-time ingest feedback
* **Authentication:** BCrypt-hashed passwords, SQLite-backed user accounts, HttpOnly session cookies with optional 30-day "remember me"
* **Per-user history:** chat history stored server-side in SQLite, fully searchable, separate per agent and per account
* **Token usage tracking:** daily Groq token consumption displayed per message and as a running total against the 500,000/day limit; persists across page switches and app restarts; resets automatically at midnight
* **Timezone selector:** IANA timezone dropdown (via WorldTimeAPI) synced across all pages and persisted per user; all timestamps (chat bubbles, forecast cards, history) respect the chosen timezone
* **Dark/light mode:** synced across login, signup, and all chat pages via `localStorage`
* **Multi-agent sidebar:** Weather, Currency, Translation, and Knowledge tabs with persistent navigation

## 🛠️ Tech Stack

| Layer | Technology |
|---|---|
| Frontend | Vanilla HTML/CSS/JS (no framework) |
| Backend | Spring Boot 3.2 (Java 17) |
| LLM | Groq API - LLaMA 3.3 70B Versatile |
| Weather data | OpenWeatherMap API |
| Currency data | ExchangeRate-API (no key required) |
| Translation | LibreTranslate (local) |
| Embeddings | Ollama - `nomic-embed-text` (local) |
| Vector store | ChromaDB v2 (local) |
| Web scraping | Jsoup |
| Database | SQLite via `sqlite-jdbc` |
| Password hashing | BCrypt (jBCrypt via Maven) |
| Build tool | Maven 3.9+ |

## 📦 Getting Started

### Prerequisites

1. **Java 17+**
2. **Maven 3.9+** - https://maven.apache.org/download.cgi (add `bin/` to PATH; set `JAVA_HOME` to JDK root)
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
6. **API keys** - set in `.env` at project root:
   ```
   OPENWEATHER_API_KEY=your_key_here
   GROQ_API_KEY=your_key_here
   ```

### Running

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

Maven downloads all dependencies automatically on first run. Then open `http://localhost:8080/signup` to create an account.

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
│       ├── schema.sql
│       └── static/
│           ├── auth.css
│           ├── currency.html
│           ├── index.html
│           ├── ingest.html
│           ├── login.html
│           ├── signup.html
│           ├── styles.css
│           └── translation.html
├── .env                    # API keys (git-ignored)
├── .gitignore
├── app.db                  # SQLite database (git-ignored)
├── chroma/                 # ChromaDB data (git-ignored)
├── pom.xml
└── run.bat
```

### Notes on ChromaDB (Windows)
ChromaDB on Windows binds to the IPv6 loopback (`::1`) rather than IPv4 (`127.0.0.1`). If the app cannot reach ChromaDB, verify with:
```
curl http://localhost:8000/api/v2/heartbeat
```
The app is pre-configured for this in `application.properties`.

## 🚧 Roadmap
- [x] **Weather Agent** - real-time weather and hourly forecasts
- [x] **Currency Agent** - live exchange rates with topic guard
- [x] **Translation Agent** - 30+ languages via local LibreTranslate
- [x] **Interactive UI** - multi-column chat interface with history panel
- [x] **Dark/light mode** - synced across all pages including login/signup
- [x] **Authentication** - signup, login, BCrypt passwords, session cookies, remember me
- [x] **Persistent memory** - per-user chat history in SQLite, searchable, deletable
- [x] **Timezone support** - IANA timezone selector, all times consistent across the UI
- [x] **RAG pipeline** - web scraping → chunking → local embeddings → ChromaDB
- [x] **Hybrid semantic search** - vector search + keyword search merged and deduplicated
- [x] **Reranking** - cosine similarity reranking of retrieved candidates
- [x] **Knowledge base UI** - `/knowledge` page to manage ingested sources per agent
- [x] **Spring Boot migration** - Maven dependency management, embedded Tomcat, JdbcTemplate
- [x] **Token usage tracking** - per-message token count and daily total vs. 500,000 limit
- [ ] **Agent Capability Expansion** - add more agents (news, schedules, etc.)
- [ ] **Streaming responses** - stream Groq output token by token instead of waiting
- [ ] **User preferences** - per-user system prompt customisation
- [ ] **RAG source attribution** - show which source each answer came from in the UI
