package com.finance.financeplus.web;

import com.finance.financeplus.model.FinancePlusSettings;
import com.finance.financeplus.service.FinancePlusAttachmentService;
import com.finance.financeplus.service.FinancePlusBackupService;
import com.finance.financeplus.service.FinancePlusExportService;
import com.finance.financeplus.service.FinancePlusImportService;
import com.finance.financeplus.service.FinancePlusNotificationService;
import com.finance.financeplus.service.FinancePlusRecurringService;
import com.finance.financeplus.service.FinancePlusSettingsService;
import com.finance.model.entity.User;
import com.finance.repository.ExpenseRepository;
import com.finance.repository.ImportedTransactionRepository;
import com.finance.repository.IncomeRepository;
import com.finance.repository.UserRepository;
import com.finance.service.CashFlowForecastService;
import com.finance.service.UserDataExportService;
import com.finance.service.excel.FinancialHealthScoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(FinancePlusController.class)
@ContextConfiguration(classes = FinancePlusController.class)
@AutoConfigureMockMvc(addFilters = false)
class FinancePlusControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean UserRepository userRepository;
    @MockBean ExpenseRepository expenseRepository;
    @MockBean IncomeRepository incomeRepository;
    @MockBean ImportedTransactionRepository importedTransactionRepository;
    @MockBean FinancePlusSettingsService settingsService;
    @MockBean FinancePlusRecurringService recurringService;
    @MockBean FinancePlusNotificationService notificationService;
    @MockBean FinancePlusAttachmentService attachmentService;
    @MockBean FinancePlusImportService importService;
    @MockBean FinancePlusExportService exportService;
    @MockBean FinancePlusBackupService backupService;
    @MockBean UserDataExportService userDataExportService;
    @MockBean CashFlowForecastService forecastService;
    @MockBean FinancialHealthScoreService healthScoreService;

    @Test
    void financePlusPageRendersLocalLibrariesAndFeatureSections() throws Exception {
        User user = testUser();
        FinancePlusSettings settings = new FinancePlusSettings();
        settings.setUser(user);
        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(settingsService.isPasscodeEnabled(user)).thenReturn(false);
        when(settingsService.getSettings(user)).thenReturn(settings);
        when(forecastService.forecast(eq(user), any(YearMonth.class)))
                .thenReturn(new CashFlowForecastService.CashFlowForecast(
                        YearMonth.of(2026, 10), new BigDecimal("3000"), new BigDecimal("1200"),
                        new BigDecimal("1800"), "Positive", 90, 3));
        when(forecastService.upcomingBills(user, 30)).thenReturn(List.of());
        when(healthScoreService.calculateHealthScore(user))
                .thenReturn(new FinancialHealthScoreService.FinancialHealthScore(
                        82, "A-", List.of("Savings: 40%"), 32, 18, 20, 12, 10));
        when(recurringService.getRules(user)).thenReturn(List.of());
        when(notificationService.getNotifications(user)).thenReturn(List.of());
        when(notificationService.getUnreadCount(user)).thenReturn(0L);
        when(attachmentService.getAttachments(user)).thenReturn(List.of());
        when(backupService.getBackups(user)).thenReturn(List.of());
        when(backupService.isVaultUnlocked(user)).thenReturn(false);
        when(expenseRepository.findByUserOrderByExpenseDateDesc(user)).thenReturn(List.of());
        when(incomeRepository.findByUserOrderByIncomeDateDesc(user)).thenReturn(List.of());
        when(importedTransactionRepository.findByUserOrderByTransactionDateDesc(user)).thenReturn(List.of());

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user.getUsername(), "N/A", List.of());
        mockMvc.perform(get("/finance-plus").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(view().name("finance-plus"))
                .andExpect(model().attribute("user", user))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Plan ahead. Stay protected.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/webjars/bootstrap/5.3.3/css/bootstrap.min.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/webjars/font-awesome/6.5.2/css/all.min.css")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"backups\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"import-export\"")));
    }

    private User testUser() {
        User user = new User();
        user.setId(101L);
        user.setUsername("finance-plus-user");
        user.setEmail("finance-plus@example.test");
        user.setFullName("Finance Plus User");
        return user;
    }
}
