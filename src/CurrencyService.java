import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class CurrencyService {

    private static final String BASE_URL = "https://api.exchangerate-api.com/v4/latest/";

    private final HttpClient http;

    public CurrencyService() {
        this.http = HttpClient.newHttpClient();
    }

    // ── Result record ────────────────────────────────────────────────────────────
    public record RateData(
            String base, // e.g. "USD"
            String target, // e.g. "EUR"
            double rate, // e.g. 0.9234
            double amount, // original amount the user asked about
            double converted // amount * rate
    ) {
    }

    // ── Public API ───────────────────────────────────────────────────────────────

    /**
     * Fetches the live exchange rate from {@code from} to {@code to}
     * and returns a RateData with the converted amount.
     *
     * @param from   ISO 4217 code, e.g. "USD"
     * @param to     ISO 4217 code, e.g. "EUR"
     * @param amount the amount to convert
     */
    public RateData convert(String from, String to, double amount)
            throws IOException, InterruptedException {
        String fromCode = from.toUpperCase().trim();
        String toCode = to.toUpperCase().trim();

        String json = get(BASE_URL + fromCode);
        double rate = extractRate(json, toCode);
        return new RateData(fromCode, toCode, rate, amount, rate * amount);
    }

    /**
     * Returns just the exchange rate between two currencies (amount = 1).
     */
    public RateData rate(String from, String to)
            throws IOException, InterruptedException {
        return convert(from, to, 1.0);
    }

    // ── Private helpers ──────────────────────────────────────────────────────────

    private String get(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();
        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 404)
            throw new IOException("Currency code not recognised by the API.");
        if (res.statusCode() != 200)
            throw new IOException("Currency API error: HTTP " + res.statusCode());
        return res.body();
    }

    /**
     * Extracts the numeric value for {@code currencyCode} from the "rates" block.
     * The JSON looks like: ..., "rates":{"AED":3.67,"AFN":71.02,...,"USD":1.0,...}
     */
    private double extractRate(String json, String currencyCode) throws IOException {
        // Find the "rates" object first, so we don't accidentally match
        // the base currency key outside that block.
        int ratesIdx = json.indexOf("\"rates\"");
        if (ratesIdx == -1)
            throw new IOException("Unexpected API response: no 'rates' field.");

        String ratesBlock = json.substring(ratesIdx);
        String key = "\"" + currencyCode + "\":";
        int i = ratesBlock.indexOf(key);
        if (i == -1)
            throw new IOException("Currency not found: " + currencyCode
                    + ". Check the currency code and try again.");

        int start = i + key.length();
        // Skip any accidental whitespace
        while (start < ratesBlock.length() && ratesBlock.charAt(start) == ' ')
            start++;

        int end = start;
        while (end < ratesBlock.length()
                && (Character.isDigit(ratesBlock.charAt(end)) || ratesBlock.charAt(end) == '.'))
            end++;

        try {
            return Double.parseDouble(ratesBlock.substring(start, end));
        } catch (NumberFormatException e) {
            throw new IOException("Could not parse rate for " + currencyCode);
        }
    }
}
