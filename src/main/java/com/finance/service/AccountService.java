package com.finance.service;

import com.finance.model.entity.Account;
import com.finance.model.entity.User;
import com.finance.repository.AccountRepository;
import com.finance.util.FinanceValidation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public List<Account> getUserAccounts(User user) {
        return accountRepository.findByUserOrderByCreatedAtDesc(user);
    }

    @Transactional(readOnly = true)
    public List<Account> getActiveAccounts(User user) {
        return accountRepository.findByUserAndIsActiveTrueOrderByAccountNameAsc(user);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalBalance(User user) {
        BigDecimal total = accountRepository.getTotalBalance(user);
        return total != null ? total : BigDecimal.ZERO;
    }

    @Transactional
    public Account createAccount(User user, String name, String type, BigDecimal balance, String institution) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }

        Account account = new Account();
        account.setUser(user);
        account.setAccountName(FinanceValidation.requireText(name, "Account name", 100));
        account.setAccountType(normalizeAccountType(type));
        account.setBalance(FinanceValidation.requireNonNegativeAmount(
                balance != null ? balance : BigDecimal.ZERO, "Account balance"));
        account.setInstitution(FinanceValidation.optionalText(institution, "Institution", 100));
        account.setIsActive(true);
        return accountRepository.save(account);
    }

    @Transactional(readOnly = true)
    public Optional<Account> getByIdAndUser(Long id, User user) {
        return accountRepository.findByIdAndUser(id, user);
    }

    @Transactional
    public Account updateAccount(User user, Long id, String name, String type, BigDecimal balance,
                                 String institution) {
        if (user == null) {
            throw new IllegalArgumentException("User is required");
        }
        Account account = accountRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        String validatedName = FinanceValidation.requireText(name, "Account name", 100);
        String validatedType = normalizeAccountType(type);
        BigDecimal validatedBalance = FinanceValidation.requireNonNegativeAmount(
                balance != null ? balance : BigDecimal.ZERO, "Account balance");
        String validatedInstitution = FinanceValidation.optionalText(institution, "Institution", 100);
        account.setAccountName(validatedName);
        account.setAccountType(validatedType);
        account.setBalance(validatedBalance);
        account.setInstitution(validatedInstitution);
        return accountRepository.save(account);
    }

    @Transactional
    public void deleteAccount(Long id, User user) {
        accountRepository.findByIdAndUser(id, user).ifPresent(account -> accountRepository.delete(account));
    }

    private String normalizeAccountType(String type) {
        String normalized = FinanceValidation.requireText(
                type != null && !type.isBlank() ? type : "CHECKING", "Account type", 20)
                .toUpperCase(Locale.ROOT);
        if (!List.of("CHECKING", "SAVINGS", "WALLET", "CASH", "CREDIT", "OTHER").contains(normalized)) {
            throw new IllegalArgumentException("Account type is invalid");
        }
        return normalized;
    }
}
