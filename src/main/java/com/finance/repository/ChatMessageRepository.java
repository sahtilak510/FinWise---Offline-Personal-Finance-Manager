package com.finance.repository;

import com.finance.model.entity.ChatMessage;
import com.finance.model.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByUserOrderByCreatedAtAsc(User user);
    List<ChatMessage> findTop50ByUserOrderByCreatedAtDesc(User user);
    long countByUser(User user);
    void deleteByUser(User user);
}
