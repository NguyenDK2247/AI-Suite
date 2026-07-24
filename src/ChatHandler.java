import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatHandler implements HttpHandler {

    private final WeatherService weatherService;
    private final GroqService groqService;
    private final SessionManager sessions;
    private final RagService rag;

    private static final String COLLECTION = "weather_knowledge";

    private static final String[] TRAILING_NOISE = {
            "right now", "today", "currently", "at the moment",
            "now", "tonight", "this week", "forecast", "like", "for"
    };

    private static final Pattern CITY_PATTERN = Pattern.compile(
            "(?:\\bweather\\s+)?\\b(?:in|for|at)\\b\\s+([A-Za-z][A-Za-z\\s]{1,30}?)\\s*" +
                    "(?:\\?|$|\\bright now\\b|\\btoday\\b|\\bcurrently\\b|\\bnow\\b|\\btonight\\b|\\bat the moment\\b|\\bforecast\\b|\\blike\\b\\??|\\bfor\\b\\s+(?:the\\s+)?(?:next|today|tonight))",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HOURS_PATTERN = Pattern.compile(
            "(?:in\\s+the\\s+)?next\\s+(\\d+)\\s+hours?",
            Pattern.CASE_INSENSITIVE);

    public ChatHandler(WeatherService weatherService, GroqService groqService,
            SessionManager sessions, RagService rag) {
        this.weatherService = weatherService;
        this.groqService = groqService;
        this.sessions = sessions;
        this.rag = rag;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Content-Type", "application/json");

        if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        // Auth check
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

        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String userMessage = extractField(body, "message");

        if (userMessage == null || userMessage.isBlank()) {
            sendResponse(exchange, 400, "{\"error\":\"No message provided\"}");
            return;
        }

        try {
            String city = detectCity(userMessage);
            int hoursAhead = detectHours(userMessage);
            String weatherDataJson = null;
            String promptForGroq;

            if (city != null && hoursAhead > 0) {
                List<WeatherService.ForecastSlot> slots = weatherService.fetchForecast(city, hoursAhead);
                promptForGroq = buildForecastPrompt(userMessage, city, hoursAhead, slots);

                StringBuilder sb = new StringBuilder();
                sb.append("{\"city\":\"").append(escapeJson(city)).append("\",\"forecast\":[");
                for (int i = 0; i < slots.size(); i++) {
                    WeatherService.ForecastSlot s = slots.get(i);
                    sb.append("{")
                            .append("\"time\":\"").append(escapeJson(s.time())).append("\",")
                            .append("\"tempCelsius\":").append(s.tempCelsius()).append(",")
                            .append("\"description\":\"").append(escapeJson(s.description())).append("\",")
                            .append("\"humidity\":").append(s.humidity()).append(",")
                            .append("\"windSpeed\":").append(s.windSpeed())
                            .append("}");
                    if (i < slots.size() - 1)
                        sb.append(",");
                }
                sb.append("]}");
                weatherDataJson = sb.toString();

            } else if (city != null) {
                WeatherService.WeatherData w = weatherService.fetch(city);
                promptForGroq = userMessage + "\n\n[Live weather data for "
                        + w.city() + ": "
                        + Math.round(w.tempCelsius()) + "°C, "
                        + "feels like " + Math.round(w.feelsLike()) + "°C, "
                        + w.description() + ", "
                        + "humidity " + w.humidity() + "%, "
                        + "wind " + String.format("%.1f", w.windSpeed()) + " m/s]";

                weatherDataJson = "{" +
                        "\"city\":\"" + escapeJson(w.city()) + "\"," +
                        "\"tempCelsius\":" + w.tempCelsius() + "," +
                        "\"feelsLike\":" + w.feelsLike() + "," +
                        "\"description\":\"" + escapeJson(w.description()) + "\"," +
                        "\"humidity\":" + w.humidity() + "," +
                        "\"windSpeed\":" + w.windSpeed() +
                        "}";

            } else {
                promptForGroq = userMessage;
            }

            // ── RAG: retrieve relevant background knowledge ──────────────────
            try {
                String ragContext = rag.retrieve(userMessage, COLLECTION);
                if (!ragContext.isEmpty())
                    promptForGroq = ragContext + "\n\n" + promptForGroq;
            } catch (Exception ragEx) {
                System.err.println("RAG retrieval skipped: " + ragEx.getMessage());
            }

            String reply = groqService.chat(promptForGroq);

            StringBuilder jsonResponse = new StringBuilder();
            jsonResponse.append("{\"reply\":\"").append(escapeJson(reply)).append("\"");
            if (weatherDataJson != null) {
                jsonResponse.append(",\"weatherData\":").append(weatherDataJson);
            }
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

    private String detectCity(String message) {
        Matcher m = CITY_PATTERN.matcher(message);
        if (m.find()) {
            String city = m.group(1).trim();
            for (String noise : TRAILING_NOISE) {
                if (city.toLowerCase().endsWith(noise))
                    city = city.substring(0, city.length() - noise.length()).trim();
            }
            return city.isBlank() ? null : city;
        }
        return null;
    }

    private int detectHours(String message) {
        Matcher m = HOURS_PATTERN.matcher(message);
        if (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private String buildForecastPrompt(String userMessage, String city,
            int hoursAhead,
            List<WeatherService.ForecastSlot> slots) {
        StringBuilder sb = new StringBuilder(userMessage);
        sb.append("\n\n[3-hour forecast for ").append(city)
                .append(" over the next ").append(hoursAhead).append(" hours:\n");
        for (WeatherService.ForecastSlot slot : slots) {
            sb.append("  ").append(slot.time())
                    .append(" — ").append(Math.round(slot.tempCelsius())).append("°C, ")
                    .append(slot.description()).append(", ")
                    .append("humidity ").append(slot.humidity()).append("%, ")
                    .append("wind ").append(String.format("%.1f", slot.windSpeed())).append(" m/s\n");
        }
        sb.append("]");
        return sb.toString();
    }

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
