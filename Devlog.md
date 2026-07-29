# 📓 Project Devlog

A running record of every feature, enhancement, and fix built for this project. Each entry follows a consistent structure for traceability.

---

## Entry Format

```
### [Entry Number] - [Title]
- **Type:**        Feature | Enhancement | Fix | Refactor
- **Status:**      Complete | Partial | WIP
- **Description:** What it is and why it was added
- **Implemented:** Concrete files and components changed
- **Depends on:**  External tools, APIs, or other entries
- **Notes:**       Caveats, limitations, or future work
```

---

## Entries

---

### `001` - Weather Agent
- **Type:** Feature
- **Status:** Complete
- **Description:** A chat-based AI agent that fetches real-time weather data and hourly forecasts for any city in the world. The agent accepts natural language queries, extracts the city name and an optional forecast horizon (e.g. "next 6 hours") via regex, fetches live data, and passes it to Groq's LLaMA 3.3 70B model to generate a natural language response. A visual weather card is rendered in the chat bubble showing temperature, humidity, wind speed, and an emoji matched to the weather condition.
- **Implemented:**
  - `src/WeatherService.java` - fetches current weather and 3-hour forecast slots from OpenWeatherMap; parses JSON manually without external libraries
  - `src/ChatHandler.java` - handles `POST /chat`; extracts city and hours via regex (`CITY_PATTERN`, `HOURS_PATTERN`); builds prompt for Groq; returns weather data JSON alongside the reply
  - `src/GroqService.java` - wraps the Groq OpenAI-compatible API; maintains per-instance conversation history; accepts a configurable system prompt
  - `frontend/index.html` - chat UI with weather card renderer (`buildBotHtml`), forecast carousel, weather emoji mapper, typing indicator, and city quick-chips
  - `frontend/styles.css` - weather card styles (`.weather-card`, `.wc-forecast`, `.wc-forecast-item`)
- **Depends on:** OpenWeatherMap API (`OPENWEATHER_API_KEY`), Groq API (`GROQ_API_KEY`)
- **Notes:** City detection uses regex and may miss ambiguous city names. The forecast uses OpenWeatherMap's free 3-hour slots, so "next 6 hours" returns 2 slots.

---

### `002` - Currency Agent
- **Type:** Feature
- **Status:** Complete
- **Description:** A second chat agent focused exclusively on currency conversion and foreign exchange. Uses regex to guard against off-topic questions (weather, recipes, code, etc.) and redirects them with a polite message. When a conversion is detected, it fetches a live exchange rate from ExchangeRate-API (no API key required) and passes the real rate to Groq for commentary. A visual rate card is rendered in the chat bubble showing the base/target pair, live rate, and converted amount.
- **Implemented:**
  - `src/CurrencyService.java` - fetches live rates from `api.exchangerate-api.com/v4/latest/{base}`; manually parses the rates JSON block
  - `src/CurrencyHandler.java` - handles `POST /currency-chat`; topic guard via `CURRENCY_PATTERN` and `OFF_TOPIC_PATTERN`; detects currency pair and amount via `CONVERSION_PATTERN`; resolves currency names (e.g. "Vietnamese dong" → "VND") via `NAME_MAP`; builds prompt with live rate injected
  - `frontend/currency.html` - currency chat UI with rate card renderer, quick-chips for common pairs
  - `frontend/styles.css` - rate card styles (`.rate-card`, `.rc-pair`, `.rc-rate`, `.rc-conversion`)
- **Depends on:** ExchangeRate-API (no key required), Groq API, Entry `001` (shared `GroqService`)
- **Notes:** ExchangeRate-API free tier gives ~1,500 requests/month. Multi-word currency names (e.g. "Vietnamese dong", "US dollar") required extending `CONVERSION_PATTERN` to match two-word tokens.

---

