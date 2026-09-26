package com.finance.habits;

/**
 * Immutable result of one habit rule.
 * Demonstrates ENCAPSULATION — all fields private final, exposed via getters only.
 */
public class RuleResult {
    private final String ruleId;
    private final String title;
    private final int score; // 0-100
    private final String status; // GOOD, OK, BAD
    private final String message;
    private final String tip;

    public RuleResult(String ruleId, String title, int score, String status, String message, String tip) {
        this.ruleId = ruleId;
        this.title = title;
        this.score = Math.max(0, Math.min(100, score));
        this.status = status;
        this.message = message;
        this.tip = tip;
    }

    public String getRuleId() { return ruleId; }
    public String getTitle() { return title; }
    public int getScore() { return score; }
    public String getStatus() { return status; }
    public String getMessage() { return message; }
    public String getTip() { return tip; }
}
