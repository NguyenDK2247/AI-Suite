import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;

public class WeatherService {

    private static final String CURRENT_URL = "https://api.openweathermap.org/data/2.5/weather";
    private static final String FORECAST_URL = "https://api.openweathermap.org/data/2.5/forecast";

    private final String apiKey;
    private final HttpClient http;

    public WeatherService(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newHttpClient();
    }

    public record WeatherData(
            String city, double tempCelsius, int humidity,
            String description, double feelsLike, double windSpeed) {
    }

    public WeatherData fetch(String city) throws IOException, InterruptedException {
        String url = CURRENT_URL + "?q=" + city.replace(" ", "+")
                + "&appid=" + apiKey + "&units=metric";
        return parseCurrent(get(url));
    }

    public record ForecastSlot(
            String time, double tempCelsius, String description,
            int humidity, double windSpeed) {
    }

    public List<ForecastSlot> fetchForecast(String city, int hoursAhead)
            throws IOException, InterruptedException {
        int cnt = Math.min(Math.max(1, (int) Math.ceil(hoursAhead / 3.0)), 40);
        String url = FORECAST_URL + "?q=" + city.replace(" ", "+")
                + "&appid=" + apiKey + "&units=metric&cnt=" + cnt;
        return parseForecast(get(url), cnt);
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404)
            throw new IOException("City not found");
        if (res.statusCode() != 200)
            throw new IOException("Weather API error: " + res.statusCode());
        return res.body();
    }

    private WeatherData parseCurrent(String json) {
        return new WeatherData(
                extractString(json, "\"name\""),
                extractDouble(json, "\"temp\""),
                (int) extractDouble(json, "\"humidity\""),
                extractString(json, "\"description\""),
                extractDouble(json, "\"feels_like\""),
                extractDouble(json, "\"speed\""));
    }

    private List<ForecastSlot> parseForecast(String json, int maxSlots) {
        List<ForecastSlot> slots = new ArrayList<>();

        // Locate the "[" that opens the "list" array
        int listIdx = json.indexOf("\"list\"");
        if (listIdx == -1)
            return slots;
        int arrayStart = json.indexOf('[', listIdx);
        if (arrayStart == -1)
            return slots;

        // Walk forward: each slot is a complete top-level {...} in the array
        int cursor = arrayStart + 1;
        while (slots.size() < maxSlots) {
            int slotStart = json.indexOf('{', cursor);
            if (slotStart == -1)
                break;
            int slotEnd = findClosingBrace(json, slotStart);
            if (slotEnd == -1)
                break;

            String s = json.substring(slotStart, slotEnd + 1);
            slots.add(new ForecastSlot(
                    extractString(s, "\"dt_txt\""),
                    extractDouble(s, "\"temp\""),
                    extractString(s, "\"description\""),
                    (int) extractDouble(s, "\"humidity\""),
                    extractDouble(s, "\"speed\"")));
            cursor = slotEnd + 1;
        }
        return slots;
    }

    private int findClosingBrace(String s, int open) {
        int depth = 0;
        boolean inStr = false;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\'))
                inStr = !inStr;
            if (!inStr) {
                if (c == '{')
                    depth++;
                else if (c == '}') {
                    if (--depth == 0)
                        return i;
                }
            }
        }
        return -1;
    }

    private String extractString(String json, String key) {
        int i = json.indexOf(key);
        if (i == -1)
            return "unknown";
        int start = json.indexOf('"', i + key.length() + 1) + 1;
        return json.substring(start, json.indexOf('"', start));
    }

    private double extractDouble(String json, String key) {
        int i = json.indexOf(key);
        if (i == -1)
            return 0.0;
        int start = i + key.length() + 1;
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == ':'))
            start++;
        int end = start;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.'))
            end++;
        try {
            return Double.parseDouble(json.substring(start, end));
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
