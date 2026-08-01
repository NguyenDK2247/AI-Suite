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

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> index(HttpServletRequest request) {
        return requireAuth(request)
                ? serveStatic("static/index.html")
                : redirect("/login");
    }

    @GetMapping(value = "/currency", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> currency(HttpServletRequest request) {
        return requireAuth(request)
                ? serveStatic("static/currency.html")
                : redirect("/login");
    }

    @GetMapping(value = "/knowledge", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> knowledge(HttpServletRequest request) {
        return requireAuth(request)
                ? serveStatic("static/ingest.html")
                : redirect("/login");
    }

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> login() {
        return serveStatic("static/login.html");
    }

    @GetMapping(value = "/signup", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Resource> signup() {
        return serveStatic("static/signup.html");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    @SuppressWarnings("null")
    private ResponseEntity<Resource> serveStatic(String path) {
        @SuppressWarnings("null")
        Resource resource = new ClassPathResource(path);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(resource);
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
