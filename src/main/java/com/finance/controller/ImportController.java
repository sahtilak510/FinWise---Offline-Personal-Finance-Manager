package com.finance.controller;

import com.finance.model.entity.Expense;
import com.finance.model.entity.ImportedTransaction;
import com.finance.model.entity.Income;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.ExpenseService;
import com.finance.service.ImportCommitService;
import com.finance.service.ImportFileService;
import com.finance.service.IncomeService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/import")
@RequiredArgsConstructor
public class ImportController {

    private static final String IMPORT_INCOME_SOURCE = "IMPORT";
    private static final String IMPORT_EXPENSE_METHOD = "Imported";

    private final ImportFileService importService;
    private final ImportCommitService importCommitService;
    private final IncomeService incomeService;
    private final ExpenseService expenseService;
    private final UserRepository userRepository;

    @GetMapping
    public String importForm(Authentication authentication, Model model) {
        if (authentication != null) {
            userRepository.findByUsername(authentication.getName()).ifPresent(u -> model.addAttribute("user", u));
        }
        return "import";
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file,
                             Authentication authentication,
                             RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            if (file.isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", "No file selected");
                return "redirect:/import";
            }

            List<ImportedTransaction> records = importService.processFile(file, user);
            redirectAttributes.addFlashAttribute("successMessage", "File processed successfully! " +
                    records.size() + " records found.");
            redirectAttributes.addFlashAttribute("importedRecords", records);
            return "redirect:/import/preview";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error processing file: " + e.getMessage());
            return "redirect:/import";
        }
    }

    @GetMapping("/preview")
    public String preview(Model model, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<ImportedTransaction> records = new ArrayList<>(importService.getImportedTransactions(user).stream()
                .filter(this::isVisibleImportRecord)
                .toList());
        List<ImportedTransaction> pendingRecords = new ArrayList<>(importService.getPendingImportedTransactions(user).stream()
                .filter(this::isVisibleImportRecord)
                .toList());
        model.addAttribute("user", user);
        model.addAttribute("records", records);
        model.addAttribute("hasPendingRecords", !pendingRecords.isEmpty());
        model.addAttribute("transactionTypes", new String[]{"INCOME", "EXPENSE"});
        return "import-preview";
    }

    @GetMapping("/history")
    public String importHistory(Authentication authentication, Model model) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        List<Income> importedIncomes = new ArrayList<>(incomeService.getUserIncome(user).stream()
                .filter(income -> IMPORT_INCOME_SOURCE.equalsIgnoreCase(income.getIncomeSource()))
                .toList());
        List<Expense> importedExpenses = new ArrayList<>(expenseService.getUserExpenses(user).stream()
                .filter(expense -> IMPORT_EXPENSE_METHOD.equalsIgnoreCase(expense.getPaymentMethod()))
                .toList());
        model.addAttribute("user", user);
        model.addAttribute("importedIncomes", importedIncomes);
        model.addAttribute("importedExpenses", importedExpenses);
        model.addAttribute("importedIncomeCount", importedIncomes.size());
        model.addAttribute("importedExpenseCount", importedExpenses.size());
        return "import-history";
    }

    @PostMapping("/validate")
    public String validateRecords(@RequestParam(value = "validIds", required = false) Long[] validIds,
                                  @RequestParam(value = "editIds", required = false) Long[] editIds,
                                  @RequestParam(value = "action", required = false) String action,
                                  @RequestParam Map<String, String> formFields,
                                  Authentication authentication,
                                  RedirectAttributes redirectAttributes) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if ("cancel".equalsIgnoreCase(action)) {
            return "redirect:/import";
        }

        if (editIds != null && editIds.length > 0) {
            return "redirect:/import/edit/" + editIds[0];
        }

        if (validIds == null || validIds.length == 0) {
            redirectAttributes.addFlashAttribute("errorMessage", "Select at least one record to import.");
            return "redirect:/import/preview";
        }

        try {
            List<Long> selected = Arrays.asList(validIds);
            ImportCommitService.ImportCommitResult result =
                    importCommitService.commit(selected, user, collectEdits(selected, formFields));

            String message = resultMessage(result);
            if (result.importedCount() == 0 && !result.messages().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage", message);
            } else {
                redirectAttributes.addFlashAttribute("successMessage", message);
            }
            return "redirect:/import/preview";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error importing records: " + e.getMessage());
            return "redirect:/import/preview";
        }
    }

    private Map<Long, ImportCommitService.ImportEdits> collectEdits(List<Long> selected,
                                                                    Map<String, String> formFields) {
        Map<Long, ImportCommitService.ImportEdits> edits = new HashMap<>();
        for (Long id : selected) {
            String description = formFields.get("description_" + id);
            String transactionType = formFields.get("type_" + id);
            String category = formFields.get("category_" + id);
            if (isBlank(description) && isBlank(transactionType) && isBlank(category)) {
                continue;
            }
            edits.put(id, new ImportCommitService.ImportEdits(description, transactionType, category));
        }
        return edits;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String resultMessage(ImportCommitService.ImportCommitResult result) {
        StringBuilder message = new StringBuilder();
        if (result.importedCount() > 0) {
            message.append("Imported ").append(result.importedCount())
                    .append(result.importedCount() == 1 ? " record" : " records")
                    .append(" into your income and expenses.");
        } else {
            message.append("No records were imported.");
        }
        if (!result.messages().isEmpty()) {
            message.append(" ").append(String.join(" ", result.messages()));
        }
        return message.toString();
    }

    @GetMapping("/edit/{id}")
    public String editForm(@PathVariable Long id, Model model, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        ImportedTransaction record = importService.getImportedTransactionById(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Record not found"));

        model.addAttribute("user", user);
        model.addAttribute("record", record);
        model.addAttribute("transactionTypes", new String[]{"INCOME", "EXPENSE"});
        return "import-edit";
    }

    @PostMapping("/update/{id}")
    public String updateRecord(@PathVariable Long id,
                               @RequestParam String description,
                               @RequestParam BigDecimal amount,
                               @RequestParam String transactionDate,
                               @RequestParam String transactionType,
                               @RequestParam String category,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            importService.updateRecord(id, user, description, amount, LocalDate.parse(transactionDate),
                    transactionType, category);
            redirectAttributes.addFlashAttribute("successMessage", "Record updated successfully!");
        return "redirect:/import/preview";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error updating record: " + e.getMessage());
            return "redirect:/import/edit/" + id;
        }
    }

    @GetMapping("/image/{id}")
    public ResponseEntity<Resource> previewImage(@PathVariable Long id, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        Path imagePath = importService.getImportedImagePath(id, user).orElse(null);
        if (imagePath == null) {
            return ResponseEntity.notFound().build();
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaTypeFactory.getMediaType(imagePath.getFileName().toString())
                    .orElse(MediaType.APPLICATION_OCTET_STREAM));
            headers.setContentLength(Files.size(imagePath));
            headers.setCacheControl("no-store");
            return ResponseEntity.ok().headers(headers).body(new FileSystemResource(imagePath));
        } catch (IOException exception) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/delete/{id}")
    public String deleteRecord(@PathVariable Long id, RedirectAttributes redirectAttributes,
                               Authentication authentication) {
        try {
            User user = userRepository.findByUsername(authentication.getName())
                    .orElseThrow(() -> new RuntimeException("User not found"));
            importService.deleteImportedTransaction(id, user);
            redirectAttributes.addFlashAttribute("successMessage", "Record deleted successfully!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Error deleting record: " + e.getMessage());
        }
        return "redirect:/import/preview";
    }

    private boolean isVisibleImportRecord(ImportedTransaction record) {
        return record != null && !"DUPLICATE".equalsIgnoreCase(record.getImportStatus());
    }
}