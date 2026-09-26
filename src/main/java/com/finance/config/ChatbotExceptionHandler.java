package com.finance.config;

import com.finance.chatbot.model.ChatResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@RestControllerAdvice(basePackages = "com.finance.chatbot")
public class ChatbotExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ChatResponse> handleValidation(MethodArgumentNotValidException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .findFirst().orElse("Invalid request. Keep message 1-500 chars.");
        ChatResponse r = new ChatResponse("⚠️ " + msg, LocalDateTime.now().toString());
        return ResponseEntity.badRequest().body(r);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ChatResponse> handleIllegal(IllegalArgumentException ex) {
        ChatResponse r = new ChatResponse("⚠️ " + ex.getMessage(), LocalDateTime.now().toString());
        return ResponseEntity.badRequest().body(r);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ChatResponse> handleGeneric(Exception ex) {
        ChatResponse r = new ChatResponse("⚠️ Something went wrong. Please try again. (Offline mode active)", LocalDateTime.now().toString());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(r);
    }
}
