package com.aisuite.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;

@Service
public class CurrencyService {

    private static final String BASE_URL = "https://api.exchangerate-api.com/v4/latest/";
    private final HttpClient http = HttpClient.newHttpClient();

    public record RateData(String base, String target,
            double rate, double amount, double converted) {
    }

    public RateData convert(String from, String to, double amount)
            throws IOException, InterruptedException {
        String fromCode = from.toUpperCase().trim();
        String toCode = to.toUpperCase().trim();
        String json = get(BASE_URL + fromCode);
        double rate = extractRate(json, toCode);
        return new RateData(fromCode, toCode, rate, amount, rate * amount);
    }

    public RateData rate(String from, String to)
            throws IOException, InterruptedException {
        return convert(from, to, 1.0);
    }

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404)
            throw new IOException("Currency code not recognised.");
        if (res.statusCode() != 200)
            throw new IOException("Currency API error: HTTP " + res.statusCode());
        return res.body();
    }

    private double extractRate(String json, String code) throws IOException {
        int ratesIdx = json.indexOf("\"rates\"");
        if (ratesIdx == -1)
            throw new IOException("Unexpected API response: no 'rates' field.");
        String ratesBlock = json.substring(ratesIdx);
        String key = "\"" + code + "\":";
        int i = ratesBlock.indexOf(key);
        if (i == -1)
            throw new IOException("Currency not found: " + code);
        int start = i + key.length();
        while (start < ratesBlock.length() && ratesBlock.charAt(start) == ' ')
            start++;
        int end = start;
        while (end < ratesBlock.length() &&
                (Character.isDigit(ratesBlock.charAt(end)) || ratesBlock.charAt(end) == '.'))
            end++;
        try {
            return Double.parseDouble(ratesBlock.substring(start, end));
        } catch (NumberFormatException e) {
            throw new IOException("Could not parse rate for " + code);
        }
    }
}
