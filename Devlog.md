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
 
### `011` - Spring Boot Migration
- **Type:** Refactor
- **Status:** Complete
- **Description:** Migrated the entire backend from plain Java (`com.sun.net.httpserver`) to Spring Boot 3.2 with Maven. The frontend, business logic, database schema, and external integrations (Groq, OpenWeatherMap, ExchangeRate-API, Ollama, ChromaDB) are unchanged. The migration eliminates manual jar management, manual JSON parsing, hand-rolled session cookies, and manual static file serving - replacing them all with Spring Boot conventions.
- **Implemented:**
  - `pom.xml` - Maven build file; all dependencies declared (spring-boot-starter-web, spring-boot-starter-jdbc, sqlite-jdbc, jbcrypt, jsoup, jackson-databind); replaces `lib/` folder and `run.bat` classpath management entirely
  - `src/main/resources/application.properties` - server port, SQLite datasource, Jackson config, static resource path, API key bindings via `${ENV_VAR}`, Ollama and ChromaDB URLs
  - `src/main/resources/schema.sql` - database schema auto-run on startup via `spring.sql.init.mode=always`; replaces `Database.java`
  - `AiSuiteApplication.java` - Spring Boot entry point with `@SpringBootApplication`; replaces `Main.java`
  - `config/DatabaseConfig.java` - `JdbcTemplate` bean with WAL mode and foreign keys enabled on startup
  - `config/AgentConfig.java` - manually creates two named `GroqService` beans (`weatherGroq`, `currencyGroq`) with their respective system prompts; avoids circular dependency that caused `StackOverflowError`
  - `config/WebConfig.java` - registers `AuthInterceptor` on protected API routes; CORS configuration
  - `config/AuthInterceptor.java` - `HandlerInterceptor` that validates session cookie and attaches `userId` to request attributes; replaces session checks scattered across all handlers
  - `controller/AuthController.java` - `@RestController` for `/auth/signup`, `/auth/login`, `/auth/logout`, `/auth/me`; replaces `AuthHandler.java`
  - `controller/ChatController.java` - `@RestController` for `POST /chat`; RAG runs before city detection so knowledge-base questions work without a city name in the query
  - `controller/CurrencyController.java` - `@RestController` for `POST /currency-chat`; RAG runs before topic guard so knowledge-base questions bypass the regex guard when relevant context is found
  - `controller/HistoryController.java` - `@RestController` for `GET/POST/DELETE /history`; replaces `HistoryHandler.java`
  - `controller/IngestController.java` - `@RestController` for `POST /ingest` and `GET /ingest/collections`; replaces `IngestHandler.java`
  - `controller/PageController.java` - serves HTML pages using `ClassPathResource`; redirects unauthenticated requests to `/login`; replaces static file routes in `Main.java`
  - `controller/UserController.java` - `@RestController` for `GET/PATCH /user/settings`; replaces `UserHandler.java`
  - `service/SessionService.java` - session token creation and validation using `JdbcTemplate`; replaces `SessionManager.java`
  - `service/UserService.java` - user signup, login, lookup, timezone update using `JdbcTemplate` and jBCrypt; replaces the user logic spread across `AuthHandler.java` and `Database.java`
  - `service/HistoryService.java` - history CRUD using `JdbcTemplate`; replaces `HistoryHandler.java` DB logic
  - `service/GroqService.java` - not a `@Service` bean (instantiated by `AgentConfig`); two-arg constructor takes system prompt directly
  - `service/WeatherService.java` - `@Service` with `@Value("${app.openweather.api-key}")`; logic unchanged
  - `service/CurrencyService.java` - `@Service`; logic unchanged
  - `service/EmbeddingService.java` - `@Service` with `@Value("${app.ollama.url}")`; logic unchanged
  - `service/VectorStore.java` - `@Service` with `@Value("${app.chroma.url}")`; logic unchanged
  - `service/WebScraper.java` - `@Service`; logic unchanged
  - `service/RagService.java` - `@Service`; logic unchanged
  - `service/Reranker.java` - `@Service`; logic unchanged
  - `model/User.java` - plain POJO for user data
  - `model/HistoryEntry.java` - plain POJO for history entries; uses `JsonNode` for extra field
  - `src/main/resources/static/` - all frontend files moved here from `frontend/`; served automatically by Spring Boot without any explicit routes
  - `run.bat` - loads `.env` then calls `mvnw.cmd spring-boot:run`; no more `javac` or classpath management
