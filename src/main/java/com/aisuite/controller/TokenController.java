package com.aisuite.controller;

import com.aisuite.service.TokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/tokens")
public class TokenController {

    private final TokenService tokenService;

    public TokenController(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    // ── GET /tokens — return today's usage and limit ──────────────────────────
    @GetMapping
    public ResponseEntity<Map<String, Object>> getTokens(HttpServletRequest request) {
        int userId = (int) request.getAttribute("userId");
        int total = tokenService.getTodayTotal(userId);
        return ResponseEntity.ok(Map.of(
                "todayTotal", total,
                "dailyLimit", tokenService.getDailyLimit()));
    }

    // ── POST /tokens — add tokens from a message ──────────────────────────────
    @PostMapping
    public ResponseEntity<Map<String, Object>> addTokens(
            @RequestBody Map<String, Integer> body,
            HttpServletRequest request) {

        int userId = (int) request.getAttribute("userId");
        int tokens = body.getOrDefault("tokens", 0);
        if (tokens > 0)
            tokenService.addTokens(userId, tokens);
        int newTotal = tokenService.getTodayTotal(userId);
        return ResponseEntity.ok(Map.of(
                "todayTotal", newTotal,
                "dailyLimit", tokenService.getDailyLimit()));
    }
}
