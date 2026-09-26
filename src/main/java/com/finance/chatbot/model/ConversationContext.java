package com.finance.chatbot.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

public class ConversationContext {

    private String lastIntent;
    private String lastEntityCategory;
    private LocalDate lastDateStart;
    private LocalDate lastDateEnd;
    private YearMonth lastYearMonth;
    private String lastEntityType;
    private BigDecimal lastAmount;
    private int lastLimit = 8;
    private final Map<String, Object> entities = new HashMap<>();
    private long lastInteractionTime = System.currentTimeMillis();

    public String getLastIntent() { return lastIntent; }
    public void setLastIntent(String lastIntent) { this.lastIntent = lastIntent; }

    public String getLastEntityCategory() { return lastEntityCategory; }
    public void setLastEntityCategory(String lastEntityCategory) { this.lastEntityCategory = lastEntityCategory; }

    public LocalDate getLastDateStart() { return lastDateStart; }
    public void setLastDateStart(LocalDate lastDateStart) { this.lastDateStart = lastDateStart; }

    public LocalDate getLastDateEnd() { return lastDateEnd; }
    public void setLastDateEnd(LocalDate lastDateEnd) { this.lastDateEnd = lastDateEnd; }

    public YearMonth getLastYearMonth() { return lastYearMonth; }
    public void setLastYearMonth(YearMonth lastYearMonth) { this.lastYearMonth = lastYearMonth; }

    public String getLastEntityType() { return lastEntityType; }
    public void setLastEntityType(String lastEntityType) { this.lastEntityType = lastEntityType; }

    public BigDecimal getLastAmount() { return lastAmount; }
    public void setLastAmount(BigDecimal lastAmount) { this.lastAmount = lastAmount; }

    public int getLastLimit() { return lastLimit; }
    public void setLastLimit(int lastLimit) { this.lastLimit = lastLimit; }

    public Map<String, Object> getEntities() { return entities; }
    public void setEntity(String key, Object value) { entities.put(key, value); }
    public Object getEntity(String key) { return entities.get(key); }

    public long getLastInteractionTime() { return lastInteractionTime; }
    public void touch() { lastInteractionTime = System.currentTimeMillis(); }

    public boolean isStale() {
        return System.currentTimeMillis() - lastInteractionTime > 30 * 60 * 1000;
    }

    public void clear() {
        lastIntent = null;
        lastEntityCategory = null;
        lastDateStart = null;
        lastDateEnd = null;
        lastYearMonth = null;
        lastEntityType = null;
        lastAmount = null;
        lastLimit = 8;
        entities.clear();
        lastInteractionTime = System.currentTimeMillis();
    }
}
