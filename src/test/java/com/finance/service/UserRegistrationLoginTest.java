package com.finance.service;

import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.User;
import com.finance.repository.ImportedTransactionRepository;
import com.finance.security.UserDetailsServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest(classes = com.finance.app.OfflineFinanceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@Transactional
class UserRegistrationLoginTest {

    @Autowired
    UserService userService;

    @Autowired
    UserDetailsServiceImpl userDetailsService;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ImportedTransactionRepository importedTransactionRepository;

    @Test
    void newUserCanRegisterAndLogin() {
        String uname = "testuser_" + System.currentTimeMillis();
        String email = uname + "@local.test";
        String rawPassword = "secret123";

        // 1. Register new unique user — must succeed
        User saved = userService.registerUser(uname, email, rawPassword, "Test User");
        assertNotNull(saved.getId());
        // password must be BCrypt-encoded, never plain
        assertNotEquals(rawPassword, saved.getPassword());
        assertTrue(saved.getPassword().startsWith("$2"));

        // 2. Login path — Spring Security loads by username
        UserDetails details = userDetailsService.loadUserByUsername(uname);
        assertEquals(uname, details.getUsername());
        assertTrue(details.isEnabled());

        // 3. Password must verify (what login does internally)
        assertTrue(userService.verifyPassword(saved, rawPassword));
        assertFalse(userService.verifyPassword(saved, "wrongpass"));

        // 4. Duplicate username must be rejected with friendly message
        IllegalArgumentException dupUser = assertThrows(IllegalArgumentException.class,
                () -> userService.registerUser(uname, "other_" + email, "other123", "Other"));
        assertEquals("Username already exists", dupUser.getMessage());

        // 5. Duplicate email must be rejected
        IllegalArgumentException dupEmail = assertThrows(IllegalArgumentException.class,
                () -> userService.registerUser("other_" + uname, email, "other123", "Other"));
        assertEquals("Email already exists", dupEmail.getMessage());
    }

    @Test
    void importPreviewRendersWithoutCsrfRequestAttribute() throws Exception {
        String username = "preview_user_" + System.currentTimeMillis();
        User user = userService.registerUser(username, username + "@local.test", "secret123", "Preview User");
        ImportedTransaction record = new ImportedTransaction();
        record.setUser(user);
        record.setOriginalFilename("preview.csv");
        record.setFileType("CSV");
        record.setTransactionDate(LocalDate.of(2026, 9, 25));
        record.setDescription("Preview transaction");
        record.setTransactionType("EXPENSE");
        record.setAmount(new BigDecimal("12.34"));
        record.setCategory("Food");
        importedTransactionRepository.save(record);

        String response = mockMvc.perform(get("/import/preview").with(user(username)))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertTrue(response.contains("Import Preview"));
        assertTrue(response.contains("name=\"editIds\" value=\"" + record.getId() + "\""));
    }
}
