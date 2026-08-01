package com.aisuite.controller;

import com.aisuite.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.*;

import org.springframework.beans.factory.annotation.Qualifier;

@RestController
public class ChatController {

    private static final String COLLECTION = "weather_knowledge";

    private static final String[] TRAILING_NOISE = {
            "right now", "today", "currently", "at the moment",
            "now", "tonight", "this week", "forecast", "like", "for"
    };

    private static final Pattern CITY_PATTERN = Pattern.compile(
            "(?:\\bweather\\s+)?\\b(?:in|for|at)\\b\\s+([A-Za-z][A-Za-z\\s]{1,30}?)\\s*" +
                    "(?:\\?|$|\\bright now\\b|\\btoday\\b|\\bcurrently\\b|\\bnow\\b|\\btonight\\b|" +
                    "\\bat the moment\\b|\\bforecast\\b|\\blike\\b\\??|\\bfor\\b\\s+(?:the\\s+)?(?:next|today|tonight))",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern HOURS_PATTERN = Pattern.compile(
            "(?:in\\s+the\\s+)?next\\s+(\\d+)\\s+hours?", Pattern.CASE_INSENSITIVE);

    private final WeatherService weatherService;
    private final GroqService groqService;
    private final RagService ragService;

    public ChatController(WeatherService weatherService,
            @Qualifier("weatherGroq") GroqService groqService,
            RagService ragService) {
        this.weatherService = weatherService;
        this.groqService = groqService;
        this.ragService = ragService;
    }

    @PostMapping("/chat")
    public ResponseEntity<Map<String, Object>> chat(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String userMessage = body.get("message");
        if (userMessage == null || userMessage.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No message provided."));

        try {
            // ── RAG retrieval first ───────────────────────────────────────────
            String ragContext = "";
            try {
                ragContext = ragService.retrieve(userMessage, COLLECTION);
            } catch (Exception e) {
                System.err.println("RAG retrieval skipped: " + e.getMessage());
            }

            String city = detectCity(userMessage);
            int hoursAhead = detectHours(userMessage);
            String promptForGroq;
            Object weatherData = null;

            if (city != null && hoursAhead > 0) {
                List<WeatherService.ForecastSlot> slots = weatherService.fetchForecast(city, hoursAhead);
                promptForGroq = buildForecastPrompt(userMessage, city, hoursAhead, slots);

                List<Map<String, Object>> forecastList = new ArrayList<>();
                for (WeatherService.ForecastSlot s : slots) {
                    forecastList.add(Map.of(
                            "time", s.time(),
                            "tempCelsius", s.tempCelsius(),
                            "description", s.description(),
                            "humidity", s.humidity(),
                            "windSpeed", s.windSpeed()));
                }
                weatherData = Map.of("city", city, "forecast", forecastList);

            } else if (city != null) {
                WeatherService.WeatherData w = weatherService.fetch(city);
                promptForGroq = userMessage + "\n\n[Live weather data for "
                        + w.city() + ": " + Math.round(w.tempCelsius()) + "°C, "
                        + "feels like " + Math.round(w.feelsLike()) + "°C, "
                        + w.description() + ", humidity " + w.humidity() + "%, "
                        + "wind " + String.format("%.1f", w.windSpeed()) + " m/s]";
                weatherData = Map.of(
                        "city", w.city(),
                        "tempCelsius", w.tempCelsius(),
                        "feelsLike", w.feelsLike(),
                        "description", w.description(),
                        "humidity", w.humidity(),
                        "windSpeed", w.windSpeed());
            } else {
                promptForGroq = userMessage;
            }

            // Prepend RAG context if found
            if (!ragContext.isEmpty())
                promptForGroq = ragContext + "\n\n" + promptForGroq;

            String reply = groqService.chat(promptForGroq);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("reply", reply);
            if (weatherData != null)
                response.put("weatherData", weatherData);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() != null
                            ? e.getMessage()
                            : "Unknown error"));
        }
    }

    // ── Detection helpers ─────────────────────────────────────────────────────
    private String detectCity(String message) {
        Matcher m = CITY_PATTERN.matcher(message);
        if (m.find()) {
            String city = m.group(1).trim();
            for (String noise : TRAILING_NOISE)
                if (city.toLowerCase().endsWith(noise))
                    city = city.substring(0, city.length() - noise.length()).trim();
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
        for (WeatherService.ForecastSlot s : slots) {
            sb.append("  ").append(s.time())
                    .append(" — ").append(Math.round(s.tempCelsius())).append("°C, ")
                    .append(s.description()).append(", ")
                    .append("humidity ").append(s.humidity()).append("%, ")
                    .append("wind ").append(String.format("%.1f", s.windSpeed())).append(" m/s\n");
        }
        return sb.append("]").toString();
    }
}
