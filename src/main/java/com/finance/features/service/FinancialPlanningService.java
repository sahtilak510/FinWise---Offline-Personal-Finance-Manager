package com.finance.features.service;

import com.finance.features.model.*;
import com.finance.features.repository.*;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import com.finance.service.AccountService;
import com.finance.util.FinanceValidation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
@Transactional
public class FinancialPlanningService implements FinancialPlanningDataCleaner {
    private final DebtRepository debtRepository;
    private final TrackedAssetRepository assetRepository;
    private final InvestmentHoldingRepository investmentRepository;
    private final TaxEntryRepository taxEntryRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final HouseholdRepository householdRepository;
    private final HouseholdMemberRepository householdMemberRepository;
    private final SharedBudgetRepository sharedBudgetRepository;
    private final HouseholdExpenseRepository householdExpenseRepository;
    private final ExpenseShareRepository expenseShareRepository;
    private final SavingsChallengeRepository challengeRepository;
    private final FinancialDocumentRepository documentRepository;
    private final UserRepository userRepository;
    private final AccountService accountService;

    public FinancialPlanningService(DebtRepository debtRepository, TrackedAssetRepository assetRepository,
                                    InvestmentHoldingRepository investmentRepository, TaxEntryRepository taxEntryRepository,
                                    SubscriptionRepository subscriptionRepository, HouseholdRepository householdRepository,
                                    HouseholdMemberRepository householdMemberRepository, SharedBudgetRepository sharedBudgetRepository,
                                    HouseholdExpenseRepository householdExpenseRepository, ExpenseShareRepository expenseShareRepository,
                                    SavingsChallengeRepository challengeRepository, FinancialDocumentRepository documentRepository,
                                    UserRepository userRepository, AccountService accountService) {
        this.debtRepository = debtRepository;
        this.assetRepository = assetRepository;
        this.investmentRepository = investmentRepository;
        this.taxEntryRepository = taxEntryRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.householdRepository = householdRepository;
        this.householdMemberRepository = householdMemberRepository;
        this.sharedBudgetRepository = sharedBudgetRepository;
        this.householdExpenseRepository = householdExpenseRepository;
        this.expenseShareRepository = expenseShareRepository;
        this.challengeRepository = challengeRepository;
        this.documentRepository = documentRepository;
        this.userRepository = userRepository;
        this.accountService = accountService;
    }

    @Transactional(readOnly = true)
    public List<Debt> debts(User user) { return debtRepository.findByUserOrderByDueDateAsc(user); }

    public Debt addDebt(User user, String name, BigDecimal startingBalance, BigDecimal currentBalance,
                        BigDecimal annualRate, BigDecimal minimumPayment, LocalDate dueDate) {
        Debt debt = new Debt();
        debt.setUser(user);
        debt.setName(text(name, "Debt name", 120));
        debt.setStartingBalance(nonNegative(startingBalance, "Starting balance"));
        debt.setCurrentBalance(nonNegative(currentBalance, "Current balance"));
        debt.setAnnualInterestRate(nonNegative(annualRate, "Annual interest rate").setScale(3, RoundingMode.HALF_UP));
        debt.setMinimumPayment(nonNegative(minimumPayment, "Minimum payment"));
        debt.setDueDate(dueDate);
        return debtRepository.save(debt);
    }

    public Debt updateDebtBalance(User user, Long id, BigDecimal balance) {
        Debt debt = debtRepository.findByIdAndUser(id, user).orElseThrow(() -> missing("Debt"));
        debt.setCurrentBalance(nonNegative(balance, "Current balance"));
        return debtRepository.save(debt);
    }

    public void deleteDebt(User user, Long id) {
        debtRepository.delete(debtRepository.findByIdAndUser(id, user).orElseThrow(() -> missing("Debt")));
    }

    @Transactional(readOnly = true)
    public List<TrackedAsset> assets(User user) { return assetRepository.findByUserOrderByNameAsc(user); }

    public TrackedAsset addAsset(User user, String name, String type, BigDecimal value) {
        TrackedAsset asset = new TrackedAsset();
        asset.setUser(user);
        asset.setName(text(name, "Asset name", 120));
        asset.setAssetType(text(type, "Asset type", 40));
        asset.setValue(nonNegative(value, "Asset value"));
        return assetRepository.save(asset);
    }