### `003` - Shared Chat UI and Sidebar
- **Type:** Feature
- **Status:** Complete
- **Description:** A shared two-panel chat layout used by both agents. The left sidebar provides tab navigation between agents. The right panel shows searchable, deletable chat history. Both pages share a single `styles.css` and a consistent design language.
- **Implemented:**
  - `frontend/styles.css` - full stylesheet; CSS variables for theming; `.app` grid layout (sidebar + chat + history); message bubble styles; typing indicator animation; history panel; chip styles; sidebar tab styles
  - `frontend/index.html` - weather chat page with sidebar
  - `frontend/currency.html` - currency chat page with sidebar
  - `src/Main.java` - static file serving for all frontend assets via `serveFile()` helper
- **Depends on:** Entry `001`, Entry `002`
- **Notes:** The layout uses CSS Grid with three columns: `sidebar-w`, `1fr`, `300px`. The sidebar width is controlled by the `--sidebar-w` CSS variable.

---

### `004` - Dark / Light Mode Toggle
- **Type:** Enhancement
- **Status:** Complete
- **Description:** A theme toggle that switches between dark mode (default) and light mode across all pages. The chosen theme is persisted in `localStorage` under the key `app_theme` so it survives page reloads and navigation between tabs. The toggle button shows ☀️ in dark mode and 🌙 in light mode.
- **Implemented:**
  - `frontend/styles.css` - `body.light-mode` block overriding all CSS variables for light theme
  - `frontend/index.html` - `toggleTheme()` and `initTheme()` functions; theme button in history panel header
  - `frontend/currency.html` - same functions mirrored
  - `frontend/auth.css` - light mode variables for login/signup pages
  - `frontend/login.html` - theme toggle button added to the login form row
  - `frontend/signup.html` - `initTheme()` called on load so mode is consistent when navigating from login
- **Depends on:** Entry `003`
- **Notes:** Theme is synced across all pages via the shared `app_theme` localStorage key. Auth pages (login, signup) read the same key so the mode is consistent before and after login.

---

### `005` - Authentication (Signup, Login, Logout)
- **Type:** Feature
- **Status:** Complete
- **Description:** A full authentication system with username/password signup, BCrypt password hashing, SQLite-backed user storage, and HttpOnly session cookies. Unauthenticated requests to protected pages (`/`, `/currency`, `/knowledge`) are redirected to `/login`. Sessions last 7 days by default, or 30 days with "Remember me".
- **Implemented:**
  - `src/BCrypt.java` - BCrypt implementation (jBCrypt source, package declaration removed to compile in default package); cost factor 12
  - `src/Database.java` - SQLite schema: `users` (id, username, password_hash, timezone, created_at), `sessions` (token, user_id, expires_at), `history` (id, user_id, page, question, answer, extra_json, created_at); migration support via `ALTER TABLE` for existing databases
  - `src/SessionManager.java` - generates 32-byte URL-safe Base64 session tokens; validates tokens against expiry; extracts token from `Cookie` header
  - `src/AuthHandler.java` - handles `/auth/signup`, `/auth/login`, `/auth/logout`, `/auth/me`; username validation (3–20 chars, alphanumeric + underscore); password minimum 8 chars; timing-safe login (BCrypt runs even for unknown usernames)
  - `src/Main.java` - auth-gated static routes; `isAuthenticated()` helper; 302 redirect to `/login`
  - `frontend/login.html` - login form with error banner, Enter key support, "Remember me" checkbox, theme toggle
  - `frontend/signup.html` - signup form with username format hint, password strength meter (5-level), confirm password match indicator
  - `frontend/auth.css` - card layout, field styles, custom checkbox, strength bar, theme toggle button
- **Depends on:** SQLite JDBC jar, slf4j jars, Entry `003`
- **Notes:** BCrypt is included as source (not a jar) to avoid an extra dependency. The "Remember me" checkbox sets `Max-Age=2592000` (30 days); unchecked sets a session cookie (expires on browser close).

---

