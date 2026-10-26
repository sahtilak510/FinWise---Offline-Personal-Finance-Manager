package com.finance.features;

import com.finance.features.model.ExpenseShare;
import com.finance.features.service.FinancialPlanningService;
import com.finance.model.entity.User;
import com.finance.service.AccountService;
import com.finance.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = com.finance.app.OfflineFinanceApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("e2e")
@Transactional
class FinancialPlanningIntegrationTest {

    @Autowired
    private FinancialPlanningService planningService;

    @Autowired
    private UserService userService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void planningWorkspaceRendersForAuthenticatedUser() throws Exception {
        User owner = register("planning-page");
        planningService.addDocument(owner, "statement.pdf", "application/pdf",
                new byte[]{'%', 'P', 'D', 'F', '-', '2'});
        mockMvc.perform(get("/planning").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Debt and loan payoff planner")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Private financial document vault")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"file\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Delete file")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"confirmation\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Upload document")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")));
    }

    @Test
    void taxYearFilterUsesTheRequestedYear() throws Exception {
        User owner = register("planning-tax-year");
        mockMvc.perform(get("/planning").param("taxYearParam", "2025").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"taxYearParam\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"2025\"")));
    }

    @Test
    void netWorthIncludesAccountsAssetsInvestmentsAndDebt() {
        User owner = register("planning-net-worth");
        accountService.createAccount(owner, "Everyday", "CHECKING", new BigDecimal("100.00"), "Local");
        planningService.addAsset(owner, "Vehicle", "Vehicle", new BigDecimal("200.00"));
        planningService.addInvestment(owner, "Index fund", "IDX", "FUND",
                new BigDecimal("1.234567"), new BigDecimal("40.00"), new BigDecimal("80.00"));
        planningService.addDebt(owner, "Loan", new BigDecimal("75.00"), new BigDecimal("50.00"),
                new BigDecimal("6.25"), new BigDecimal("10.00"), LocalDate.now().plusDays(15));

        assertEquals(new BigDecimal("330.00"), planningService.netWorth(owner));
        assertEquals(new BigDecimal("1.234567"),
                planningService.investments(owner).get(0).getUnits());
    }

    @Test
    void householdSplitsAreEqualAndPrivateToMembers() {
        User owner = register("planning-household-owner");
        User member = register("planning-household-member");
        User outsider = register("planning-household-outsider");
        var household = planningService.createHousehold(owner, "Shared home");
        planningService.addHouseholdMember(owner, household.getId(), member.getUsername());
        planningService.addSharedBudget(member, household.getId(), "Groceries", new BigDecimal("200.00"));
        var budget = planningService.sharedBudgets(owner, household.getId()).get(0);
        var expense = planningService.addHouseholdExpense(owner, household.getId(), budget.getId(),
                "Groceries", new BigDecimal("10.01"), LocalDate.now());

        List<ExpenseShare> shares = planningService.expenseShares(member, household.getId(), expense.getId());
        assertEquals(2, shares.size());
        assertEquals(new BigDecimal("10.01"),
                shares.stream().map(ExpenseShare::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals(new BigDecimal("10.01"),
                planningService.sharedBudgets(owner, household.getId()).get(0).getSpentAmount());
        assertThrows(IllegalArgumentException.class,
                () -> planningService.householdExpenses(outsider, household.getId()));
    }

    @Test
    void personalRecordsAndDocumentsCannotBeReadByAnotherUser() {
        User owner = register("planning-private-owner");
        User other = register("planning-private-other");
        var debt = planningService.addDebt(owner, "Private loan", new BigDecimal("20.00"),
                new BigDecimal("20.00"), BigDecimal.ZERO, BigDecimal.ONE, null);
        var doc = planningService.addDocument(owner, "statement.pdf", "application/pdf",
                new byte[]{'%', 'P', 'D', 'F', '-'});

        assertTrue(planningService.debts(other).isEmpty());
        assertThrows(IllegalArgumentException.class,
                () -> planningService.updateDebtBalance(other, debt.getId(), BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class, () -> planningService.document(other, doc.getId()));
    }

    @Test
    void deletingHouseholdMemberPreservesSharedHistoryAndRemovesTheirPrivateData() {
        User owner = register("planning-delete-owner");
        User member = register("planning-delete-member");
        var household = planningService.createHousehold(owner, "Shared home");
        planningService.addHouseholdMember(owner, household.getId(), member.getUsername());
        var expense = planningService.addHouseholdExpense(member, household.getId(), null,
                "Shared dinner", new BigDecimal("30.00"), LocalDate.now());
        planningService.addDocument(member, "private.pdf", "application/pdf", new byte[]{'%', 'P', 'D', 'F', '-'});

        userService.deleteAccountAndData(member);

        assertEquals(1, planningService.householdMembers(owner, household.getId()).size());
        var retainedExpense = planningService.householdExpenses(owner, household.getId()).stream()
                .filter(item -> item.getId().equals(expense.getId())).findFirst().orElseThrow();
        assertNull(retainedExpense.getPaidBy());
        assertEquals(0, planningService.documents(owner).size());
        assertEquals(1, planningService.expenseShares(owner, household.getId(), expense.getId()).size());
    }

    @Test
    void uploadedDocumentsAreAuthorizedAndUnsafeFilesRejected() throws Exception {
        User owner = register("planning-upload-owner");
        User other = register("planning-upload-other");
        MockMultipartFile pdf = new MockMultipartFile("file", "..\\statement.pdf",
                "application/pdf", new byte[]{'%', 'P', 'D', 'F', '-', '1'});
        mockMvc.perform(multipart("/planning/documents").file(pdf)
                        .with(user(owner.getUsername())).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/planning#documents"));

        var document = planningService.documents(owner).get(0);
        var otherDocument = planningService.addDocument(owner, "other.pdf", "application/pdf",
                new byte[]{'%', 'P', 'D', 'F', '-', '2'});
        assertEquals("__statement.pdf", document.getFilename());
        mockMvc.perform(get("/planning").with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"confirmation\"")));
        mockMvc.perform(get("/planning/documents/{id}", document.getId()).with(user(owner.getUsername())))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(content().contentType("application/pdf"));
        mockMvc.perform(get("/planning/documents/{id}", document.getId()).with(user(other.getUsername())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/planning/documents/{id}/delete", document.getId())
                        .with(user(owner.getUsername())).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/planning#documents"))
                .andExpect(flash().attribute("errorMessage", "Type DELETE to confirm removal of this file."));
        assertEquals(2, planningService.documents(owner).size());

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/planning/documents/{id}/delete", document.getId())
                        .param("confirmation", "DELETE")
                        .with(user(owner.getUsername())).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/planning#documents"))
                .andExpect(flash().attribute("successMessage", "Vault file deleted. Your account was not changed."));
        assertEquals(List.of(otherDocument.getId()),
                planningService.documents(owner).stream().map(item -> item.getId()).toList());
        assertTrue(userService.getUserByUsername(owner.getUsername()).isPresent());

        MockMultipartFile invalid = new MockMultipartFile("file", "fake.pdf",
                "application/pdf", "not a pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mockMvc.perform(multipart("/planning/documents").file(invalid)
                        .with(user(owner.getUsername())).with(csrf()))
                .andExpect(status().isFound())
                .andExpect(flash().attributeExists("errorMessage"));
    }

    private User register(String prefix) {
        String suffix = Long.toString(System.nanoTime());
        return userService.registerUser(prefix + "_" + suffix, prefix + "_" + suffix + "@local.test",
                "secret123", prefix);
    }
}
