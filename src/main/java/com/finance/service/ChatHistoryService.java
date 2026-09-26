package com.finance.service;

import com.finance.model.entity.ChatMessage;
import com.finance.model.entity.User;
import com.finance.repository.ChatMessageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

@Service
public class ChatHistoryService {

    private final ChatMessageRepository chatMessageRepository;

    public ChatHistoryService(ChatMessageRepository chatMessageRepository) {
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional
    public ChatMessage saveMessage(User user, String role, String content) {
        if (user == null || content == null || content.trim().isEmpty()) {
            throw new IllegalArgumentException("User and content must not be empty");
        }
        String safeRole = "BOT".equalsIgnoreCase(role) ? "BOT" : "USER";
        // Truncate to avoid oversized content (DB TEXT still limited pragmatically)
        String safeContent = content.length() > 4000 ? content.substring(0, 4000) : content;
        ChatMessage msg = new ChatMessage();
        msg.setUser(user);
        msg.setRole(safeRole);
        msg.setContent(safeContent);
        return chatMessageRepository.save(msg);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getHistory(User user) {
        return chatMessageRepository.findByUserOrderByCreatedAtAsc(user);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getRecentHistory(User user) {
        List<ChatMessage> recent = chatMessageRepository.findTop50ByUserOrderByCreatedAtDesc(user);
        // Return ascending for display
        recent.sort(Comparator.comparing(message -> message.getCreatedAt(), Comparator.nullsFirst(Comparator.naturalOrder())));
        return recent;
    }

    private LocalDateTime getCreatedAt(ChatMessage message) {
        return message == null ? null : message.getCreatedAt();
    }

    @Transactional
    public void clearHistory(User user) {
        chatMessageRepository.deleteByUser(user);
    }

    @Transactional(readOnly = true)
    public long countByUser(User user) {
        return chatMessageRepository.countByUser(user);
    }
}