### `006` - Per-User Server-Side History
- **Type:** Feature
- **Status:** Complete
- **Description:** Chat history moved from `localStorage` to a server-side SQLite table, stored per user and per agent page. History persists across devices and browser clears. Each entry stores the question, answer, a timestamp (UTC), and optional extra JSON (weather data or rate data for replaying cards). The history panel supports search, individual deletion, and full clear.
- **Implemented:**
  - `src/HistoryHandler.java` - handles `GET /history?page=`, `POST /history`, `DELETE /history?id=`, `DELETE /history?page=`; all endpoints session-gated; `extra_json` column stores raw weather/rate data for card replay
  - `src/Database.java` - `history` table with foreign key to `users`
  - `frontend/index.html` - `loadHistory()`, `addToHistory()`, `deleteHistoryItem()`, `clearHistory()`, `renderHistory()` all converted from localStorage to fetch calls
  - `frontend/currency.html` - same conversion
- **Depends on:** Entry `005`
- **Notes:** Timestamps are stored as UTC strings by SQLite's `datetime('now')`. Conversion to the user's local timezone happens on the frontend at render time.

---

### `007` - Timezone Selector
- **Type:** Feature
- **Status:** Complete
- **Description:** A timezone dropdown in the sidebar that applies the chosen timezone to every time displayed on the site - chat bubble timestamps, forecast card times, and history panel timestamps. The selected timezone is persisted in both `localStorage` and the server-side `users` table so it syncs across devices. Timezone list is fetched from WorldTimeAPI with a 20-timezone fallback. Each option displays the city name and current UTC offset (e.g. `Ho Chi Minh (GMT+7)`) computed via the browser's `Intl.DateTimeFormat` API.
- **Implemented:**
  - `src/UserHandler.java` - handles `GET /user/settings` and `PATCH /user/settings`; reads/writes `timezone` column on the `users` table
  - `src/Database.java` - `timezone TEXT NOT NULL DEFAULT 'UTC'` column added to `users`; migration via `ALTER TABLE` for existing databases
  - `src/Main.java` - `/user/settings` route registered
  - `frontend/styles.css` - `.sidebar-tz`, `.tz-label`, `.tz-select` styles
  - `frontend/index.html` - `currentTz` state variable; `toTZ()` (for forecast times), `toTZFull()` (for history timestamps), `nowInTZ()` (for chat bubble timestamps); `loadTimezones()` with WorldTimeAPI + fallback; `onTzChange()` persists to server and re-renders history
  - `frontend/currency.html` - same timezone functions mirrored
  - `frontend/ingest.html` - timezone selector included for consistency; syncs on load
- **Depends on:** Entry `005`, Entry `006`, WorldTimeAPI (`worldtimeapi.org`)
- **Notes:** WorldTimeAPI is called once on page load. If unreachable, a hardcoded list of 20 common timezones is used. The UTC offset label uses `Intl.DateTimeFormat` with `timeZoneName: 'shortOffset'` which automatically handles DST.

---

### `008` - RAG Pipeline (Web Scraping → Embeddings → ChromaDB)
- **Type:** Feature
- **Status:** Complete
- **Description:** A Retrieval-Augmented Generation pipeline that allows each agent to draw on scraped web content when answering questions. URLs are ingested via a Knowledge Base UI page: the page is scraped, split into overlapping chunks, embedded locally via Ollama, and stored in a per-agent ChromaDB collection. At query time, the user's message is embedded, the most relevant chunks are retrieved, and they are injected into the Groq prompt as background context.
- **Implemented:**
  - `src/WebScraper.java` - scrapes URLs with Jsoup; detects and fast-fails on known JS-rendered sites (AccuWeather, Bloomberg, etc.); detects JS-rendered pages by body length; special Wikipedia path using the MediaWiki API (`/w/api.php?action=query&prop=extracts&explaintext=true`) to bypass JS rendering; chunks text into 200-word overlapping segments (30-word overlap)
  - `src/EmbeddingService.java` - POSTs to Ollama's `/api/embeddings` with `nomic-embed-text`; 30s request timeout; validates response contains `embedding` field before parsing
  - `src/VectorStore.java` - ChromaDB v2 REST client; `getOrCreateCollection()`, `upsert()`, `query()`, `queryWithIds()`, `queryByKeyword()`; uses IPv6 loopback `[::1]:8000` to match Windows ChromaDB binding; embeddings serialised to 6 decimal float precision
  - `src/RagService.java` - orchestrates ingest (scrape → chunk → embed → upsert in batches of 5) and retrieval (hybrid search → merge → rerank → format context string)
  - `src/IngestHandler.java` - handles `POST /ingest`; session-gated; synchronous execution so errors are returned to the browser; validates URL scheme and depth
  - `src/Main.java` - `/ingest` and `/knowledge` routes registered; `EmbeddingService`, `VectorStore`, `WebScraper`, `Reranker`, `RagService` wired
  - `frontend/ingest.html` - Knowledge Base UI; URL input, agent selector, depth radio (single page / page + links); loading state during ingest; success shows page/chunk counts; error shown directly in UI; tips panel lists good and bad sources
  - `frontend/index.html` - 🧠 Knowledge tab added to sidebar
  - `frontend/currency.html` - 🧠 Knowledge tab added to sidebar
  - `run.bat` - Jsoup jar added to compile and runtime classpath
