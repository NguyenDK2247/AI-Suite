import java.io.IOException;
import java.util.*;

/**
 * Reranks a list of candidate chunks by computing cosine similarity
 * between the query embedding and each chunk's embedding.
 *
 * This is a lightweight cross-encoder substitute — same model
 * (nomic-embed-text),
 * just used to score already-retrieved candidates more precisely than L2
 * distance.
 */
public class Reranker {

    private final EmbeddingService embedder;

    // Only rerank if we have more candidates than we need
    private static final int MIN_CANDIDATES_TO_RERANK = 4;

    public Reranker(EmbeddingService embedder) {
        this.embedder = embedder;
    }

    /**
     * Given a query and a list of candidate chunks, returns the top-k
     * chunks reordered by cosine similarity to the query.
     */
    public List<String> rerank(String query,
            List<String> candidates,
            int topK) throws IOException, InterruptedException {

        if (candidates.size() <= MIN_CANDIDATES_TO_RERANK) {
            // Not enough candidates to bother reranking — return as-is up to topK
            return candidates.subList(0, Math.min(topK, candidates.size()));
        }

        List<Double> queryVec = embedder.embed(query);
        List<ScoredDoc> scored = new ArrayList<>();

        for (String chunk : candidates) {
            try {
                List<Double> chunkVec = embedder.embed(chunk);
                double score = cosineSimilarity(queryVec, chunkVec);
                scored.add(new ScoredDoc(chunk, score));
            } catch (Exception e) {
                // If embedding a chunk fails, include it with a low score
                scored.add(new ScoredDoc(chunk, -1.0));
            }
        }

        // Sort descending by cosine similarity (higher = more relevant)
        scored.sort((a, b) -> Double.compare(b.score(), a.score()));

        System.out.println("Reranker: reranked " + candidates.size()
                + " candidates, keeping top " + topK);

        return scored.stream()
                .limit(topK)
                .map(ScoredDoc::document)
                .toList();
    }

    // ── Cosine similarity ─────────────────────────────────────────────────────
    private double cosineSimilarity(List<Double> a, List<Double> b) {
        if (a.size() != b.size())
            return 0.0;
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0 : dot / denom;
    }

    private record ScoredDoc(String document, double score) {
    }
}
