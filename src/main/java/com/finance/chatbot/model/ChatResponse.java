package com.finance.chatbot.model;

import java.util.List;
import java.util.Map;

public class ChatResponse {
    private String message;
    private String timestamp;
    private double confidence;
    private String intent;
    private List<String> suggestions;
    private Map<String, Object> chart;

    public ChatResponse() {}

    public ChatResponse(String message, String timestamp) {
        this.message = message;
        this.timestamp = timestamp;
        this.confidence = 1.0;
    }

    public ChatResponse(String message, String timestamp, double confidence, String intent, List<String> suggestions) {
        this.message = message;
        this.timestamp = timestamp;
        this.confidence = confidence;
        this.intent = intent;
        this.suggestions = suggestions;
    }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public List<String> getSuggestions() { return suggestions; }
    public void setSuggestions(List<String> suggestions) { this.suggestions = suggestions; }
    public Map<String, Object> getChart() { return chart; }
    public void setChart(Map<String, Object> chart) { this.chart = chart; }
}
