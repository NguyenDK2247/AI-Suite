package com.aisuite.service;

import org.springframework.stereotype.Service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Scrapes a URL and splits the text into overlapping chunks for embedding.
 */
@Service
public class WebScraper {

    private static final int CHUNK_SIZE = 200; // words per chunk — smaller = more precise retrieval
    private static final int CHUNK_OVERLAP = 30; // words of overlap to preserve context across chunks
    private static final int TIMEOUT_MS = 10_000;

    public record ScrapeResult(String url, String title, List<String> chunks) {
    }

    // ── Scrape a single URL ───────────────────────────────────────────────────
    // Known JS-rendered sites that won't work with Jsoup
    private static final java.util.List<String> JS_SITES = java.util.List.of(
            "accuweather.com", "weather.com", "wunderground.com",
            "weather.gov", "bbc.com/weather", "metoffice.gov.uk",
            "bloomberg.com", "reuters.com", "wsj.com", "ft.com");

    public ScrapeResult scrape(String url) throws IOException {
        // ── Wikipedia: use their REST API ────────────────────────────────────
        if (url.contains("wikipedia.org/wiki/")) {
            return scrapeWikipedia(url);
        }

        // ── Known JS-only sites: fail fast with a helpful message ─────────────
        String lowerUrl = url.toLowerCase();
        for (String jsSite : JS_SITES) {
            if (lowerUrl.contains(jsSite)) {
                throw new IOException(
                        "'" + jsSite + "' requires JavaScript and cannot be scraped. "
                                + "For weather knowledge, try Wikipedia climate pages "
                                + "(e.g. https://en.wikipedia.org/wiki/Climate_of_Vietnam) "
                                + "or https://www.timeanddate.com/weather/ pages instead.");
            }
        }

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                        + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .referrer("https://www.google.com")
                .timeout(TIMEOUT_MS)
                .get();

        String title = doc.title();

        // ── Detect JS-rendered pages by checking if body is nearly empty ──────
        String rawBodyText = doc.body().text().trim();
        if (rawBodyText.length() < 200) {
            throw new IOException(
                    "The page at '" + url + "' appears to be JavaScript-rendered "
                            + "(body text is nearly empty). "
                            + "Try a Wikipedia page or timeanddate.com instead.");
        }

        // Remove noise elements
        doc.select("script, style, nav, footer, header, aside, "
                + "form, iframe, noscript, [class*=cookie], [class*=banner], "
                + "[class*=ad], [id*=ad], .reflist, .references, "
                + ".mw-editsection, .navbox, .infobox, .sidebar").remove();

        StringBuilder sb = new StringBuilder();

        String[] contentSelectors = {
                "#mw-content-text", "article", "main", "[role=main]",
                ".content", "#content", ".post-content", ".article-body"
        };

        Element container = null;
        for (String sel : contentSelectors) {
            container = doc.selectFirst(sel);
            if (container != null)
                break;
        }
        if (container == null)
            container = doc.body();

        Elements blocks = container.select("p, h1, h2, h3, h4, li, td, th, blockquote, pre");
        for (Element el : blocks) {
            String text = el.text().trim();
            if (text.length() > 40)
                sb.append(text).append("\n\n");
        }

        if (sb.length() < 200) {
            String bodyText = container.text();
            if (bodyText.length() > 200)
                sb = new StringBuilder(bodyText);
        }

        String fullText = sb.toString().trim();
        if (fullText.isEmpty())
            throw new IOException(
                    "No text extracted from '" + url + "'. "
                            + "The site may block scrapers or require JavaScript. "
                            + "Try a Wikipedia page or timeanddate.com instead.");

        List<String> chunks = chunk(fullText);
        System.out.println("Scraped '" + url + "' -> " + chunks.size() + " chunks");
        return new ScrapeResult(url, title, chunks);
    }