    public void deleteAsset(User user, Long id) {
        assetRepository.delete(assetRepository.findByIdAndUser(id, user).orElseThrow(() -> missing("Asset")));
    }

    public void updateAssetValue(User user, Long id, BigDecimal value) {
        TrackedAsset asset = assetRepository.findByIdAndUser(id, user).orElseThrow(() -> missing("Asset"));
        asset.setValue(nonNegative(value, "Asset value"));
        assetRepository.save(asset);
    }

    @Transactional(readOnly = true)
    public List<InvestmentHolding> investments(User user) { return investmentRepository.findByUserOrderByNameAsc(user); }

    public InvestmentHolding addInvestment(User user, String name, String ticker, String type,
                                           BigDecimal units, BigDecimal costBasis, BigDecimal currentValue) {
        InvestmentHolding holding = new InvestmentHolding();
        holding.setUser(user);
        holding.setName(text(name, "Investment name", 120));
        holding.setTicker(optionalText(ticker, 20));
        holding.setInvestmentType(text(type, "Investment type", 40));
        holding.setUnits(nonNegativeUnits(units));
        holding.setCostBasis(nonNegative(costBasis, "Cost basis"));
        holding.setCurrentValue(nonNegative(currentValue, "Current value"));
        return investmentRepository.save(holding);
    }

