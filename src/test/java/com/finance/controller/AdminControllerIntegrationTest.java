package com.finance.controller;

import com.finance.model.entity.User;
import com.finance.model.entity.Income;
import com.finance.repository.IncomeRepository;
import com.finance.repository.UserRepository;
import com.finance.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(classes = com.finance.app.OfflineFinanceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@Transactional
class AdminControllerIntegrationTest {
    @Value("${spring.security.user.name}")
    private String adminUsername;

    @Value("${spring.security.user.password}")
    private String adminPassword;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private IncomeRepository incomeRepository;

    @Test
    void adminCanOpenDatabaseAndUserOverview() throws Exception {
        mockMvc.perform(get("/admin").with(user("test-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("admin/dashboard"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Database tables")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Registered users")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ZIP + files")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Reset password")));
    }

    @Test
    void regularUserCannotOpenAdminOverview() throws Exception {
        mockMvc.perform(get("/admin").with(user("regular-user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanDeleteUserAndTheirData() throws Exception {
        String username = "admin-delete-" + System.currentTimeMillis();
        User userToDelete = userService.registerUser(
                username, username + "@local.test", "private-test-password", "Temporary User");

        mockMvc.perform(post("/admin/users/{id}/delete", userToDelete.getId())
                        .with(user("test-admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        assertFalse(userRepository.existsById(userToDelete.getId()));
    }

    @Test
    void adminCanDownloadUserExcelAndZipWithoutPasswordHashes() throws Exception {
        String username = "admin-export-" + System.currentTimeMillis();
        User exportedUser = userService.registerUser(
                username, username + "@local.test", "private-test-password", "Export User");
        Income income = new Income();
        income.setUser(exportedUser);
        income.setDescription("Exported salary");
        income.setAmount(new java.math.BigDecimal("1250.00"));
        income.setIncomeDate(java.time.LocalDate.of(2026, 10, 1));
        income.setCategory("Salary");
        income.setIncomeSource("Employer");
        incomeRepository.save(income);

        byte[] excel = mockMvc.perform(get("/admin/users/{id}/export.xlsx", exportedUser.getId())
                        .with(user("test-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andReturn().getResponse().getContentAsByteArray();
        try (org.apache.poi.ss.usermodel.Workbook workbook =
                     new org.apache.poi.xssf.usermodel.XSSFWorkbook(new ByteArrayInputStream(excel))) {
            for (String sheetName : new String[]{"Profile", "Accounts", "Income", "Expenses", "Budgets", "Goals",
                    "Imported Transactions", "Categories", "Chat History", "FP Recurring", "FP Notifications",
                    "FP Settings", "FP Attachments", "FP Backups"}) {
                assertNotNull(workbook.getSheet(sheetName), sheetName);
            }
            assertEquals("Exported salary",
                    workbook.getSheet("Income").getRow(1).getCell(2).getStringCellValue());
            for (org.apache.poi.ss.usermodel.Sheet sheet : workbook) {
                for (org.apache.poi.ss.usermodel.Row row : sheet) {
                    for (org.apache.poi.ss.usermodel.Cell cell : row) {
                        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.STRING) {
                            assertNotEquals(exportedUser.getPassword(), cell.getStringCellValue());
                            assertFalse(cell.getStringCellValue().contains("passwordResetTokenHash"));
                        }
                    }
                }
            }
        }

        byte[] archive = mockMvc.perform(get("/admin/users/{id}/export.zip", exportedUser.getId())
                        .with(user("test-admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/zip"))
                .andReturn().getResponse().getContentAsByteArray();
        boolean containsWorkbook = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if ("finwise-data.xlsx".equals(entry.getName())) {
                    containsWorkbook = true;
                }
            }
        }
        assertTrue(containsWorkbook);
    }

    @Test
    void adminPasswordResetLinkCanBeUsedOnlyOnceToSetANewPassword() throws Exception {
        String username = "admin-reset-" + System.currentTimeMillis();
        User resetUser = userService.registerUser(
                username, username + "@local.test", "old-private-password", "Reset User");

        MvcResult result = mockMvc.perform(post("/admin/users/{id}/password-reset", resetUser.getId())
                        .with(user("test-admin").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        String resetUrl = (String) result.getFlashMap().get("resetUrl");
        assertNotNull(resetUrl);
        String token = resetUrl.substring(resetUrl.indexOf("token=") + "token=".length());
        assertNotEquals(token, userRepository.findById(resetUser.getId()).orElseThrow().getPasswordResetTokenHash());
        mockMvc.perform(post("/reset-password")
                        .param("userId", resetUser.getId().toString())
                        .param("token", token)
                        .param("newPassword", "new-private-password")
                        .param("confirmPassword", "new-private-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reset=success"));

        User updated = userRepository.findById(resetUser.getId()).orElseThrow();
        assertTrue(userService.verifyPassword(updated, "new-private-password"));
        assertFalse(userService.verifyPassword(updated, "old-private-password"));
        assertTrue(updated.getPasswordResetTokenHash() == null);

        mockMvc.perform(post("/reset-password")
                        .param("userId", resetUser.getId().toString())
                        .param("token", token)
                        .param("newPassword", "another-private-password")
                        .param("confirmPassword", "another-private-password")
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(view().name("reset-password"));
    }

    @Test
    void configuredAdminAndRegularUsersKeepTheirSeparateLoginDestinations() throws Exception {
        mockMvc.perform(post("/login")
                        .param("username", adminUsername)
                        .param("password", adminPassword)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin"));

        String username = "regular-login-" + System.currentTimeMillis();
        userService.registerUser(username, username + "@local.test", "private-test-password", "Regular User");

        mockMvc.perform(post("/login")
                        .param("username", username)
                        .param("password", "private-test-password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }
}
