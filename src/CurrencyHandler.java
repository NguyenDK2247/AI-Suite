import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CurrencyHandler implements HttpHandler {

    private final SessionManager sessions;

    private final GroqService groqService;
    private final CurrencyService currencyService;

    // ── Topic guard ──────────────────────────────────────────────────────────────
    private static final Pattern CURRENCY_PATTERN = Pattern.compile(
            "(?i)\\b(" +
                    "convert|converting|exchange|exchanging|worth|rate|rates|price|cost|" +
                    "how much|what is|what's|calculate|calculating|" +
                    "currency|currencies|forex|fx|" +
                    "dollar|dollars|usd|euro|euros|eur|pound|pounds|gbp|yen|jpy|" +
                    "yuan|cny|rmb|franc|chf|rupee|inr|won|krw|ruble|rub|" +
                    "real|brl|peso|mxn|aud|cad|sgd|hkd|nzd|vnd|dong|" +
                    "bitcoin|btc|ethereum|eth|crypto" +
                    ")\\b");

    private static final Pattern OFF_TOPIC_PATTERN = Pattern.compile(
            "(?i)\\b(weather|forecast|temperature|rain|wind|humidity|" +
                    "recipe|cook|movie|music|sport|game|code|program|health|medical)\\b");

    // ── Currency extraction ──────────────────────────────────────────────────────

    // Matches: "100 USD to EUR", "USD to EUR", "dollars to euros"
    private static final Pattern CONVERSION_PATTERN = Pattern.compile(
            "(?i)([\\d,]+(?:\\.\\d+)?)\\s+([A-Za-z]{2,10})\\s+(?:to|in|into)\\s+([A-Za-z]{2,10})" +
                    "|([A-Za-z]{2,10})\\s+(?:to|in|into)\\s+([A-Za-z]{2,10})");

    // Common name -> ISO code
    private static final Map<String, String> NAME_MAP = Map.ofEntries(
            Map.entry("dollar", "USD"), Map.entry("dollars", "USD"),
            Map.entry("euro", "EUR"), Map.entry("euros", "EUR"),
            Map.entry("pound", "GBP"), Map.entry("pounds", "GBP"), Map.entry("sterling", "GBP"),
            Map.entry("yen", "JPY"),
            Map.entry("yuan", "CNY"), Map.entry("renminbi", "CNY"), Map.entry("rmb", "CNY"),
            Map.entry("franc", "CHF"), Map.entry("francs", "CHF"),
            Map.entry("rupee", "INR"), Map.entry("rupees", "INR"),
            Map.entry("won", "KRW"),
            Map.entry("ruble", "RUB"), Map.entry("rubles", "RUB"),
            Map.entry("real", "BRL"), Map.entry("reais", "BRL"),
            Map.entry("peso", "MXN"), Map.entry("pesos", "MXN"),
            Map.entry("dong", "VND"),
            Map.entry("bitcoin", "BTC"), Map.entry("btc", "BTC"),
            Map.entry("ethereum", "ETH"), Map.entry("eth", "ETH"));

    // ── Constructor ──────────────────────────────────────────────────────────────
    public CurrencyHandler(GroqService groqService, CurrencyService currencyService, SessionManager sessions) {
        this.groqService = groqService;
        this.currencyService = currencyService;
        this.sessions = sessions;
    }

    // ── Handle ───────────────────────────────────────────────────────────────────
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String userMessage = extractField(body, "message");

        if (userMessage == null || userMessage.isBlank()) {
            sendResponse(exchange, 400, "{\"error\":\"No message provided\"}");
            return;
        }

        // ── Auth check ───────────────────────────────────────────────────────────
        try {
            String token = sessions.extractToken(exchange);
            if (sessions.validateSession(token) == -1) {
                sendResponse(exchange, 401, "{\"error\":\"Not logged in.\"}");
                return;
            }
        } catch (java.sql.SQLException e) {
            sendResponse(exchange, 500, "{\"error\":\"Session error\"}");
            return;
        }

        // ── Topic guard ──────────────────────────────────────────────────────────
        boolean isCurrencyRelated = CURRENCY_PATTERN.matcher(userMessage).find();
        boolean isOffTopic = OFF_TOPIC_PATTERN.matcher(userMessage).find();

        if (!isCurrencyRelated || isOffTopic) {
            String refusal = "I can only help with currency and exchange rate questions. "
                    + "Try: \"Convert 100 USD to EUR\" or \"What is the GBP to JPY rate?\"";
            sendResponse(exchange, 200, "{\"reply\":\"" + escapeJson(refusal) + "\"}");
            return;
        }

        // ── Try to extract currencies and fetch a live rate ──────────────────────
        try {
            String[] pair = detectCurrencyPair(userMessage);
            double amount = detectAmount(userMessage);
            String promptForGroq;
            String rateDataJson = null;

            if (pair != null) {
                String from = pair[0];
                String to = pair[1];

                CurrencyService.RateData rd = currencyService.convert(from, to, amount);

                promptForGroq = userMessage + "\n\n[Live rate: 1 " + rd.base()
                        + " = " + String.format("%.4f", rd.rate()) + " " + rd.target()
                        + ". " + String.format("%.2f", rd.amount()) + " " + rd.base()
                        + " = " + String.format("%.2f", rd.converted()) + " " + rd.target() + "]";

                rateDataJson = "{"
                        + "\"base\":\"" + escapeJson(rd.base()) + "\","
                        + "\"target\":\"" + escapeJson(rd.target()) + "\","
                        + "\"rate\":" + rd.rate() + ","
                        + "\"amount\":" + rd.amount() + ","
                        + "\"converted\":" + rd.converted()
                        + "}";

            } else {
                // No pair detected — let Groq answer from training knowledge
                promptForGroq = userMessage;
            }

            String reply = groqService.chat(promptForGroq);

            StringBuilder jsonResponse = new StringBuilder();
            jsonResponse.append("{\"reply\":\"").append(escapeJson(reply)).append("\"");
            if (rateDataJson != null)
                jsonResponse.append(",\"rateData\":").append(rateDataJson);
            jsonResponse.append("}");

            sendResponse(exchange, 200, jsonResponse.toString());

        } catch (IOException e) {
            String err = e.getMessage() != null ? e.getMessage() : "Unknown error";
            sendResponse(exchange, 500, "{\"error\":\"" + escapeJson(err) + "\"}");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            sendResponse(exchange, 500, "{\"error\":\"Request interrupted\"}");
        }
    }

    // ── Currency pair detection ──────────────────────────────────────────────────

    private String[] detectCurrencyPair(String message) {
        Matcher m = CONVERSION_PATTERN.matcher(message);
        if (m.find()) {
            // "100 USD to EUR"
            if (m.group(2) != null && m.group(3) != null) {
                String from = resolveCode(m.group(2));
                String to = resolveCode(m.group(3));
                if (from != null && to != null)
                    return new String[] { from, to };
            }
            // "USD to EUR" (no amount)
            if (m.group(4) != null && m.group(5) != null) {
                String from = resolveCode(m.group(4));
                String to = resolveCode(m.group(5));
                if (from != null && to != null)
                    return new String[] { from, to };
            }
        }
        return null;
    }

    private double detectAmount(String message) {
        Matcher m = Pattern.compile("([\\d,]+(?:\\.\\d+)?)").matcher(message);
        while (m.find()) {
            try {
                double val = Double.parseDouble(m.group(1).replace(",", ""));
                if (val > 0)
                    return val;
            } catch (NumberFormatException ignored) {
            }
        }
        return 1.0;
    }

    private String resolveCode(String raw) {
        if (raw == null)
            return null;
        String lower = raw.toLowerCase().trim();
        if (NAME_MAP.containsKey(lower))
            return NAME_MAP.get(lower);
        // Accept 3-letter codes directly; API will reject invalid ones
        if (raw.matches("[A-Za-z]{3}"))
            return raw.toUpperCase();
        return null;
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────────

    private void sendResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String extractField(String json, String key) {
        String search = "\"" + key + "\"";
        int i = json.indexOf(search);
        if (i == -1)
            return null;
        int start = json.indexOf('"', i + search.length() + 1) + 1;
        int end = start;
        while (end < json.length()) {
            if (json.charAt(end) == '"' && json.charAt(end - 1) != '\\')
                break;
            end++;
        }
        return json.substring(start, end).replace("\\\"", "\"").replace("\\n", "\n");
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
