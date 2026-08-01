package com.aisuite.controller;

import com.aisuite.service.SessionService;
import com.aisuite.service.UserService;
import jakarta.servlet.http.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;
    private final SessionService sessionService;

    public AuthController(UserService userService, SessionService sessionService) {
        this.userService = userService;
        this.sessionService = sessionService;
    }

    // ── POST /auth/signup ─────────────────────────────────────────────────────
    @PostMapping("/signup")
    public ResponseEntity<Map<String, Object>> signup(
            @RequestBody Map<String, String> body,
            HttpServletResponse response) {

        String username = body.get("username");
        String password = body.get("password");

        UserService.SignupResult result = userService.signup(username, password);
        if (!result.ok())
            return ResponseEntity.status(409)
                    .body(Map.of("ok", false, "error", result.error()));

        String token = sessionService.createSession(result.userId(), true);
        addSessionCookie(response, token, true);
        return ResponseEntity.status(201)
                .body(Map.of("ok", true, "username", username));
    }

    // ── POST /auth/login ──────────────────────────────────────────────────────
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody Map<String, Object> body,
            HttpServletResponse response) {

        String username = (String) body.get("username");
        String password = (String) body.get("password");
        boolean rememberMe = Boolean.TRUE.equals(body.get("rememberMe"));

        UserService.LoginResult result = userService.login(username, password);
        if (!result.ok())
            return ResponseEntity.status(401)
                    .body(Map.of("ok", false, "error", result.error()));

        String token = sessionService.createSession(result.userId(), rememberMe);
        addSessionCookie(response, token, rememberMe);
        return ResponseEntity.ok(Map.of("ok", true, "username", username));
    }

    // ── POST /auth/logout ─────────────────────────────────────────────────────
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            @CookieValue(name = "session", required = false) String token,
            HttpServletResponse response) {

        sessionService.deleteSession(token);
        Cookie cookie = new Cookie("session", "");
        cookie.setMaxAge(0);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ── GET /auth/me ──────────────────────────────────────────────────────────
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(
            @CookieValue(name = "session", required = false) String token) {

        int userId = sessionService.validateSession(token);
        if (userId == -1)
            return ResponseEntity.status(401)
                    .body(Map.of("ok", false, "error", "Not logged in."));

        var user = userService.findById(userId);
        if (user == null)
            return ResponseEntity.status(404)
                    .body(Map.of("ok", false, "error", "User not found."));

        return ResponseEntity.ok(Map.of("ok", true, "username", user.getUsername()));
    }

    // ── Helper ────────────────────────────────────────────────────────────────
    private void addSessionCookie(HttpServletResponse response,
            String token, boolean rememberMe) {
        Cookie cookie = new Cookie("session", token);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        if (rememberMe)
            cookie.setMaxAge(2592000); // 30 days
        response.addCookie(cookie);
    }
}
