package com.aisuite.config;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(StackOverflowError.class)
    public ResponseEntity<Map<String, Object>> handleStackOverflow(StackOverflowError e) {
        e.printStackTrace();
        return ResponseEntity.internalServerError()
                .body(Map.of("error", "StackOverflowError — check server logs for details"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception e) {
        e.printStackTrace();
        return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getClass().getSimpleName() + ": " + e.getMessage()));
    }
}
