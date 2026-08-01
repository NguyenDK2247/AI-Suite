package com.aisuite.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Orchestrates the full RAG pipeline with hybrid search and reranking:
 *
 * Ingest: scrape -> chunk (200 words, 30 overlap) -> embed -> store
 * Retrieve: embed query
 * -> semantic search (top SEMANTIC_K)
 * -> keyword search (top KEYWORD_K)
 * -> merge + deduplicate
 * -> rerank by cosine similarity
 * -> return top FINAL_K as context string
 */
@Service
public class RagService {

    private final WebScraper scraper;
    private final EmbeddingService embedder;
    private final VectorStore vectorStore;
    private final Reranker reranker;

    // ── Retrieval constants ───────────────────────────────────────────────────
    private static final int SEMANTIC_K = 6; // candidates from vector search
    private static final int KEYWORD_K = 4; // candidates from keyword search
    private static final int FINAL_K = 3; // chunks kept after reranking
    private static final int MAX_CONTEXT_LEN = 1500; // chars injected into Groq prompt

    // ── Ingest constants ──────────────────────────────────────────────────────
    private static final int BATCH_SIZE = 5;

    public RagService(WebScraper scraper, EmbeddingService embedder,
            VectorStore vectorStore, Reranker reranker) {
        this.scraper = scraper;
        this.embedder = embedder;
        this.vectorStore = vectorStore;
        this.reranker = reranker;
    }

    // ── Ingest ────────────────────────────────────────────────────────────────
    public IngestResult ingest(String url, String collectionName, int depth)
            throws IOException, InterruptedException {

        List<WebScraper.ScrapeResult> pages = depth > 0
                ? scraper.scrapeWithDepth(url, depth)
                : List.of(scraper.scrape(url));

        int totalChunks = 0;

        for (WebScraper.ScrapeResult page : pages) {
            List<String> ids = new ArrayList<>();
            List<List<Double>> embeddings = new ArrayList<>();
            List<String> documents = new ArrayList<>();
            List<Map<String, String>> metadatas = new ArrayList<>();

            List<String> chunks = page.chunks();

            for (int i = 0; i < chunks.size(); i += BATCH_SIZE) {
                int end = Math.min(i + BATCH_SIZE, chunks.size());

                for (int j = i; j < end; j++) {
                    String chunk = chunks.get(j);
                    try {
                        List<Double> vec = embedder.embed(chunk);
                        String id = collectionName + "_"
                                + Math.abs(page.url().hashCode()) + "_" + j;
                        ids.add(id);
                        embeddings.add(vec);
                        documents.add(chunk);
                        metadatas.add(Map.of(
                                "url", page.url(),
                                "title", page.title(),
                                "chunk", String.valueOf(j)));
                    } catch (Exception e) {
                        System.err.println("Embedding failed for chunk " + j
                                + ": " + e.getClass().getSimpleName()
                                + " — " + e.getMessage());
                    }
                }

                if (!ids.isEmpty()) {
                    try {
                        vectorStore.upsert(collectionName, ids, embeddings,
                                documents, metadatas);
                    } catch (Exception e) {
                        System.err.println("Upsert failed at chunk " + i
                                + ": " + e.getMessage());
                    }
                }
                ids.clear();
                embeddings.clear();
                documents.clear();
                metadatas.clear();
            }
            totalChunks += chunks.size();
        }

        return new IngestResult(pages.size(), totalChunks, collectionName);
    }

    // ── Retrieve: hybrid search + rerank ─────────────────────────────────────
    public String retrieve(String query, String collectionName)
            throws IOException, InterruptedException {

        List<Double> queryVec = embedder.embed(query);

        // 1. Semantic search
        List<VectorStore.ScoredChunk> semanticChunks;
        try {
            semanticChunks = vectorStore.queryWithIds(collectionName, queryVec, SEMANTIC_K);
        } catch (IOException e) {
            System.out.println("RAG: semantic search failed for '"
                    + collectionName + "': " + e.getMessage());
            return "";
        }

        // 2. Keyword search — extract meaningful tokens from the query
        List<String> keywords = extractKeywords(query);
        List<VectorStore.ScoredChunk> keywordChunks = List.of();
        if (!keywords.isEmpty()) {
            try {
                keywordChunks = vectorStore.queryByKeyword(
                        collectionName, keywords, KEYWORD_K);
            } catch (IOException e) {
                // Keyword search failure is non-fatal — semantic results are enough
                System.out.println("RAG: keyword search skipped: " + e.getMessage());
            }
        }

        // 3. Merge + deduplicate (semantic results first, then keyword additions)
        LinkedHashMap<String, String> seen = new LinkedHashMap<>();
        for (VectorStore.ScoredChunk c : semanticChunks)
            seen.putIfAbsent(normalise(c.document()), c.document());
        for (VectorStore.ScoredChunk c : keywordChunks)
            seen.putIfAbsent(normalise(c.document()), c.document());

        List<String> candidates = new ArrayList<>(seen.values());

        if (candidates.isEmpty()) {
            System.out.println("RAG: no candidates found in '" + collectionName + "'");
            return "";
        }

        // 4. Rerank
        List<String> ranked = reranker.rerank(query, candidates, FINAL_K);

        // 5. Build context string
        StringBuilder ctx = new StringBuilder();
        ctx.append("[Relevant background knowledge:\n");
        for (int i = 0; i < ranked.size(); i++) {
            ctx.append("--- Source ").append(i + 1).append(" ---\n");
            ctx.append(ranked.get(i)).append("\n");
        }
        ctx.append("]");

        String result = ctx.toString();
        if (result.length() > MAX_CONTEXT_LEN)
            result = result.substring(0, MAX_CONTEXT_LEN) + "\n...(truncated)]";

        return result;
    }

    // ── Extract keywords: drop stopwords, keep meaningful tokens ─────────────
    private List<String> extractKeywords(String query) {
        // Use HashSet — Set.of() throws on duplicates which crashes at runtime
        Set<String> stopwords = new HashSet<>(Arrays.asList(
                "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
                "have", "has", "had", "do", "does", "did", "will", "would", "could", "should",
                "may", "might", "shall", "can", "to", "of", "in", "for", "on", "with", "at",
                "by", "from", "up", "about", "into", "through", "during", "what", "which",
                "who", "how", "when", "where", "why", "and", "or", "but", "if", "then",
                "that", "this", "these", "those", "i", "me", "my", "you", "your", "it", "its",
                "tell", "give", "show", "explain", "much", "many", "some", "any", "use"));

        return Arrays.stream(query.toLowerCase().split("[^a-z0-9]+"))
                .filter(w -> w.length() >= 3 && !stopwords.contains(w))
                .distinct()
                .limit(4)
                .collect(Collectors.toList());
    }

    // Normalise a chunk for deduplication (first 80 chars lowercased)
    private String normalise(String s) {
        return s.toLowerCase().substring(0, Math.min(80, s.length())).trim();
    }

    // ── Result record ─────────────────────────────────────────────────────────
    public record IngestResult(int pagesIngested, int chunksIngested, String collection) {
    }
}
