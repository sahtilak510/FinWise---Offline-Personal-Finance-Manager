package com.finance.controller;

import com.finance.model.dto.ExtractedTransactionDTO;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.CategoryEngine;
import com.finance.service.DocumentScanService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scan &amp; Fill web layer (separation of concerns: HTTP only).
 *
 * <p>Depends on the {@link DocumentScanService} interface, not on the concrete
 * implementation (polymorphism) — Spring injects {@code LocalDocumentScanService}.
 * All routes require authentication via the existing Spring Security setup.</p>
 */
@Controller
@RequestMapping("/scan")
@RequiredArgsConstructor
public class ScanController {

    private static final List<String> PAYMENT_METHODS = List.of(
            "UPI", "Cash", "Card", "Net Banking", "Wallet", "Cheque", "Other");

    private final DocumentScanService documentScanService;
    private final CategoryEngine categoryEngine;
    private final UserRepository userRepository;

    /** Renders the Scan &amp; Fill page (Dashboard → Scan &amp; Fill). */
    @GetMapping
    public String scanPage(Authentication authentication, Model model) {
        User user = currentUser(authentication);
        model.addAttribute("user", user);
        model.addAttribute("categories", categoryEngine.getSupportedCategories());
        model.addAttribute("paymentMethods", PAYMENT_METHODS);
        model.addAttribute("today", LocalDate.now().toString());
        return "scan-fill";
    }

    /**
     * AJAX endpoint used by the "Scan &amp; Extract" button. Returns JSON so the
     * form can be auto-filled without a page reload. Never throws: failures are
     * reported as {@code {success:false, message:...}}.
     */
    @PostMapping("/extract")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> extract(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) {
        Map<String, Object> response = new HashMap<>();
        try {
            User user = currentUser(authentication);
            List<ExtractedTransactionDTO> transactions = documentScanService.scanRows(file, user);
            ExtractedTransactionDTO dto = transactions.get(0);
            response.put("success", true);
            response.put("data", dto);
            response.put("transactions", transactions);
            if (!dto.getWarnings().isEmpty()) {
                response.put("message", String.join(" ", dto.getWarnings()));
            }
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.ok(response);
        } catch (Throwable t) {
            // Includes java.lang.Error from native layers: always answer JSON
            // so the page can show the message instead of a failed request.
            response.put("success", false);
            response.put("message", "Unable to extract transaction details. Please enter the details manually.");
            return ResponseEntity.ok(response);
        }
    }

    /**
     * Saves the user-reviewed transaction through the existing
     * expense/income services. Every field remains editable up to this point.
     */
    @PostMapping("/save")
    public String save(@RequestParam String merchant,
                       @RequestParam String amount,
                       @RequestParam String date,
                       @RequestParam(required = false, defaultValue = "Other") String paymentMethod,
                       @RequestParam(required = false, defaultValue = "EXPENSE") String transactionType,
                       @RequestParam(required = false, defaultValue = "Other") String category,
                       Authentication authentication,
                       RedirectAttributes redirectAttributes) {
        try {
            User user = currentUser(authentication);

            BigDecimal parsedAmount;
            try {
                parsedAmount = new BigDecimal(amount.trim().replace(",", ""));
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid amount. Please enter a valid number.");
            }
            LocalDate parsedDate;
            try {
                parsedDate = LocalDate.parse(date.trim());
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid date. Please choose a valid date.");
            }

            ExtractedTransactionDTO dto = new ExtractedTransactionDTO();
            dto.setMerchant(merchant == null ? "" : merchant.trim());
            dto.setDescription(dto.getMerchant());
            dto.setAmount(parsedAmount);
            dto.setDate(parsedDate);
            dto.setPaymentMethod(paymentMethod);
            dto.setTransactionType(transactionType);
            dto.setCategory(category);

            String savedType = documentScanService.saveTransaction(dto, user);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Transaction saved successfully from Scan & Fill!");

            if ("INCOME".equalsIgnoreCase(savedType)) {
                return "redirect:/income";
            }
            return "redirect:/expenses";
        } catch (IllegalArgumentException | IllegalStateException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/scan";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Could not save the transaction. Please check the details and try again.");
            return "redirect:/scan";
        }
    }

    private User currentUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("Please log in to use Scan & Fill.");
        }
        return userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found."));
    }
}
