package com.finance.features.web;

import com.finance.features.model.*;
import com.finance.features.service.FinancialPlanningService;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Year;
import java.util.*;

@Controller
@RequestMapping("/planning")
public class FinancialPlanningController {
    private static final long MAX_DOCUMENT_SIZE = 5L * 1024L * 1024L;

    private final FinancialPlanningService planningService;
    private final UserRepository userRepository;

    public FinancialPlanningController(FinancialPlanningService planningService, UserRepository userRepository) {
        this.planningService = planningService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public String page(Authentication authentication, Model model,
                       @RequestParam(required = false) Integer taxYearParam) {
        User user = currentUser(authentication);
        int taxYear = taxYearParam == null ? Year.now().getValue() : taxYearParam;
        if (taxYear < 1900 || taxYear > Year.now().getValue() + 1) {
            throw new IllegalArgumentException("Tax year must be between 1900 and next year");
        }
        List<TaxEntry> taxEntries = planningService.taxEntries(user).stream()
                .filter(entry -> entry.getEntryDate().getYear() == taxYear).toList();
        BigDecimal taxableIncome = taxEntries.stream().filter(entry -> "INCOME".equals(entry.getEntryType()))
                .map(TaxEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deductions = taxEntries.stream().filter(entry -> "DEDUCTION".equals(entry.getEntryType()))
                .map(TaxEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal subscriptionAnnualTotal = planningService.subscriptions(user).stream()
                .filter(Subscription::isActive).map(this::annualized)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<InvestmentHolding> investments = planningService.investments(user);
        BigDecimal investmentCost = investments.stream().map(InvestmentHolding::getCostBasis)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal investmentValue = investments.stream().map(InvestmentHolding::getCurrentValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal trackedAssets = planningService.assets(user).stream().map(TrackedAsset::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debtBalance = planningService.debts(user).stream().map(Debt::getCurrentBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Debt> avalancheOrder = planningService.debts(user).stream()
                .sorted(Comparator.comparing(Debt::getAnnualInterestRate).reversed()).toList();
        List<Debt> snowballOrder = planningService.debts(user).stream()
                .sorted(Comparator.comparing(Debt::getCurrentBalance)).toList();

        model.addAttribute("user", user);
        model.addAttribute("debts", planningService.debts(user));
        model.addAttribute("debtEstimates", planningService.payoffEstimates(user));
        model.addAttribute("avalancheOrder", avalancheOrder);
        model.addAttribute("snowballOrder", snowballOrder);
        model.addAttribute("assets", planningService.assets(user));
        model.addAttribute("trackedAssets", trackedAssets);
        model.addAttribute("debtBalance", debtBalance);
        model.addAttribute("investments", investments);
        model.addAttribute("investmentCost", investmentCost);
        model.addAttribute("investmentValue", investmentValue);
        model.addAttribute("investmentGain", investmentValue.subtract(investmentCost));
        model.addAttribute("taxEntries", taxEntries);
        model.addAttribute("taxYear", taxYear);
        model.addAttribute("currentTaxYear", Year.now().getValue());
        model.addAttribute("taxableIncome", taxableIncome);
        model.addAttribute("deductions", deductions);
        model.addAttribute("estimatedTaxableAmount", taxableIncome.subtract(deductions).max(BigDecimal.ZERO));
        model.addAttribute("subscriptions", planningService.subscriptions(user));
        model.addAttribute("subscriptionAnnualTotal", subscriptionAnnualTotal);
        model.addAttribute("challenges", planningService.challenges(user));
        model.addAttribute("documents", planningService.documents(user));
        model.addAttribute("accountBalance", planningService.accountBalance(user));
        model.addAttribute("netWorth", planningService.netWorth(user));
        return "financial-planning";
    }

    @PostMapping("/debts")
    public String addDebt(Authentication auth, RedirectAttributes flash,
                          @RequestParam String name, @RequestParam BigDecimal startingBalance,
                          @RequestParam BigDecimal currentBalance, @RequestParam BigDecimal annualInterestRate,
                          @RequestParam BigDecimal minimumPayment, @RequestParam(required = false) LocalDate dueDate) {
        return action(auth, flash, "debts", () -> planningService.addDebt(currentUser(auth), name, startingBalance,
                currentBalance, annualInterestRate, minimumPayment, dueDate));
    }

    @PostMapping("/debts/{id}/balance")
    public String updateDebt(Authentication auth, RedirectAttributes flash, @PathVariable Long id,
                             @RequestParam BigDecimal currentBalance) {
        return action(auth, flash, "debts",
                () -> planningService.updateDebtBalance(currentUser(auth), id, currentBalance));
    }

    @PostMapping("/debts/{id}/delete")
    public String deleteDebt(Authentication auth, RedirectAttributes flash, @PathVariable Long id) {
        return action(auth, flash, "debts", () -> planningService.deleteDebt(currentUser(auth), id));
    }

    @PostMapping("/assets")
    public String addAsset(Authentication auth, RedirectAttributes flash, @RequestParam String name,
                           @RequestParam String assetType, @RequestParam BigDecimal value) {
        return action(auth, flash, "net-worth",
                () -> planningService.addAsset(currentUser(auth), name, assetType, value));
    }

    @PostMapping("/assets/{id}/delete")
    public String deleteAsset(Authentication auth, RedirectAttributes flash, @PathVariable Long id) {
        return action(auth, flash, "net-worth", () -> planningService.deleteAsset(currentUser(auth), id));
    }

    @PostMapping("/assets/{id}/value")
    public String updateAssetValue(Authentication auth, RedirectAttributes flash, @PathVariable Long id,
                                   @RequestParam BigDecimal value) {
        return action(auth, flash, "net-worth",
                () -> planningService.updateAssetValue(currentUser(auth), id, value));
    }

    @PostMapping("/investments")
    public String addInvestment(Authentication auth, RedirectAttributes flash, @RequestParam String name,
                                @RequestParam(required = false) String ticker, @RequestParam String investmentType,
                                @RequestParam BigDecimal units, @RequestParam BigDecimal costBasis,
                                @RequestParam BigDecimal currentValue) {
        return action(auth, flash, "investments", () -> planningService.addInvestment(currentUser(auth), name, ticker,
                investmentType, units, costBasis, currentValue));
    }

    @PostMapping("/investments/{id}/delete")
    public String deleteInvestment(Authentication auth, RedirectAttributes flash, @PathVariable Long id) {
        return action(auth, flash, "investments", () -> planningService.deleteInvestment(currentUser(auth), id));
    }

    @PostMapping("/investments/{id}/value")
    public String updateInvestment(Authentication auth, RedirectAttributes flash, @PathVariable Long id,
                                   @RequestParam BigDecimal units, @RequestParam BigDecimal costBasis,
                                   @RequestParam BigDecimal currentValue) {
        return action(auth, flash, "investments", () -> planningService.updateInvestment(currentUser(auth), id,
                units, costBasis, currentValue));
    }

    @PostMapping("/tax")
    public String addTaxEntry(Authentication auth, RedirectAttributes flash, @RequestParam String description,
                             @RequestParam String category, @RequestParam String entryType,
                             @RequestParam BigDecimal amount, @RequestParam LocalDate entryDate) {
        return action(auth, flash, "tax",
                () -> planningService.addTaxEntry(currentUser(auth), description, category, entryType, amount, entryDate));
    }

    @PostMapping("/tax/{id}/delete")
    public String deleteTaxEntry(Authentication auth, RedirectAttributes flash, @PathVariable Long id,
                                 @RequestParam int taxYear) {
        return action(auth, flash, "tax?taxYear=" + taxYear,
                () -> planningService.deleteTaxEntry(currentUser(auth), id));
    }

    @PostMapping("/subscriptions")
    public String addSubscription(Authentication auth, RedirectAttributes flash, @RequestParam String name,
                                  @RequestParam BigDecimal amount, @RequestParam String frequency,
                                  @RequestParam LocalDate nextRenewal) {
        return action(auth, flash, "subscriptions", () -> planningService.addSubscription(currentUser(auth),
                name, amount, frequency, nextRenewal));
    }

    @PostMapping("/subscriptions/{id}/active")
    public String toggleSubscription(Authentication auth, RedirectAttributes flash, @PathVariable Long id,
                                     @RequestParam boolean active) {
        return action(auth, flash, "subscriptions",
                () -> planningService.setSubscriptionActive(currentUser(auth), id, active));
    }

    @PostMapping("/subscriptions/{id}/update")
    public String updateSubscription(Authentication auth, RedirectAttributes flash, @PathVariable Long id,
                                     @RequestParam BigDecimal amount, @RequestParam String frequency,
                                     @RequestParam LocalDate nextRenewal) {
        return action(auth, flash, "subscriptions", () -> planningService.updateSubscription(currentUser(auth),
                id, amount, frequency, nextRenewal));
    }

    @PostMapping("/subscriptions/{id}/delete")
    public String deleteSubscription(Authentication auth, RedirectAttributes flash, @PathVariable Long id) {
        return action(auth, flash, "subscriptions",
                () -> planningService.deleteSubscription(currentUser(auth), id));
    }

    @PostMapping("/households")
    public String createHousehold(Authentication auth, RedirectAttributes flash, @RequestParam String name) {
        return action(auth, flash, "households",
                () -> planningService.createHousehold(currentUser(auth), name));
    }

    @PostMapping("/households/{id}/members")
    public String addMember(Authentication auth, RedirectAttributes flash, @PathVariable Long id,
                            @RequestParam String username) {
        return action(auth, flash, "households", () -> planningService.addHouseholdMember(currentUser(auth), id, username),
                id);
    }

    @PostMapping("/household-members/{id}/delete")
    public String removeMember(Authentication auth, RedirectAttributes flash, @PathVariable Long id,
                               @RequestParam Long householdId) {
        return action(auth, flash, "households", () -> planningService.removeHouseholdMember(currentUser(auth), id),
                householdId);
    }

    @PostMapping("/shared-budgets")
    public String addSharedBudget(Authentication auth, RedirectAttributes flash, @RequestParam Long householdId,
                                  @RequestParam String name, @RequestParam BigDecimal limitAmount) {
        return action(auth, flash, "households", () -> planningService.addSharedBudget(currentUser(auth),
                householdId, name, limitAmount), householdId);
    }

    @PostMapping("/household-expenses")
    public String addHouseholdExpense(Authentication auth, RedirectAttributes flash,
                                      @RequestParam Long householdId, @RequestParam(required = false) Long budgetId,
                                      @RequestParam String description, @RequestParam BigDecimal amount,
                                      @RequestParam LocalDate expenseDate) {
        return action(auth, flash, "households", () -> planningService.addHouseholdExpense(currentUser(auth),
                householdId, budgetId, description, amount, expenseDate), householdId);
    }

    @PostMapping("/challenges")
    public String addChallenge(Authentication auth, RedirectAttributes flash, @RequestParam String name,
                               @RequestParam BigDecimal targetAmount, @RequestParam LocalDate targetDate,
                               @RequestParam String cadence) {
        return action(auth, flash, "challenges", () -> planningService.addChallenge(currentUser(auth),
                name, targetAmount, targetDate, cadence));
    }

    @PostMapping("/challenges/{id}/progress")
    public String updateChallenge(Authentication auth, RedirectAttributes flash, @PathVariable Long id,
                                  @RequestParam BigDecimal savedAmount) {
        return action(auth, flash, "challenges",
                () -> planningService.updateChallengeProgress(currentUser(auth), id, savedAmount));
    }

    @PostMapping("/challenges/{id}/delete")
    public String deleteChallenge(Authentication auth, RedirectAttributes flash, @PathVariable Long id) {
        return action(auth, flash, "challenges", () -> planningService.deleteChallenge(currentUser(auth), id));
    }

    @PostMapping("/documents")
    public String uploadDocument(Authentication auth, RedirectAttributes flash,
                                 @RequestParam("file") MultipartFile file) {
        User user = currentUser(auth);
        try {
            String contentType = detectDocumentType(file);
            planningService.addDocument(user, safeFilename(file.getOriginalFilename()), contentType, file.getBytes());
            flash.addFlashAttribute("successMessage", "Document uploaded to your private vault.");
            return "redirect:/planning#documents";
        } catch (IllegalArgumentException | IOException exception) {
            flash.addFlashAttribute("errorMessage", exception.getMessage());
            return "redirect:/planning#documents";
        }
    }

    @GetMapping("/documents/{id}")
    public ResponseEntity<byte[]> downloadDocument(Authentication auth, @PathVariable Long id) {
        FinancialDocument document = planningService.document(currentUser(auth), id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(document.getFilename()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .contentLength(document.getContentLength())
                .body(document.getData());
    }

    @PostMapping("/documents/{id}/delete")
    public String deleteDocument(Authentication auth, RedirectAttributes flash, @PathVariable Long id,
                                 @RequestParam(required = false) String confirmation) {
        if (!"DELETE".equals(confirmation)) {
            flash.addFlashAttribute("errorMessage", "Type DELETE to confirm removal of this file.");
            return "redirect:/planning#documents";
        }
        try {
            planningService.deleteDocument(currentUser(auth), id);
            flash.addFlashAttribute("successMessage", "Vault file deleted. Your account was not changed.");
        } catch (IllegalArgumentException exception) {
            flash.addFlashAttribute("errorMessage", exception.getMessage());
        }
        return "redirect:/planning#documents";
    }

    private String detectDocumentType(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Choose a document to upload");
        if (file.getSize() > MAX_DOCUMENT_SIZE) throw new IllegalArgumentException("Documents must be 5 MB or smaller");
        byte[] bytes = file.getBytes();
        if (startsWith(bytes, new byte[]{'%', 'P', 'D', 'F', '-'})) return "application/pdf";
        if (startsWith(bytes, new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})) return "image/png";
        if (startsWith(bytes, new byte[]{(byte) 0xff, (byte) 0xd8, (byte) 0xff})) return "image/jpeg";
        if (startsWith(bytes, new byte[]{'G', 'I', 'F', '8'})) return "image/gif";
        if (bytes.length >= 12 && startsWith(bytes, new byte[]{'R', 'I', 'F', 'F'})
                && new String(bytes, 8, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("WEBP")) return "image/webp";
        throw new IllegalArgumentException("Only PDF, PNG, JPEG, GIF, and WebP files are supported");
    }

    private boolean startsWith(byte[] value, byte[] prefix) {
        if (value.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if (value[index] != prefix[index]) return false;
        }
        return true;
    }

    private String safeFilename(String filename) {
        if (filename == null || filename.isBlank()) return "financial-document";
        String safe = filename.replace('\\', '_').replace('/', '_').replace("..", "_")
                .replaceAll("[\\p{Cntrl}]", "_").trim();
        return safe.substring(0, Math.min(255, safe.length()));
    }

    private User currentUser(Authentication authentication) {
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
    }

    private String action(Authentication auth, RedirectAttributes flash, String section, Runnable operation) {
        return action(auth, flash, section, operation, null);
    }

    private String action(Authentication auth, RedirectAttributes flash, String section, Runnable operation,
                          Long householdId) {
        try {
            operation.run();
            flash.addFlashAttribute("successMessage", "Saved successfully.");
        } catch (IllegalArgumentException exception) {
            flash.addFlashAttribute("errorMessage", exception.getMessage());
        }
        String redirect = "redirect:/planning";
        if (householdId != null) redirect += "?householdId=" + householdId;
        return redirect + "#" + section;
    }

    private BigDecimal annualized(Subscription subscription) {
        BigDecimal multiplier = switch (subscription.getFrequency()) {
            case "WEEKLY" -> BigDecimal.valueOf(52);
            case "MONTHLY" -> BigDecimal.valueOf(12);
            case "QUARTERLY" -> BigDecimal.valueOf(4);
            case "YEARLY" -> BigDecimal.ONE;
            default -> BigDecimal.ZERO;
        };
        return subscription.getAmount().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }
}
