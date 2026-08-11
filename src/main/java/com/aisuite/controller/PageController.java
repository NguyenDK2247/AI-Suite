package com.aisuite.controller;

import com.aisuite.service.SessionService;
import jakarta.servlet.http.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PageController {

    private final SessionService sessionService;

    public PageController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping("/favicon.ico")
    public ResponseEntity<Void> favicon() {
        return ResponseEntity.noContent().build();
    }

    // ── Protected pages ───────────────────────────────────────────────────────
    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index(HttpServletRequest request) {
        return requireAuth(request) ? html("index.html") : redirect("/login");
    }

    @GetMapping(value = "/currency", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> currency(HttpServletRequest request) {
        return requireAuth(request) ? html("currency.html") : redirect("/login");
    }

    @GetMapping(value = "/translation", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> translation(HttpServletRequest request) {
        return requireAuth(request) ? html("translation.html") : redirect("/login");
    }

    @GetMapping(value = "/knowledge", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> knowledge(HttpServletRequest request) {
        return requireAuth(request) ? html("ingest.html") : redirect("/login");
    }

    // ── Public auth pages ─────────────────────────────────────────────────────
    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> login() {
        return html("login.html");
    }

    @GetMapping(value = "/signup", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> signup() {
        return html("signup.html");
    }

    // ── Static assets ─────────────────────────────────────────────────────────
    @GetMapping(value = "/styles.css", produces = "text/css")
    public ResponseEntity<Resource> stylesCss() {
        return css("styles.css");
    }

    @GetMapping(value = "/auth.css", produces = "text/css")
    public ResponseEntity<Resource> authCss() {
        return css("auth.css");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    @SuppressWarnings("null")
    private ResponseEntity<Resource> html(String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/" + filename));
    }

    private ResponseEntity<Resource> css(String filename) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/css"))
                .body(new ClassPathResource("static/" + filename));
    }

    private ResponseEntity<Resource> redirect(String location) {
        return ResponseEntity.status(302)
                .header("Location", location)
                .build();
    }

    private boolean requireAuth(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null)
            return false;
        for (Cookie c : cookies) {
            if ("session".equals(c.getName()))
                return sessionService.validateSession(c.getValue()) != -1;
        }
        return false;
    }
}
