import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Talks to ChromaDB v2 REST API running on localhost:8000.
 *
 * ChromaDB v2 endpoints used:
 * POST /api/v2/tenants/{tenant}/databases/{db}/collections
 * GET /api/v2/tenants/{tenant}/databases/{db}/collections/{name}
 * POST /api/v2/tenants/{tenant}/databases/{db}/collections/{id}/upsert
 * POST /api/v2/tenants/{tenant}/databases/{db}/collections/{id}/query
 */
public class VectorStore {

    private static final String BASE = "http://[::1]:8000";
    private static final String TENANT = "default_tenant";
    private static final String DATABASE = "default_database";
    private static final String COLLECTIONS = BASE + "/api/v2/tenants/" + TENANT
            + "/databases/" + DATABASE + "/collections";

    private final HttpClient http;

    // Cache: collectionName → collectionId (UUID assigned by Chroma)
    private final Map<String, String> collectionIds = new HashMap<>();

    public VectorStore() {
        this.http = HttpClient.newHttpClient();
    }

    // ── Ensure a collection exists and return its id ──────────────────────────
    public String getOrCreateCollection(String name) throws IOException, InterruptedException {
        if (collectionIds.containsKey(name))
            return collectionIds.get(name);

        // Try to GET the collection first
        HttpRequest getReq = HttpRequest.newBuilder()
                .uri(URI.create(COLLECTIONS + "/" + name))
                .GET().build();
        HttpResponse<String> getRes = http.send(getReq, HttpResponse.BodyHandlers.ofString());

        if (getRes.statusCode() == 200) {
            System.out.println(
                    "Found existing collection: " + getRes.body().substring(0, Math.min(200, getRes.body().length())));
            String id = extractString(getRes.body(), "id");
            if (id == null || id.isEmpty())
                throw new IOException("Collection '" + name + "' found but could not parse id from: " + getRes.body());
            collectionIds.put(name, id);
            return id;
        }

        // Collection doesn't exist — create it
        String body = "{\"name\":\"" + name + "\"}";
        HttpRequest createReq = HttpRequest.newBuilder()
                .uri(URI.create(COLLECTIONS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> createRes = http.send(createReq, HttpResponse.BodyHandlers.ofString());

        if (createRes.statusCode() != 200 && createRes.statusCode() != 201)
            throw new IOException("Failed to create collection '" + name
                    + "': HTTP " + createRes.statusCode() + " — " + createRes.body());

        String responseBody = createRes.body();
        String id = extractString(responseBody, "id");
        collectionIds.put(name, id);
        System.out.println("Created ChromaDB collection: " + name + " (id=" + id + ")");
        return id;
    }

    // ── Upsert documents into a collection ───────────────────────────────────
    public void upsert(String collectionName,
            List<String> ids,
            List<List<Double>> embeddings,
            List<String> documents,
            List<Map<String, String>> metadatas)
            throws IOException, InterruptedException {

        String collId = getOrCreateCollection(collectionName);

        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"ids\":").append(toJsonStringArray(ids)).append(",");
        sb.append("\"embeddings\":").append(toJsonDoubleMatrix(embeddings)).append(",");
        sb.append("\"documents\":").append(toJsonStringArray(documents)).append(",");
        sb.append("\"metadatas\":").append(toJsonMetadataArray(metadatas));
        sb.append("}");

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(COLLECTIONS + "/" + collId + "/upsert"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(sb.toString(), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200 && res.statusCode() != 201)
            throw new IOException("Upsert failed: HTTP " + res.statusCode() + " — " + res.body());
    }

    // ── Semantic query: find top-k most similar documents ─────────────────────
    public List<String> query(String collectionName,
            List<Double> queryEmbedding,
            int topK)
            throws IOException, InterruptedException {
        return queryWithIds(collectionName, queryEmbedding, topK).stream()
                .map(ScoredChunk::document)
                .toList();
    }

    // ── Semantic query returning id+document pairs for reranking ──────────────
    public List<ScoredChunk> queryWithIds(String collectionName,
            List<Double> queryEmbedding,
            int topK)
            throws IOException, InterruptedException {

        String collId = getOrCreateCollection(collectionName);

        String body = "{"
                + "\"query_embeddings\":[[" + toJsonDoubleArray(queryEmbedding) + "]],"
                + "\"n_results\":" + topK + ","
                + "\"include\":[\"documents\",\"distances\"]"
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(COLLECTIONS + "/" + collId + "/query"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200)
            throw new IOException("Query failed: " + res.statusCode() + " " + res.body());

        List<ScoredChunk> results = parseChunks(res.body());
        System.out.println("RAG semantic: retrieved " + results.size()
                + " chunks from '" + collectionName + "'");
        return results;
    }

    // ── Keyword query: filter by documents containing all query terms ──────────
    public List<ScoredChunk> queryByKeyword(String collectionName,
            List<String> keywords,
            int topK)
            throws IOException, InterruptedException {

        if (keywords.isEmpty())
            return List.of();
        String collId = getOrCreateCollection(collectionName);

        // Build $and filter: each keyword must appear in the document
        StringBuilder whereDoc = new StringBuilder();
        if (keywords.size() == 1) {
            whereDoc.append("{\"$contains\":\"").append(escJson(keywords.get(0))).append("\"}");
        } else {
            whereDoc.append("{\"$and\":[");
            for (int i = 0; i < keywords.size(); i++) {
                if (i > 0)
                    whereDoc.append(",");
                whereDoc.append("{\"$contains\":\"").append(escJson(keywords.get(i))).append("\"}");
            }
            whereDoc.append("]}");
        }

        String body = "{"
                + "\"where_document\":" + whereDoc + ","
                + "\"n_results\":" + topK + ","
                + "\"include\":[\"documents\"]"
                + "}";

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(COLLECTIONS + "/" + collId + "/get"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
        // 200 or empty result — keyword search may return nothing, that's fine
        if (res.statusCode() != 200)
            return List.of();

        List<String> docs = parseDocuments(res.body());
        System.out.println("RAG keyword: retrieved " + docs.size()
                + " chunks from '" + collectionName + "'");
        return docs.stream().map(d -> new ScoredChunk(d, 0.0)).toList();
    }

    // ── Result record ─────────────────────────────────────────────────────────
    public record ScoredChunk(String document, double distance) {
    }

    // ── Parse chunks with distances ───────────────────────────────────────────
    private List<ScoredChunk> parseChunks(String json) {
        List<String> docs = parseDocuments(json);
        List<Double> distances = parseDistances(json);
        List<ScoredChunk> chunks = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            double dist = i < distances.size() ? distances.get(i) : 1.0;
            chunks.add(new ScoredChunk(docs.get(i), dist));
        }
        return chunks;
    }

    // ── Parse distances array from query response ─────────────────────────────
    private List<Double> parseDistances(String json) {
        List<Double> results = new ArrayList<>();
        int idx = json.indexOf("\"distances\"");
        if (idx == -1)
            return results;
        int outer = json.indexOf("[", idx);
        int inner = json.indexOf("[", outer + 1);
        int end = json.indexOf("]", inner + 1);
        if (inner == -1 || end == -1)
            return results;
        String[] parts = json.substring(inner + 1, end).split(",");
        for (String p : parts) {
            try {
                results.add(Double.parseDouble(p.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        return results;
    }

    // ── Parse documents array from query/get response ─────────────────────────
    // Response shape: {"documents":[[doc1, doc2, ...]], ...}
    private List<String> parseDocuments(String json) {
        List<String> results = new ArrayList<>();
        int docsIdx = json.indexOf("\"documents\"");
        if (docsIdx == -1)
            return results;

        // Find the inner array [[...]]
        int outer = json.indexOf("[", docsIdx);
        int inner = json.indexOf("[", outer + 1);
        int innerEnd = json.indexOf("]", inner + 1);
        if (inner == -1 || innerEnd == -1)
            return results;

        String inner_str = json.substring(inner + 1, innerEnd).trim();
        if (inner_str.isEmpty())
            return results;

        // Split by "," but only at top level (not inside strings)
        List<String> tokens = splitJsonStrings(inner_str);
        for (String token : tokens) {
            String cleaned = token.trim();
            if (cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1);
                cleaned = cleaned.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
                results.add(cleaned);
            }
        }
        return results;
    }

    // Split a JSON array of strings respecting escaped chars
    private List<String> splitJsonStrings(String s) {
        List<String> parts = new ArrayList<>();
        boolean inStr = false;
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' && (i == 0 || s.charAt(i - 1) != '\\'))
                inStr = !inStr;
            if (c == ',' && !inStr) {
                parts.add(s.substring(start, i).trim());
                start = i + 1;
            }
        }
        if (start < s.length())
            parts.add(s.substring(start).trim());
        return parts;
    }

    // ── JSON serialisation helpers ────────────────────────────────────────────
    private String toJsonStringArray(List<String> list) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append("\"").append(escJson(list.get(i))).append("\"");
        }
        return sb.append("]").toString();
    }

    private String toJsonDoubleArray(List<Double> vec) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vec.size(); i++) {
            if (i > 0)
                sb.append(",");
            // Format as float32 precision (6 decimal places) — ChromaDB v2 requires this
            sb.append(String.format("%.6f", vec.get(i)));
        }
        return sb.toString();
    }

    private String toJsonDoubleMatrix(List<List<Double>> matrix) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < matrix.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append("[").append(toJsonDoubleArray(matrix.get(i))).append("]");
        }
        return sb.append("]").toString();
    }

    private String toJsonMetadataArray(List<Map<String, String>> metas) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < metas.size(); i++) {
            if (i > 0)
                sb.append(",");
            sb.append("{");
            boolean first = true;
            for (Map.Entry<String, String> e : metas.get(i).entrySet()) {
                if (!first)
                    sb.append(",");
                sb.append("\"").append(escJson(e.getKey())).append("\":")
                        .append("\"").append(escJson(e.getValue())).append("\"");
                first = false;
            }
            sb.append("}");
        }
        return sb.append("]").toString();
    }

    private String extractString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int i = json.indexOf(search);
        if (i == -1)
            return "";
        int start = i + search.length();
        int end = json.indexOf("\"", start);
        return end == -1 ? "" : json.substring(start, end);
    }

    private String escJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "");
    }
}