- **Depends on:** All previous entries; Maven 3.9+; Java 17+
- **Notes:** `BCrypt.java` source file replaced by the `org.mindrot:jbcrypt:0.4` Maven dependency. `lib/` and `out/` folders deleted - Maven uses `~/.m2/repository` for dependencies and `target/` for compiled output. `JAVA_HOME` must point to the JDK root folder (e.g. `C:\Program Files\Eclipse Adoptium\jdk-17.0.13.11-hotspot`), not the `bin\java.exe` path. User-level environment variables take priority over system-level ones on Windows. `forward:` in Spring MVC controllers causes `StackOverflowError` if the forwarded path is also handled by the same controller - use `ClassPathResource` to serve static files directly instead. `GroqService` must not be a `@Service` bean when it has a factory method that creates instances of itself - Spring will attempt to wire it recursively, causing a `StackOverflowError`.

---

### `012` - Translation Agent
- **Type:** Feature
- **Status:** Complete
- **Description:** A third AI agent dedicated to language translation and language-related questions. Uses a locally hosted LibreTranslate instance for actual translation (free, unlimited, no API key), and Groq for natural language commentary around the result. Detects source language automatically via LibreTranslate's `/detect` endpoint if not specified. A purple translation card renders in the chat bubble showing the source language, target language, original text, and translated text. Includes retry logic (3 attempts, 2s apart) to handle LibreTranslate connection resets after idle periods.
- **Implemented:**
  - `service/TranslationService.java` - calls local LibreTranslate `/translate` and `/detect` endpoints; URL injected via `${app.libretranslate.url}`; language name → ISO 639-1 code resolution via `HashMap` (31 languages); `postWithRetry()` wraps every request in a 3-attempt retry loop catching `IOException` (covers `HttpTimeoutException` and connection resets); connect timeout 30s, translate timeout 45s, detect timeout 20s
  - `controller/TranslationController.java` - handles `POST /translate-chat`; topic guard via `TOPIC_PATTERN`; extracts text and target/source language from natural language queries via `TRANSLATE_PATTERN` regex (handles quoted text, "translate X to Y", "how do you say X in Y" phrasings); RAG retrieves from `translation_knowledge` collection before topic guard; falls back to Groq general language knowledge if no specific translation is detected
  - `config/AgentConfig.java` - added `translationGroq` bean with `TRANSLATION_PROMPT`
  - `service/GroqService.java` - added `TRANSLATION_PROMPT` constant
  - `config/WebConfig.java` - added `/translate-chat` to protected interceptor routes
  - `controller/PageController.java` - added `/translation` route serving `translation.html`
  - `controller/IngestController.java` - added `translation_knowledge` to collections list
  - `static/translation.html` - translation chat page; purple gradient card (`.translation-card`) showing source/target lang badges, original text in italics, translated text large; quick-chips for common language pairs; full history, timezone, theme support matching other pages
  - `static/index.html`, `currency.html`, `ingest.html` - 🌐 Translate tab added to sidebar
  - `src/main/resources/application.properties` - added `app.libretranslate.url=http://localhost:5000`
- **Depends on:** LibreTranslate (local, `pip install libretranslate`), Entry `011`, Groq API
- **Notes:** LibreTranslate must be running before the app starts (`libretranslate --host 0.0.0.0 --port 5000`). First run downloads ~1-2 GB of language models; subsequent runs start in seconds. `Map.ofEntries()` throws `IllegalArgumentException` on duplicate keys at class initialisation - always use a `static {}` block with `HashMap` for large maps. `HttpTimeoutException` extends `IOException` so catching both in a multi-catch is a compile error - `IOException` alone is sufficient. The `Connection reset` error occurs when LibreTranslate's worker process idles and drops the socket; the retry loop recovers from this automatically without user-facing errors.

---

