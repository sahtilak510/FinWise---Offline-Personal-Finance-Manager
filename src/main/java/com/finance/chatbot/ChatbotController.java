package com.finance.chatbot;

import com.finance.chatbot.model.ChatRequest;
import com.finance.chatbot.model.ChatResponse;
import com.finance.chatbot.service.ChatbotService;
import com.finance.model.entity.ChatMessage;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.ChatHistoryService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chatbot")
public class ChatbotController {

    private final ChatbotService chatbotService;
    private final ChatHistoryService chatHistoryService;
    private final UserRepository userRepository;

    public ChatbotController(ChatbotService chatbotService, ChatHistoryService chatHistoryService, UserRepository userRepository) {
        this.chatbotService = chatbotService;
        this.chatHistoryService = chatHistoryService;
        this.userRepository = userRepository;
    }

    @PostMapping("/ask")
    public ResponseEntity<ChatResponse> askQuestion(Authentication auth, @RequestBody @Valid ChatRequest request) {
        if (auth == null || auth.getName() == null) {
            ChatResponse r = new ChatResponse("⚠️ Please log in to use the chatbot. Your data is private per user.", LocalDateTime.now().toString());
            return ResponseEntity.status(401).body(r);
        }
        String username = auth.getName();
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            ChatResponse r = new ChatResponse("⚠️ User not found. Please re-login.", LocalDateTime.now().toString());
            return ResponseEntity.status(404).body(r);
        }

        String userMessage = request.getMessage();
        if (userMessage == null || userMessage.trim().isEmpty()) {
            ChatResponse r = new ChatResponse("Please type a question. Try: \"How much did I spend this month?\" or \"Show my recent transactions\"", LocalDateTime.now().toString());
            return ResponseEntity.badRequest().body(r);
        }
        if (userMessage.length() > 500) {
            ChatResponse r = new ChatResponse("⚠️ Message too long. Keep it under 500 characters.", LocalDateTime.now().toString());
            return ResponseEntity.badRequest().body(r);
        }

        try {
            chatHistoryService.saveMessage(user, "USER", userMessage);
        } catch (Exception e) {
            // Non-fatal
        }

        ChatbotService.ChatResult result;
        try {
            result = chatbotService.generateResponse(user, userMessage);
        } catch (Exception e) {
            result = new ChatbotService.ChatResult();
            result.message = "⚠️ Something went wrong processing your question. Please try again. (All data remains local & offline)";
            result.confidence = 0.0;
            result.intent = "ERROR";
            result.suggestions = List.of("How much did I spend this month?", "What is my monthly income?");
        }

        try {
            chatHistoryService.saveMessage(user, "BOT", result.message);
        } catch (Exception e) {
            // ignore
        }

        ChatResponse chatResponse = new ChatResponse(
                result.message,
                LocalDateTime.now().toString(),
                result.confidence,
                result.intent,
                result.suggestions
        );
        chatResponse.setChart(result.chart);
        return ResponseEntity.ok(chatResponse);
    }

    @GetMapping("/history")
    public ResponseEntity<List<Map<String, String>>> getHistory(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(404).build();
        List<ChatMessage> history = chatHistoryService.getHistory(user);
        List<Map<String, String>> dto = history.stream().map(m -> {
            Map<String, String> map = new HashMap<>();
            map.put("role", m.getRole());
            map.put("content", m.getContent());
            map.put("timestamp", m.getCreatedAt() != null ? m.getCreatedAt().toString() : "");
            map.put("id", String.valueOf(m.getId()));
            return map;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(dto);
    }

    @DeleteMapping("/history")
    public ResponseEntity<Map<String, String>> clearHistory(Authentication auth) {
        if (auth == null) return ResponseEntity.status(401).build();
        User user = userRepository.findByUsername(auth.getName()).orElse(null);
        if (user == null) return ResponseEntity.status(404).build();
        chatHistoryService.clearHistory(user);
        chatbotService.clearContext(user);
        Map<String, String> res = new HashMap<>();
        res.put("status", "cleared");
        res.put("message", "Chat history cleared for your account only.");
        return ResponseEntity.ok(res);
    }

    @GetMapping("/help")
    public ResponseEntity<Map<String, Object>> getHelp() {
        Map<String, Object> help = new HashMap<>();
        help.put("status", "active");
        help.put("name", "Finwise Premium Offline Assistant");
        help.put("version", "3.0.0");
        help.put("mode", "offline-local-database");
        help.put("auth", "user-isolated — each user sees only their own records");
        help.put("security", "No arbitrary SQL, no external APIs, no cloud — predefined safe queries only");
        help.put("capabilities", List.of(
                "Total balance & net worth",
                "Expenses: total, this month, last month, by category, by date",
                "Income: total, this month, last month, by category",
                "Budget status & remaining (limit - spent)",
                "Recent transactions (unified expenses + income)",
                "Biggest / smallest / average expenses",
                "Income vs expense comparison",
                "Financial goals progress",
                "Monthly report with insights",
                "Spending trends & patterns",
                "Transaction search",
                "Account balances",
                "Follow-up questions (context-aware)"
        ));
        help.put("examples", List.of(
                "How much did I spend this month?",
                "What about last month?",
                "Spending by category",
                "Income vs expenses",
                "Show my top 5 expenses",
                "Budget remaining for food",
                "What are my goals?",
                "Monthly report",
                "Am I spending more than last month?"
        ));
        help.put("storage", "Chat history stored locally in MySQL table chat_history, per-user isolated");
        return ResponseEntity.ok(help);
    }
}
