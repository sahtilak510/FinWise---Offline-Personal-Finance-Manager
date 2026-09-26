package com.finance.chatbot.model;

public class ChatRequest {
    @jakarta.validation.constraints.NotBlank(message = "Message must not be blank")
    @jakarta.validation.constraints.Size(max = 500, message = "Message must be <= 500 characters")
    private String message;
    private String language;

    public ChatRequest() {}

    public ChatRequest(String message) {
        this.message = message;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
}