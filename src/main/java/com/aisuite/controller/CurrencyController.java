package com.aisuite.controller;

import com.aisuite.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.regex.*;

import org.springframework.beans.factory.annotation.Qualifier;

@RestController
public class CurrencyController {

    private static final String COLLECTION = "currency_knowledge";

    private static final Pattern CURRENCY_PATTERN = Pattern.compile(
            "(?i)\\b(convert|converting|exchange|exchanging|worth|rate|rates|price|cost|" +
                    "how much|what is|what's|calculate|calculating|currency|currencies|forex|fx|" +
                    "dollar|dollars|usd|euro|euros|eur|pound|pounds|gbp|yen|jpy|yuan|cny|rmb|" +
                    "franc|chf|rupee|inr|won|krw|ruble|rub|real|brl|peso|mxn|aud|cad|sgd|hkd|" +
                    "nzd|vnd|dong|bitcoin|btc|ethereum|eth|crypto)\\b");

    private static final Pattern OFF_TOPIC_PATTERN = Pattern.compile(
            "(?i)\\b(weather|forecast|temperature|rain|wind|humidity|" +
                    "recipe|cook|movie|music|sport|game|code|program|health|medical)\\b");

    private static final Pattern CONVERSION_PATTERN = Pattern.compile(
            "(?i)([\\d,]+(?:\\.\\d+)?)\\s+([A-Za-z]{2,10})\\s+(?:to|in|into)\\s+" +
                    "([A-Za-z]+(?:\\s+[A-Za-z]+)?)" +
                    "|([A-Za-z]+(?:\\s+[A-Za-z]+)?)\\s+(?:to|in|into)\\s+([A-Za-z]+(?:\\s+[A-Za-z]+)?)");

    private static final Map<String, String> NAME_MAP = Map.ofEntries(
            Map.entry("dollar", "USD"), Map.entry("dollars", "USD"),
            Map.entry("euro", "EUR"), Map.entry("euros", "EUR"),
            Map.entry("pound", "GBP"), Map.entry("pounds", "GBP"),
            Map.entry("sterling", "GBP"), Map.entry("yen", "JPY"),
            Map.entry("yuan", "CNY"), Map.entry("renminbi", "CNY"),
            Map.entry("rmb", "CNY"), Map.entry("franc", "CHF"),
            Map.entry("francs", "CHF"), Map.entry("rupee", "INR"),
            Map.entry("rupees", "INR"), Map.entry("won", "KRW"),
            Map.entry("ruble", "RUB"), Map.entry("rubles", "RUB"),
            Map.entry("real", "BRL"), Map.entry("reais", "BRL"),
            Map.entry("peso", "MXN"), Map.entry("pesos", "MXN"),
            Map.entry("dong", "VND"), Map.entry("vietnamese dong", "VND"),
            Map.entry("bitcoin", "BTC"), Map.entry("btc", "BTC"),
            Map.entry("ethereum", "ETH"), Map.entry("eth", "ETH"),
            Map.entry("us dollar", "USD"), Map.entry("us dollars", "USD"),
            Map.entry("canadian dollar", "CAD"), Map.entry("canadian dollars", "CAD"),
            Map.entry("australian dollar", "AUD"), Map.entry("australian dollars", "AUD"),
            Map.entry("hong kong dollar", "HKD"), Map.entry("singapore dollar", "SGD"),
            Map.entry("swiss franc", "CHF"), Map.entry("new zealand dollar", "NZD"),
            Map.entry("south korean won", "KRW"), Map.entry("chinese yuan", "CNY"),
            Map.entry("japanese yen", "JPY"), Map.entry("british pound", "GBP"));

    private final CurrencyService currencyService;
    private final GroqService groqService;
    private final RagService ragService;

    public CurrencyController(CurrencyService currencyService,
            @Qualifier("currencyGroq") GroqService groqService,
            RagService ragService) {
        this.currencyService = currencyService;
        this.groqService = groqService;
        this.ragService = ragService;
    }

    @PostMapping("/currency-chat")
    public ResponseEntity<Map<String, Object>> currencyChat(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        String userMessage = body.get("message");
        if (userMessage == null || userMessage.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "No message provided."));

        // ── RAG retrieval first — if knowledge base has relevant context,
        // allow the question through even if it doesn't match currency keywords
        String ragContext = "";
        try {
            ragContext = ragService.retrieve(userMessage, COLLECTION);
        } catch (Exception e) {
            System.err.println("RAG retrieval skipped: " + e.getMessage());
        }

        // Topic guard — bypass if RAG found relevant context
        boolean isCurrencyRelated = CURRENCY_PATTERN.matcher(userMessage).find();
        boolean isOffTopic = OFF_TOPIC_PATTERN.matcher(userMessage).find();
        boolean hasRagContext = !ragContext.isEmpty();

        if ((!isCurrencyRelated || isOffTopic) && !hasRagContext) {
            String refusal = "I can only help with currency and exchange rate questions. "
                    + "Try: \"Convert 100 USD to EUR\" or \"What is the GBP to JPY rate?\"";
            return ResponseEntity.ok(Map.of("reply", refusal));
        }

        try {
            String[] pair = detectCurrencyPair(userMessage);
            double amount = detectAmount(userMessage);
            String promptForGroq;
            Object rateData = null;

            if (pair != null) {
                CurrencyService.RateData rd = currencyService.convert(pair[0], pair[1], amount);
                promptForGroq = userMessage + "\n\n[Live rate: 1 " + rd.base()
                        + " = " + String.format("%.4f", rd.rate()) + " " + rd.target()
                        + ". " + String.format("%.2f", rd.amount()) + " " + rd.base()
                        + " = " + String.format("%.2f", rd.converted()) + " " + rd.target() + "]";
                rateData = Map.of(
                        "base", rd.base(),
                        "target", rd.target(),
                        "rate", rd.rate(),
                        "amount", rd.amount(),
                        "converted", rd.converted());
            } else {
                promptForGroq = userMessage;
            }

            // Prepend RAG context if found
            if (hasRagContext)
                promptForGroq = ragContext + "\n\n" + promptForGroq;

            String reply = groqService.chat(promptForGroq);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("reply", reply);
            if (rateData != null)
                response.put("rateData", rateData);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage() != null
                            ? e.getMessage()
                            : "Unknown error"));
        }
    }

    // ── Detection helpers ─────────────────────────────────────────────────────
    private String[] detectCurrencyPair(String message) {
        Matcher m = CONVERSION_PATTERN.matcher(message);
        if (m.find()) {
            if (m.group(2) != null && m.group(3) != null) {
                String from = resolveCode(m.group(2));
                String to = resolveCode(m.group(3));
                if (from != null && to != null)
                    return new String[] { from, to };
            }
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
        if (raw.matches("[A-Za-z]{3}"))
            return raw.toUpperCase();
        return null;
    }
}