    public void deleteInvestment(User user, Long id) {
        investmentRepository.delete(investmentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> missing("Investment")));
    }

    public void updateInvestment(User user, Long id, BigDecimal units, BigDecimal costBasis, BigDecimal currentValue) {
        InvestmentHolding holding = investmentRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> missing("Investment"));
        holding.setUnits(nonNegativeUnits(units));
        holding.setCostBasis(nonNegative(costBasis, "Cost basis"));
        holding.setCurrentValue(nonNegative(currentValue, "Current value"));
        investmentRepository.save(holding);
    }

    @Transactional(readOnly = true)
    public List<TaxEntry> taxEntries(User user) { return taxEntryRepository.findByUserOrderByEntryDateDesc(user); }

    public TaxEntry addTaxEntry(User user, String description, String category, String entryType,
                                BigDecimal amount, LocalDate date) {
        String type = enumValue(entryType, "Entry type", List.of("INCOME", "DEDUCTION"));
        TaxEntry entry = new TaxEntry();
        entry.setUser(user);
        entry.setDescription(text(description, "Description", 120));
        entry.setCategory(text(category, "Category", 40));
        entry.setEntryType(type);
        entry.setAmount(nonNegative(amount, "Amount"));
        entry.setEntryDate(Objects.requireNonNull(date, "Date is required"));
        return taxEntryRepository.save(entry);
    }

    public void deleteTaxEntry(User user, Long id) {
        taxEntryRepository.delete(taxEntryRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> missing("Tax entry")));
    }

    @Transactional(readOnly = true)
    public List<Subscription> subscriptions(User user) { return subscriptionRepository.findByUserOrderByNextRenewalAsc(user); }

    public Subscription addSubscription(User user, String name, BigDecimal amount, String frequency, LocalDate renewal) {
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setName(text(name, "Subscription name", 120));
        subscription.setAmount(positive(amount, "Subscription amount"));
        subscription.setFrequency(enumValue(frequency, "Frequency", List.of("WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY")));
        subscription.setNextRenewal(Objects.requireNonNull(renewal, "Next renewal is required"));
        subscription.setActive(true);
        return subscriptionRepository.save(subscription);
    }

    public void setSubscriptionActive(User user, Long id, boolean active) {
        Subscription subscription = subscriptionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> missing("Subscription"));
        subscription.setActive(active);
        subscriptionRepository.save(subscription);
    }

    public void updateSubscription(User user, Long id, BigDecimal amount, String frequency, LocalDate renewal) {
        Subscription subscription = subscriptionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> missing("Subscription"));
        subscription.setAmount(positive(amount, "Subscription amount"));
        subscription.setFrequency(enumValue(frequency, "Frequency",
                List.of("WEEKLY", "MONTHLY", "QUARTERLY", "YEARLY")));
        subscription.setNextRenewal(Objects.requireNonNull(renewal, "Next renewal is required"));
        subscriptionRepository.save(subscription);
    }

    public void deleteSubscription(User user, Long id) {
        subscriptionRepository.delete(subscriptionRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> missing("Subscription")));
    }

    @Transactional(readOnly = true)
    public List<Household> households(User user) {
        Map<Long, Household> households = new LinkedHashMap<>();
        householdRepository.findByOwnerOrderByNameAsc(user).forEach(h -> households.put(h.getId(), h));
        householdMemberRepository.findByUserOrderByHouseholdNameAsc(user)
                .forEach(member -> households.put(member.getHousehold().getId(), member.getHousehold()));
        return List.copyOf(households.values());
    }

    public Household createHousehold(User owner, String name) {
        Household household = new Household();
        household.setOwner(owner);
        household.setName(text(name, "Household name", 100));
        return householdRepository.save(household);
    }

    public HouseholdMember addHouseholdMember(User owner, Long householdId, String username) {
        Household household = householdRepository.findByIdAndOwner(householdId, owner)
                .orElseThrow(() -> missing("Household"));
        User member = userRepository.findByUsername(text(username, "Username", 100))
                .orElseThrow(() -> new IllegalArgumentException("FinWise user not found"));
        if (member.getId().equals(owner.getId())) {
            throw new IllegalArgumentException("The household owner is already a member");
        }
        if (householdMemberRepository.findByHouseholdAndUser(household, member).isPresent()) {
            throw new IllegalArgumentException("This user is already a household member");
        }
        HouseholdMember householdMember = new HouseholdMember();
        householdMember.setHousehold(household);
        householdMember.setUser(member);
        return householdMemberRepository.save(householdMember);
    }

    public void removeHouseholdMember(User owner, Long membershipId) {
        HouseholdMember member = householdMemberRepository.findByIdAndHouseholdOwner(membershipId, owner)
                .orElseThrow(() -> missing("Household member"));
        householdMemberRepository.delete(member);
    }

    @Transactional(readOnly = true)
    public List<User> householdMembers(User user, Long householdId) {
        Household household = accessibleHousehold(user, householdId);
        List<User> members = new ArrayList<>();
        members.add(household.getOwner());
        householdMemberRepository.findByHouseholdOrderByUserUsernameAsc(household)
                .stream().map(HouseholdMember::getUser).forEach(members::add);
        return List.copyOf(members);
    }

    @Transactional(readOnly = true)
    public List<HouseholdMember> householdMemberships(User user, Long householdId) {
        Household household = accessibleHousehold(user, householdId);
        if (!household.getOwner().getId().equals(user.getId())) return List.of();
        return householdMemberRepository.findByHouseholdOrderByUserUsernameAsc(household);
    }

    @Transactional(readOnly = true)
    public List<SharedBudget> sharedBudgets(User user, Long householdId) {
        return sharedBudgetRepository.findByHouseholdOrderByCreatedAtDesc(accessibleHousehold(user, householdId));
    }

    public SharedBudget addSharedBudget(User user, Long householdId, String name, BigDecimal limit) {
        SharedBudget budget = new SharedBudget();
        budget.setHousehold(accessibleHousehold(user, householdId));
        budget.setName(text(name, "Budget name", 100));
        budget.setLimitAmount(positive(limit, "Budget limit"));
        budget.setSpentAmount(BigDecimal.ZERO);
        return sharedBudgetRepository.save(budget);
    }

    @Transactional(readOnly = true)
    public List<HouseholdExpense> householdExpenses(User user, Long householdId) {
        return householdExpenseRepository.findByHouseholdOrderByExpenseDateDesc(accessibleHousehold(user, householdId));
    }

    @Transactional(readOnly = true)
    public List<ExpenseShare> expenseShares(User user, Long householdId, Long expenseId) {
        Household household = accessibleHousehold(user, householdId);
        HouseholdExpense expense = householdExpenseRepository.findByIdAndHousehold(expenseId, household)
                .orElseThrow(() -> missing("Shared expense"));
        return expenseShareRepository.findByExpenseOrderByUserUsernameAsc(expense);
    }

    public HouseholdExpense addHouseholdExpense(User user, Long householdId, Long budgetId,
                                                String description, BigDecimal amount, LocalDate date) {
        Household household = accessibleHousehold(user, householdId);
        BigDecimal validatedAmount = positive(amount, "Expense amount");
        SharedBudget budget = null;
        if (budgetId != null) {
            budget = sharedBudgetRepository.findById(budgetId)
                    .filter(candidate -> candidate.getHousehold().getId().equals(household.getId()))
                    .orElseThrow(() -> missing("Shared budget"));
        }

        HouseholdExpense expense = new HouseholdExpense();
        expense.setHousehold(household);
        expense.setPaidBy(user);
        expense.setDescription(text(description, "Expense description", 120));
        expense.setAmount(validatedAmount);
        expense.setExpenseDate(Objects.requireNonNull(date, "Expense date is required"));
        expense = householdExpenseRepository.save(expense);

        List<User> members = householdMembers(user, householdId);
        BigDecimal share = validatedAmount.divide(BigDecimal.valueOf(members.size()), 2, RoundingMode.DOWN);
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < members.size(); index++) {
            BigDecimal amountForMember = index == members.size() - 1
                    ? validatedAmount.subtract(allocated) : share;
            ExpenseShare expenseShare = new ExpenseShare();
            expenseShare.setExpense(expense);
            expenseShare.setUser(members.get(index));
            expenseShare.setAmount(amountForMember);
            expenseShareRepository.save(expenseShare);
            allocated = allocated.add(amountForMember);
        }
        if (budget != null) {
            budget.setSpentAmount(budget.getSpentAmount().add(validatedAmount));
            sharedBudgetRepository.save(budget);
        }
        return expense;
    }

    private Household accessibleHousehold(User user, Long householdId) {
        Household household = householdRepository.findById(householdId)
                .orElseThrow(() -> missing("Household"));
        if (household.getOwner().getId().equals(user.getId())
                || householdMemberRepository.findByHouseholdAndUser(household, user).isPresent()) {
            return household;
        }
        throw new IllegalArgumentException("Household not found");
    }

    @Transactional(readOnly = true)
    public List<SavingsChallenge> challenges(User user) { return challengeRepository.findByUserOrderByTargetDateAsc(user); }

    public SavingsChallenge addChallenge(User user, String name, BigDecimal target, LocalDate targetDate, String cadence) {
        SavingsChallenge challenge = new SavingsChallenge();
        challenge.setUser(user);
        challenge.setName(text(name, "Challenge name", 120));
        challenge.setTargetAmount(positive(target, "Target amount"));
        challenge.setSavedAmount(BigDecimal.ZERO);
        challenge.setTargetDate(Objects.requireNonNull(targetDate, "Target date is required"));
        challenge.setCadence(enumValue(cadence, "Cadence", List.of("WEEKLY", "MONTHLY")));
        return challengeRepository.save(challenge);
    }

    public void updateChallengeProgress(User user, Long id, BigDecimal saved) {
        SavingsChallenge challenge = challengeRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> missing("Savings challenge"));
        challenge.setSavedAmount(nonNegative(saved, "Saved amount"));
        challengeRepository.save(challenge);
    }

    public void deleteChallenge(User user, Long id) {
        challengeRepository.delete(challengeRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> missing("Savings challenge")));
    }

    @Transactional(readOnly = true)
    public List<FinancialDocument> documents(User user) { return documentRepository.findByUserOrderByCreatedAtDesc(user); }

    public FinancialDocument addDocument(User user, String filename, String contentType, byte[] data) {
        if (data == null || data.length == 0 || data.length > 5 * 1024 * 1024) {
            throw new IllegalArgumentException("Document must be between 1 byte and 5 MB");
        }
        FinancialDocument document = new FinancialDocument();
        document.setUser(user);
        document.setFilename(text(filename, "Filename", 255));
        document.setContentType(text(contentType, "Content type", 100));
        document.setContentLength(data.length);
        document.setData(data);
        return documentRepository.save(document);
    }

    @Transactional(readOnly = true)
    public FinancialDocument document(User user, Long id) {
        return documentRepository.findByIdAndUser(id, user).orElseThrow(() -> missing("Document"));
    }

    public void deleteDocument(User user, Long id) {
        documentRepository.delete(document(user, id));
    }

    @Transactional(readOnly = true)
    public BigDecimal accountBalance(User user) { return accountService.getTotalBalance(user); }

    public BigDecimal netWorth(User user) {
        BigDecimal trackedAssets = assets(user).stream().map(TrackedAsset::getValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal investments = investments(user).stream().map(InvestmentHolding::getCurrentValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal debts = debts(user).stream().map(Debt::getCurrentBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
        return accountBalance(user).add(trackedAssets).add(investments).subtract(debts);
    }

    @Override
    public void deleteUserData(User user) {
        debtRepository.deleteAll(debts(user));
        assetRepository.deleteAll(assets(user));
        investmentRepository.deleteAll(investments(user));
        taxEntryRepository.deleteAll(taxEntries(user));
        subscriptionRepository.deleteAll(subscriptions(user));
        challengeRepository.deleteAll(challenges(user));
        documentRepository.deleteAll(documents(user));

        for (Household household : householdRepository.findByOwnerOrderByNameAsc(user)) {
            List<HouseholdExpense> expenses = householdExpenseRepository
                    .findByHouseholdOrderByExpenseDateDesc(household);
            for (HouseholdExpense expense : expenses) {
                expenseShareRepository.deleteAll(expenseShareRepository.findByExpenseOrderByUserUsernameAsc(expense));
            }
            householdExpenseRepository.deleteAll(expenses);
            sharedBudgetRepository.deleteAll(sharedBudgetRepository.findByHouseholdOrderByCreatedAtDesc(household));
            householdMemberRepository.deleteAll(
                    householdMemberRepository.findByHouseholdOrderByUserUsernameAsc(household));
            householdRepository.delete(household);
        }

        for (HouseholdExpense expense : householdExpenseRepository.findByPaidBy(user)) {
            expense.setPaidBy(null);
            householdExpenseRepository.save(expense);
        }
        expenseShareRepository.deleteAll(expenseShareRepository.findByUser(user));
        householdMemberRepository.deleteAll(householdMemberRepository.findByUserOrderByHouseholdNameAsc(user));
    }

    public List<DebtEstimate> payoffEstimates(User user) {
        return debts(user).stream().map(debt -> estimate(debt)).toList();
    }

    private DebtEstimate estimate(Debt debt) {
        BigDecimal balance = debt.getCurrentBalance();
        BigDecimal monthlyRate = debt.getAnnualInterestRate().divide(BigDecimal.valueOf(1200), 10, RoundingMode.HALF_UP);
        if (balance.signum() == 0) return new DebtEstimate(debt, 0, BigDecimal.ZERO);
        if (debt.getMinimumPayment().signum() <= 0
                || (monthlyRate.signum() > 0 && debt.getMinimumPayment().compareTo(balance.multiply(monthlyRate)) <= 0)) {
            return new DebtEstimate(debt, -1, BigDecimal.ZERO);
        }
        int months = 0;
        BigDecimal interestTotal = BigDecimal.ZERO;
        while (balance.signum() > 0 && months < 1200) {
            BigDecimal interest = balance.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            interestTotal = interestTotal.add(interest);
            balance = balance.add(interest).subtract(debt.getMinimumPayment()).max(BigDecimal.ZERO);
            months++;
        }
        return new DebtEstimate(debt, balance.signum() == 0 ? months : -1, interestTotal);
    }

    public record DebtEstimate(Debt debt, int months, BigDecimal estimatedInterest) {}

    private BigDecimal positive(BigDecimal amount, String label) {
        BigDecimal validated = nonNegative(amount, label);
        if (validated.signum() <= 0) throw new IllegalArgumentException(label + " must be greater than zero");
        return validated;
    }

    private BigDecimal nonNegative(BigDecimal amount, String label) {
        return FinanceValidation.requireNonNegativeAmount(amount, label);
    }

    private BigDecimal nonNegativeUnits(BigDecimal amount) {
        if (amount == null || amount.signum() < 0) {
            throw new IllegalArgumentException("Units cannot be negative");
        }
        FinanceValidation.requireNonNegativeAmount(amount, "Units");
        return amount.setScale(6, RoundingMode.HALF_UP);
    }

    private String text(String value, String label, int maxLength) {
        return FinanceValidation.requireText(value, label, maxLength);
    }

    private String optionalText(String value, int maxLength) {
        return value == null || value.isBlank() ? null : FinanceValidation.optionalText(value, "Value", maxLength);
    }

    private String enumValue(String value, String label, List<String> allowed) {
        String normalized = text(value, label, 20).toUpperCase(Locale.ROOT);
        if (!allowed.contains(normalized)) throw new IllegalArgumentException(label + " is invalid");
        return normalized;
    }

    private IllegalArgumentException missing(String type) {
        return new IllegalArgumentException(type + " not found");
    }
}
