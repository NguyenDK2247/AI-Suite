package com.aisuite.controller;

import com.aisuite.service.RagService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/ingest")
public class IngestController {

    private final RagService ragService;

    public IngestController(RagService ragService) {
        this.ragService = ragService;
    }

    // ── GET /ingest/collections ───────────────────────────────────────────────
    @GetMapping("/collections")
    public ResponseEntity<Map<String, Object>> listCollections() {
        return ResponseEntity.ok(Map.of(
                "collections", new String[] { "weather_knowledge", "currency_knowledge" }));
    }

    // ── POST /ingest ──────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        String url = (String) body.get("url");
        String collection = (String) body.get("collection");
        int depth = body.get("depth") instanceof Number n ? n.intValue() : 0;

        if (url == null || url.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "Missing 'url' field."));
        if (collection == null || collection.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "Missing 'collection' field."));
        if (!url.startsWith("http://") && !url.startsWith("https://"))
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "URL must start with http:// or https://"));
        if (depth < 0 || depth > 1)
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "depth must be 0 or 1."));

        try {
            RagService.IngestResult result = ragService.ingest(url, collection, depth);
            System.out.println("Ingest complete: " + result.pagesIngested()
                    + " page(s), " + result.chunksIngested()
                    + " chunks -> " + result.collection());
            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "pagesIngested", result.pagesIngested(),
                    "chunksIngested", result.chunksIngested(),
                    "collection", result.collection()));
        } catch (Exception e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            System.err.println("Ingest failed for " + url + ": " + msg);
            return ResponseEntity.internalServerError()
                    .body(Map.of("ok", false, "error", msg));
        }
    }
}