- **Depends on:** Ollama + `nomic-embed-text`, ChromaDB v2 (local), Jsoup jar, Entry `005`
- **Notes:** ChromaDB must be running (`chroma run --host localhost --port 8000`) before starting the app. ChromaDB on Windows binds to IPv6 `::1`, not IPv4 `127.0.0.1`. ChromaDB v2 rejects empty `metadata:{}` on collection creation - omit the field entirely. Data persists in `./chroma` across server restarts.

---

### `009` - Hybrid Search + Reranking
- **Type:** Enhancement
- **Status:** Complete
- **Description:** Improved RAG retrieval quality by combining semantic vector search with keyword search, then reranking the merged candidates by cosine similarity. Semantic search finds conceptually related chunks even without exact word matches. Keyword search catches exact terms (currency codes, city names) that might score poorly in vector space. Reranking re-scores all candidates by computing cosine similarity between the query embedding and each chunk embedding, giving a more precise final ordering than L2 distance alone.
- **Implemented:**
  - `src/VectorStore.java` - added `queryWithIds()` returning `ScoredChunk` records (document + L2 distance); added `queryByKeyword()` using ChromaDB's `$contains` filter via the `/get` endpoint; added `parseDistances()` and `parseChunks()` helpers
  - `src/Reranker.java` - new class; re-embeds each candidate chunk and the query; computes cosine similarity; sorts descending; only triggers when candidates ≥ 4 to avoid unnecessary Ollama calls
  - `src/RagService.java` - retrieval pipeline: semantic (top 6) + keyword (top 4) → deduplicate by first 80 chars → rerank → keep top 3; `extractKeywords()` strips stopwords using `HashSet` (not `Set.of()` which throws on duplicates) and limits to 4 terms; context string capped at 1500 chars
  - `src/WebScraper.java` - chunk size reduced from 400 → 200 words, overlap from 50 → 30 words for more precise retrieval
  - `run.bat` - `Reranker.java` added to compile list
- **Depends on:** Entry `008`
- **Notes:** Re-ingesting existing URLs is recommended after this change since chunk sizes changed. Reranking adds latency (~1–2s) proportional to the number of candidates since each requires an Ollama embed call. `Set.of()` throws `IllegalArgumentException` on duplicate elements at runtime - always use `HashSet` for stopword sets.

---

### `010` - Remember Me (Login Persistence)
- **Type:** Enhancement
- **Status:** Complete
- **Description:** A "Remember me" checkbox on the login page that controls session cookie lifetime. Checked sets `Max-Age=2592000` (30 days); unchecked sets a session cookie that expires when the browser closes. New account signups always get a 30-day cookie.
- **Implemented:**
  - `frontend/login.html` - custom-styled checkbox with CSS checkmark via `::after` pseudo-element; `rememberMe` boolean sent in POST body
  - `src/AuthHandler.java` - `extractBoolean()` reads `rememberMe` from request body; `setSessionCookie()` takes a boolean parameter and sets `Max-Age` conditionally
  - `frontend/auth.css` - `.form-row`, `.remember-label`, `.custom-checkbox`, `.checkmark` styles
- **Depends on:** Entry `005`
- **Notes:** The checkbox is keyboard-accessible via `focus-visible` outline on the hidden native input.

---