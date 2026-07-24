# 🤖🌤️💱 AI Agent and Interface 

A personal AI agent and web interface for weather forecasting and currency exchange, with a built-in knowledge base powered by RAG (Retrieval-Augmented Generation).

## 🚀 Current Features (WIP)
* **Weather Agent:** fetches real-time weather and hourly forecasts via OpenWeatherMap, with Groq (LLaMA 3.3 70B Versatile) providing natural language commentary
* **Currency Agent:** fetches live exchange rates via ExchangeRate-API and converts between currencies, with topic-guarded responses (regex prevents off-topic questions)
* **RAG Pipeline:** web scraper (Jsoup) ingests URLs into ChromaDB via local Ollama embeddings (`nomic-embed-text`), with hybrid search (semantic + keyword) and cosine-similarity reranking
* **Knowledge Base UI:** dedicated `/knowledge` page to add URLs per agent, with crawl depth control and real-time ingest feedback
* **Authentication:** BCrypt-hashed passwords, SQLite-backed user accounts, HttpOnly session cookies with optional 30-day "remember me"
* **Per-user history:** chat history stored server-side in SQLite, fully searchable, separate per agent and per account
* **Timezone selector:** IANA timezone dropdown (via WorldTimeAPI) synced across all pages and persisted per user; all timestamps (chat bubbles, forecast cards, history) respect the chosen timezone
* **Dark/light mode:** synced across login, signup, and all chat pages via `localStorage`
* **Multi-agent sidebar:** Weather, Currency, and Knowledge tabs with persistent navigation

## 🛠️ Tech Stack (WIP)

| Layer | Technology |
|---|---|
| Frontend | Vanilla HTML/CSS/JS (no framework) |
| Backend | Plain Java (`com.sun.net.httpserver`) |
| LLM | Groq API - LLaMA 3.3 70B Versatile |
| Weather data | OpenWeatherMap API |
| Currency data | ExchangeRate-API (no key required) |
| Embeddings | Ollama - `nomic-embed-text` (local) |
| Vector store | ChromaDB v2 (local) |
| Web scraping | Jsoup |
| Database | SQLite via `sqlite-jdbc` |
| Password hashing | BCrypt (jBCrypt source) |

## 📦 Getting Started

### Prerequisites
 
1. **Java 17+**
2. **Ollama** - https://ollama.com/download
```
   ollama pull nomic-embed-text
```
3. **ChromaDB** - requires Python 3.11+
```
   pip install chromadb
```
4. **API keys** - set in `.env`:
```
   OPENWEATHER_API_KEY=your_key_here
   GROQ_API_KEY=your_key_here
```

### lib\ dependencies
 
Download these jars into the `lib\` folder:
 
| Jar | Source |
|---|---|
| `sqlite-jdbc-3.41.2.2.jar` | [github.com/xerial/sqlite-jdbc/releases](https://github.com/xerial/sqlite-jdbc/releases) |
| `slf4j-api-2.0.17.jar` | [mvnrepository.com](https://mvnrepository.com/) |
| `slf4j-simple-2.0.17.jar` | [mvnrepository.com](https://mvnrepository.com/) |
| `jsoup-1.22.2.jar` | [jsoup.org/download](https://jsoup.org/download) |

### Running
 
Open three terminals:
 
**Terminal 1 - Ollama** (may already be running as a service):
```
ollama serve
```
 
**Terminal 2 - ChromaDB:**
```
chroma run --host localhost --port 8000
```
 
**Terminal 3 - App** (from project root):
```
run.bat
```
 
Then open `http://localhost:8080/signup` to create an account.

### Project Structure
 
```
project/
├── frontend/
│   ├── index.html          # Weather chat
│   ├── currency.html       # Currency chat
│   ├── ingest.html         # Knowledge base manager
│   ├── login.html
│   ├── signup.html
│   ├── styles.css
│   └── auth.css
├── src/
│   ├── Main.java
│   ├── AuthHandler.java
│   ├── ChatHandler.java
│   ├── CurrencyHandler.java
│   ├── HistoryHandler.java
│   ├── IngestHandler.java
│   ├── UserHandler.java
│   ├── Database.java
│   ├── SessionManager.java
│   ├── GroqService.java
│   ├── WeatherService.java
│   ├── CurrencyService.java
│   ├── EmbeddingService.java
│   ├── VectorStore.java
│   ├── WebScraper.java
│   ├── RagService.java
│   ├── Reranker.java
│   └── BCrypt.java
├── lib/                    # External jars
├── out/                    # Compiled classes (git-ignored)
├── app.db                  # SQLite database (git-ignored)
├── .env                    # API keys (git-ignored)
├── .gitignore
└── run.bat
```

## 🚧 Roadmaps & Upcoming Enhancements
- [x] **Weather Agent** - real-time weather and hourly forecasts
- [x] **Currency Agent** - live exchange rates with topic guard
- [x] **Interactive UI** - multi-column chat interface with history panel
- [x] **Dark/light mode** - synced across all pages
- [x] **Authentication** - signup, login, BCrypt passwords, session cookies, remember me
- [x] **Persistent memory** - per-user chat history in SQLite, searchable, deletable
- [x] **Timezone support** - IANA timezone selector, all times consistent across the UI
- [x] **RAG pipeline** - web scraping → chunking → local embeddings → ChromaDB
- [x] **Hybrid semantic search** - vector search + keyword search merged and deduplicated
- [x] **Reranking** - cosine similarity reranking of retrieved candidates
- [x] **Knowledge base UI** - `/knowledge` page to manage ingested sources per agent
- [ ] **Agent Capability Expansion** - add more agents (news, schedules, etc.)
- [ ] **Streaming responses** - stream Groq output token by token instead of waiting
- [ ] **User preferences** - per-user system prompt customisation
- [ ] **RAG source attribution** - show which source each answer came from in the UI
