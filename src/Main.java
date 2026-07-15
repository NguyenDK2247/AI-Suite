import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

public class Main {

    private static final int PORT = 8080;

    public static void main(String[] args) throws IOException, SQLException {

        // ── API keys ──────────────────────────────────────────────────────────
        String weatherKey = System.getenv("OPENWEATHER_API_KEY");
        String groqKey = System.getenv("GROQ_API_KEY");
        if (weatherKey == null || groqKey == null) {
            System.err.println("Error: set OPENWEATHER_API_KEY and GROQ_API_KEY in .env");
            System.exit(1);
        }

        // ── Database ──────────────────────────────────────────────────────────
        Database.init();
        SessionManager sessions = new SessionManager();

        // ── Services ──────────────────────────────────────────────────────────
        GroqService weatherGroq = new GroqService(groqKey, GroqService.WEATHER_PROMPT);
        GroqService currencyGroq = new GroqService(groqKey, GroqService.CURRENCY_PROMPT);
        WeatherService weatherService = new WeatherService(weatherKey);
        CurrencyService currencyService = new CurrencyService();

        // ── Handlers ──────────────────────────────────────────────────────────
        AuthHandler authHandler = new AuthHandler(sessions);
        HistoryHandler historyHandler = new HistoryHandler(sessions);
        ChatHandler chatHandler = new ChatHandler(weatherService, weatherGroq, sessions);
        CurrencyHandler currencyHandler = new CurrencyHandler(currencyGroq, currencyService, sessions);

        // ── HTTP server ───────────────────────────────────────────────────────
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Auth routes (no session required)
        server.createContext("/auth/signup", authHandler);
        server.createContext("/auth/login", authHandler);
        server.createContext("/auth/logout", authHandler);
        server.createContext("/auth/me", authHandler);

        // History API (session required — enforced inside handler)
        server.createContext("/history", historyHandler);

        // Chat APIs (session required — enforced inside handler)
        server.createContext("/chat", chatHandler);
        server.createContext("/currency-chat", currencyHandler);

        // Static pages — redirect to /login if no valid session
        server.createContext("/", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET"))
                return;
            if (!isAuthenticated(exchange, sessions)) {
                redirect(exchange, "/login");
                return;
            }
            serveFile(exchange, "frontend/index.html", "text/html; charset=utf-8");
        });

        server.createContext("/currency", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET"))
                return;
            if (!isAuthenticated(exchange, sessions)) {
                redirect(exchange, "/login");
                return;
            }
            serveFile(exchange, "frontend/currency.html", "text/html; charset=utf-8");
        });

        // Auth pages — always public
        server.createContext("/login", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET"))
                return;
            serveFile(exchange, "frontend/login.html", "text/html; charset=utf-8");
        });

        server.createContext("/signup", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET"))
                return;
            serveFile(exchange, "frontend/signup.html", "text/html; charset=utf-8");
        });

        // Shared static assets
        server.createContext("/styles.css", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET"))
                return;
            serveFile(exchange, "frontend/styles.css", "text/css; charset=utf-8");
        });

        server.createContext("/auth.css", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET"))
                return;
            serveFile(exchange, "frontend/auth.css", "text/css; charset=utf-8");
        });

        server.setExecutor(null);
        server.start();

        System.out.println("Server running at http://localhost:" + PORT);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static boolean isAuthenticated(HttpExchange exchange, SessionManager sessions) {
        try {
            String token = sessions.extractToken(exchange);
            return sessions.validateSession(token) != -1;
        } catch (Exception e) {
            return false;
        }
    }

    private static void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().add("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.getResponseBody().close();
    }

    static void serveFile(HttpExchange exchange, String filePath, String contentType) throws IOException {
        try {
            byte[] bytes = Files.readAllBytes(Path.of(filePath));
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } catch (IOException e) {
            String msg = filePath + " not found";
            exchange.getResponseHeaders().add("Content-Type", "text/plain");
            exchange.sendResponseHeaders(404, msg.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(msg.getBytes());
            }
        }
    }
}
