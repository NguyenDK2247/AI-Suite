package com.aisuite.controller;

import com.aisuite.model.HistoryEntry;
import com.aisuite.service.HistoryService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/history")
public class HistoryController {

    private final HistoryService historyService;

    public HistoryController(HistoryService historyService) {
        this.historyService = historyService;
    }

    // ── GET /history?page=weather ─────────────────────────────────────────────
    @GetMapping
    public ResponseEntity<?> getHistory(
            @RequestParam String page,
            HttpServletRequest request) {

        if (page == null || page.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "Missing page param."));

        int userId = (int) request.getAttribute("userId");
        List<HistoryEntry> entries = historyService.getHistory(userId, page);
        return ResponseEntity.ok(entries);
    }

    // ── POST /history ─────────────────────────────────────────────────────────
    @PostMapping
    public ResponseEntity<Map<String, Object>> addEntry(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        int userId = (int) request.getAttribute("userId");
        String page = (String) body.get("page");
        String question = (String) body.get("question");
        String answer = (String) body.get("answer");

        if (page == null || question == null || answer == null)
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "Missing fields: page, question, answer."));

        JsonNode extra = null;
        if (body.get("extra") instanceof Map || body.get("extra") instanceof List) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                extra = mapper.valueToTree(body.get("extra"));
            } catch (Exception ignored) {
            }
        }

        int newId = historyService.addEntry(userId, page, question, answer, extra);
        return ResponseEntity.status(201).body(Map.of("ok", true, "id", newId));
    }

    // ── DELETE /history?id=X or DELETE /history?page=weather ───────────────
    @DeleteMapping
    public ResponseEntity<Map<String, Object>> deleteHistory(
            @RequestParam(required = false) Integer id,
            @RequestParam(required = false) String page,
            HttpServletRequest request) {

        int userId = (int) request.getAttribute("userId");

        if (id != null) {
            historyService.deleteEntry(userId, id);
            return ResponseEntity.ok(Map.of("ok", true));
        } else if (page != null && !page.isBlank()) {
            historyService.clearPage(userId, page);
            return ResponseEntity.ok(Map.of("ok", true));
        } else {
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "Provide id or page param."));
        }
    }
}