### `013` - Token Usage Tracking
- **Type:** Feature
- **Status:** Complete
- **Description:** Tracks Groq API token consumption per user per day, persisted in SQLite so usage survives app restarts and page switches. Every bot message displays how many tokens that message used, and the chat header shows a running daily total against Groq's 500,000 token/day limit. The daily counter resets automatically at midnight by keying records on the current date string. Token counts are shared across all three agents - switching from Weather to Currency to Translation and back shows the same accumulated daily total everywhere.
- **Implemented:**
  - `service/TokenService.java` - new service; creates `token_usage` table (user_id, date, total_tokens, PRIMARY KEY (user_id, date)); `addTokens()` uses SQLite's `INSERT ... ON CONFLICT DO UPDATE` to upsert atomically; `getTodayTotal()` queries by `LocalDate.now().toString()` so it returns 0 automatically on a new day without any cron job or scheduled reset; `getDailyLimit()` returns 500,000
  - `controller/TokenController.java` - new controller; `GET /tokens` returns `{todayTotal, dailyLimit}` for the logged-in user; `POST /tokens` adds tokens and returns the updated total; both routes session-gated via `AuthInterceptor`
  - `config/WebConfig.java` - added `/tokens/**` to protected interceptor routes; also fixed a pre-existing duplicate line bug in `addPathPatterns`
  - `service/GroqService.java` - added `TokenUsage` record (promptTokens, completionTokens, totalTokens); added `ChatResult` record (reply, usage); `chatWithUsage()` parses the `usage` block from Groq's JSON response and returns both the reply and token counts; old `chat()` delegates to `chatWithUsage()` for backward compatibility; `resetHistory()` also resets `sessionTotalTokens`
  - `controller/ChatController.java` - calls `chatWithUsage()`, persists tokens via `tokenService.addTokens()`, reads back `todayTotal`, includes `{promptTokens, completionTokens, totalTokens, todayTotal, dailyLimit}` in every response
  - `controller/CurrencyController.java` - same token persistence pattern as ChatController
  - `controller/TranslationController.java` - same token persistence pattern; fixed missing `import com.aisuite.service.TokenService` (other controllers use wildcard imports; this one uses explicit imports)
  - `static/index.html` - added `token-display` element to chat header; `updateTokenDisplay(usage)` shows `↗ 267 this msg  |  1,243 / 500,000 today` after each message; `updateTokenDisplayFromTotal()` shows `1,243 / 500,000 tokens used today` on boot before any message is sent; boot sequence fetches `GET /tokens` to populate the display immediately on page load; per-message token badge (`.token-badge`) appended below each bot bubble via third argument to `buildBotHtml()`
  - `static/currency.html` - same token display pattern; fixed stray extra `}` left by sed replacement
  - `static/translation.html` - same token display pattern; fixed stray extra `}` left by sed replacement
  - `static/styles.css` - added `.token-badge` style (10px, muted color, 0.7 opacity)
- **Depends on:** Entry `011` (Spring Boot), Entry `012` (all three agents), Groq API
- **Notes:** `INSERT ... ON CONFLICT(user_id, date) DO UPDATE SET total_tokens = total_tokens + excluded.total_tokens` is SQLite's upsert syntax - requires SQLite 3.24+. The daily limit of 500,000 is hardcoded in `TokenService.getDailyLimit()` for LLaMA 3.3 70B Versatile on Groq's free tier; update this constant if you switch models or upgrade your Groq plan. Extra `}` braces introduced by `sed` replacements inside existing function blocks are a common pitfall - always verify the resulting file structure after sed edits. `HttpTimeoutException` extends `IOException` so catching both in a multi-catch is a compile error - `IOException` alone is sufficient.

---

### 014 - Unicode Character Fix (CJK and Non-ASCII)
- **Type:** Fix
- **Status:** Complete
- **Description:** Non-ASCII characters (Chinese, Japanese, Korean, Arabic, Hebrew, etc.) were rendering as empty strings in bot responses and translated text. Groq's API and LibreTranslate both return non-ASCII characters as JSON Unicode escape sequences (`\uXXXX`) rather than literal UTF-8 characters. The manual JSON parsers in `GroqService`, `TranslationService`, and `VectorStore` only handled `\n`, `\"`, and `\\` but silently passed `\uXXXX` through unchanged, which browsers could not render as characters.
- **Implemented:**
  - `service/GroqService.java` - `unescape()` rewritten to walk the string character by character; detects `\u` followed by exactly 4 hex digits, converts to a Unicode codepoint via `Integer.parseInt(..., 16)`, and appends the actual character via `appendCodePoint()`; covers all Unicode planes including CJK Unified Ideographs, Arabic, Hebrew, Cyrillic, etc.
  - `service/TranslationService.java` - same logic added as `unescapeJson()` replacing the one-liner in `extractString()`; fixes translated Chinese/Japanese/Korean text returned by LibreTranslate
  - `service/VectorStore.java` - same `unescapeJson()` helper added and applied in `parseDocuments()`; fixes RAG chunks containing non-ASCII content from ingested pages
- **Depends on:** Entry `012` (Translation Agent), Entry `008` (RAG Pipeline)
- **Notes:** This bug affects any language with characters outside the ASCII range - not just CJK. Any service that manually parses JSON strings rather than using a proper JSON library (Jackson, Gson) must implement `\uXXXX` decoding explicitly. The correct fix pattern is to check for `\` + `u` + 4 hex digits and call `appendCodePoint(Integer.parseInt(hex, 16))`. Using Jackson's `ObjectMapper` to parse Groq/API responses would eliminate this class of bug entirely and is worth considering in a future refactor.