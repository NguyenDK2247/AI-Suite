package com.aisuite.controller;

import com.aisuite.model.User;
import com.aisuite.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/user")
public class UserController {

    private static final Pattern IANA_TZ = Pattern.compile("[A-Za-z0-9/_+\\-]+");

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // ── GET /user/settings ────────────────────────────────────────────────────
    @GetMapping("/settings")
    public ResponseEntity<Map<String, Object>> getSettings(HttpServletRequest request) {
        int userId = (int) request.getAttribute("userId");
        User user = userService.findById(userId);
        if (user == null)
            return ResponseEntity.status(404)
                    .body(Map.of("ok", false, "error", "User not found."));

        return ResponseEntity.ok(Map.of(
                "ok", true,
                "timezone", user.getTimezone()));
    }

    // ── PATCH /user/settings ──────────────────────────────────────────────────
    @PatchMapping("/settings")
    public ResponseEntity<Map<String, Object>> updateSettings(
            @RequestBody Map<String, String> body,
            HttpServletRequest request) {

        int userId = (int) request.getAttribute("userId");
        String timezone = body.get("timezone");

        if (timezone == null || timezone.isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "Missing timezone field."));
        if (!IANA_TZ.matcher(timezone).matches())
            return ResponseEntity.badRequest()
                    .body(Map.of("ok", false, "error", "Invalid timezone format."));

        userService.updateTimezone(userId, timezone);
        return ResponseEntity.ok(Map.of("ok", true, "timezone", timezone));
    }
}
