package com.finance.service;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class LocalCategoryEngine implements CategoryEngine {

    private static final List<String> CATEGORIES = Arrays.asList(
            "Food", "Shopping", "Transport", "Bills", "Entertainment",
            "Health", "Education", "Salary", "Investment", "Other"
    );

    private static final Map<String, List<String>> KEYWORDS = Map.ofEntries(
            Map.entry("Food", Arrays.asList("restaurant", "cafe", "coffee", "pizza", "burger", "grocery", "milk", "bakery", "food", "lunch", "dinner", "supermarket", "grocer")),
            Map.entry("Shopping", Arrays.asList("amazon", "shop", "store", "marketplace", "mall", "retail", "clothes", "fashion", "purchase", "shopping", "ecommerce")),
            Map.entry("Transport", Arrays.asList("uber", "lyft", "taxi", "petrol", "fuel", "train", "bus", "metro", "flight", "transport", "parking", "vehicle")),
            Map.entry("Bills", Arrays.asList("internet", "electricity", "water", "gas", "phone", "utility", "rent", "mortgage", "insurance", "bill", "subscription")),
            Map.entry("Entertainment", Arrays.asList("movie", "netflix", "spotify", "cinema", "concert", "game", "streaming", "fun", "entertainment", "theater")),
            Map.entry("Health", Arrays.asList("pharmacy", "clinic", "hospital", "doctor", "medicine", "health", "fitness", "wellness", "dentist")),
            Map.entry("Education", Arrays.asList("school", "college", "course", "tuition", "books", "study", "education", "university", "training")),
            Map.entry("Salary", Arrays.asList("salary", "payroll", "wage", "bonus", "paycheck", "deposit", "income")),
            Map.entry("Investment", Arrays.asList("investment", "brokerage", "mutual", "stock", "crypto", "dividend", "interest", "portfolio"))
    );

    @Override
    public String categorize(String description, String merchant, BigDecimal amount, String transactionType) {
        String normalizedText = normalize(description + " " + merchant + " " + transactionType);

        if (transactionType != null && (transactionType.equalsIgnoreCase("INCOME") || transactionType.equalsIgnoreCase("CREDIT"))) {
            if (normalizedText.contains("salary") || normalizedText.contains("payroll") || normalizedText.contains("wage") || normalizedText.contains("bonus")) {
                return "Salary";
            }
            if (normalizedText.contains("investment") || normalizedText.contains("dividend") || normalizedText.contains("interest") || normalizedText.contains("portfolio")) {
                return "Investment";
            }
            return "Salary";
        }

        for (Map.Entry<String, List<String>> entry : KEYWORDS.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (normalizedText.contains(keyword)) {
                    return entry.getKey();
                }
            }
        }

        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            return "Other";
        }
        return "Other";
    }

    @Override
    public List<String> getSupportedCategories() {
        return CATEGORIES;
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9\\s]", " ");
    }
}