    // ── Wikipedia REST API scraper ────────────────────────────────────────────
    @SuppressWarnings("unused")
    private ScrapeResult scrapeWikipedia(String url) throws IOException {
        // Extract page title from URL: /wiki/Vietnamese_dong -> Vietnamese_dong
        String pageTitle = url.replaceAll(".*wikipedia\\.org/wiki/", "")
                .replaceAll("#.*", "")
                .trim();

        String lang = "en";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(\\w+)\\.wikipedia\\.org").matcher(url);
        if (m.find())
            lang = m.group(1);

        // Use the summary endpoint for a clean intro paragraph
        String summaryUrl = "https://" + lang + ".wikipedia.org/api/rest_v1/page/summary/"
                + pageTitle;

        // Also fetch the full plain-text content
        String contentUrl = "https://" + lang + ".wikipedia.org/w/api.php"
                + "?action=query&prop=extracts&explaintext=true&exlimit=1"
                + "&titles=" + pageTitle + "&format=json";

        java.net.http.HttpClient http = java.net.http.HttpClient.newHttpClient();
        StringBuilder fullText = new StringBuilder();
        String title = pageTitle.replace("_", " ");

        // Fetch full plain text via MediaWiki API
        try {
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(contentUrl))
                    .header("User-Agent", "RAGBot/1.0 (educational project)")
                    .GET().build();
            java.net.http.HttpResponse<String> res = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (res.statusCode() == 200) {
                String json = res.body();
                // Extract the "extract" field from MediaWiki JSON
                int extractIdx = json.indexOf("\"extract\":");
                if (extractIdx != -1) {
                    int start = json.indexOf('"', extractIdx + 10) + 1;
                    // Walk to find the unescaped closing quote
                    StringBuilder extract = new StringBuilder();
                    for (int i = start; i < json.length(); i++) {
                        char c = json.charAt(i);
                        if (c == '\\' && i + 1 < json.length()) {
                            char next = json.charAt(i + 1);
                            if (next == '"') {
                                extract.append('"');
                                i++;
                            } else if (next == 'n') {
                                extract.append('\n');
                                i++;
                            } else if (next == '\\') {
                                extract.append('\\');
                                i++;
                            } else
                                extract.append(c);
                        } else if (c == '"') {
                            break;
                        } else {
                            extract.append(c);
                        }
                    }
                    fullText.append(extract);

                    // Also grab title from JSON
                    int titleIdx = json.indexOf("\"title\":");
                    if (titleIdx != -1) {
                        int ts = json.indexOf('"', titleIdx + 8) + 1;
                        int te = json.indexOf('"', ts);
                        if (te > ts)
                            title = json.substring(ts, te);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Wikipedia API request interrupted");
        }

        if (fullText.length() < 100)
            throw new IOException("Wikipedia returned no content for: " + pageTitle
                    + ". Check the page title in the URL.");

        List<String> chunks = chunk(fullText.toString());
        System.out.println("Wikipedia API: '" + title + "' -> " + chunks.size() + " chunks");
        return new ScrapeResult(url, title, chunks);
    }

    // ── Scrape a URL and all hrefs it links to (1 level deep) ────────────────
    public List<ScrapeResult> scrapeWithDepth(String url, int depth) throws IOException {
        List<ScrapeResult> results = new ArrayList<>();
        ScrapeResult root = scrape(url);
        results.add(root);

        if (depth <= 0)
            return results;

        // Collect same-domain links
        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (compatible; RAGBot/1.0)")
                .timeout(TIMEOUT_MS)
                .get();

        String baseDomain = extractDomain(url);
        Elements links = doc.select("a[href]");

        int followed = 0;
        for (Element link : links) {
            if (followed >= 10)
                break; // cap at 10 sub-pages per root
            String href = link.absUrl("href");
            if (href.isEmpty() || !extractDomain(href).equals(baseDomain))
                continue;
            if (href.contains("#") || href.equals(url))
                continue;
            try {
                results.add(scrape(href));
                followed++;
                Thread.sleep(300); // polite crawl delay
            } catch (IOException | InterruptedException ignored) {
            }
        }
        return results;
    }

    // ── Split text into overlapping word-based chunks ─────────────────────────
    private List<String> chunk(String text) {
        String[] words = text.split("\\s+");
        List<String> chunks = new ArrayList<>();

        int i = 0;
        while (i < words.length) {
            int end = Math.min(i + CHUNK_SIZE, words.length);
            StringBuilder chunk = new StringBuilder();
            for (int j = i; j < end; j++) {
                if (j > i)
                    chunk.append(" ");
                chunk.append(words[j]);
            }
            String c = chunk.toString().trim();
            if (!c.isEmpty())
                chunks.add(c);
            i += CHUNK_SIZE - CHUNK_OVERLAP; // slide with overlap
            if (i >= words.length)
                break;
        }
        return chunks;
    }

    private String extractDomain(String url) {
        try {
            java.net.URI uri = java.net.URI.create(url);
            return uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        } catch (Exception e) {
            return "";
        }
    }
}
