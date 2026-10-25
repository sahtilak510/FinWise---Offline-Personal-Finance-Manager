package com.finance.service;

import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
public class AdminPasswordResetService {
    private static final int TOKEN_BYTES = 32;
    private static final int TOKEN_LIFETIME_MINUTES = 30;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdminPasswordResetService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public String createResetToken(User user) {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

        user.setPasswordResetTokenHash(passwordEncoder.encode(token));
        user.setPasswordResetExpiresAt(LocalDateTime.now().plusMinutes(TOKEN_LIFETIME_MINUTES));
        userRepository.save(user);
        return token;
    }

    @Transactional
    public void resetPassword(Long userId, String token, String newPassword) {
        if (userId == null || token == null || token.isBlank()) {
            throw new IllegalArgumentException("This reset link is invalid or expired.");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new IllegalArgumentException("Password must be at least 6 characters long.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("This reset link is invalid or expired."));
        String tokenHash = user.getPasswordResetTokenHash();
        LocalDateTime expiresAt = user.getPasswordResetExpiresAt();
        if (tokenHash == null || expiresAt == null
                || !expiresAt.isAfter(LocalDateTime.now())
                || !passwordEncoder.matches(token, tokenHash)) {
            throw new IllegalArgumentException("This reset link is invalid or expired.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordResetTokenHash(null);
        user.setPasswordResetExpiresAt(null);
        userRepository.save(user);
    }
}
