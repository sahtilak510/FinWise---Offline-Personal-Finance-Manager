package com.finance.financeplus.service;

import com.finance.financeplus.model.FinancePlusSettings;
import com.finance.financeplus.repository.FinancePlusSettingsRepository;
import com.finance.model.entity.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Transactional
public class FinancePlusSettingsService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final FinancePlusSettingsRepository settingsRepository;
    private final PasswordEncoder passwordEncoder;

    public FinancePlusSettingsService(FinancePlusSettingsRepository settingsRepository,
                                      PasswordEncoder passwordEncoder) {
        this.settingsRepository = settingsRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public FinancePlusSettings getSettings(User user) {
        return settingsRepository.findByUser(user).orElseGet(() -> {
            FinancePlusSettings settings = new FinancePlusSettings();
            settings.setUser(user);
            return settingsRepository.save(settings);
        });
    }

    public FinancePlusSettings updatePreferences(User user, boolean billRemindersEnabled,
                                                   int billReminderDays, boolean largeExpenseAlertsEnabled,
                                                   BigDecimal largeExpenseThreshold,
                                                   boolean weeklySummaryEnabled,
                                                   boolean monthlySummaryEnabled) {
        FinancePlusSettings settings = getSettings(user);
        settings.setBillRemindersEnabled(billRemindersEnabled);
        settings.setBillReminderDays(Math.max(1, Math.min(30, billReminderDays)));
        settings.setLargeExpenseAlertsEnabled(largeExpenseAlertsEnabled);
        settings.setLargeExpenseThreshold(normalizeThreshold(largeExpenseThreshold));
        settings.setWeeklySummaryEnabled(weeklySummaryEnabled);
        settings.setMonthlySummaryEnabled(monthlySummaryEnabled);
        return settingsRepository.save(settings);
    }

    public FinancePlusSettings updateBackupPreferences(User user, boolean enabled, int hour) {
        FinancePlusSettings settings = getSettings(user);
        settings.setAutoBackupEnabled(enabled);
        settings.setAutomaticBackupHour(Math.max(0, Math.min(23, hour)));
        return settingsRepository.save(settings);
    }

    public void setPasscode(User user, String currentPasscode, String passcode) {
        if (isPasscodeEnabled(user) && !verifyPasscode(user, currentPasscode)) {
            throw new IllegalArgumentException("Current passcode is incorrect");
        }
        if (passcode == null || !passcode.matches("\\d{4,8}")) {
            throw new IllegalArgumentException("Passcode must contain 4 to 8 digits");
        }
        FinancePlusSettings settings = getSettings(user);
        settings.setPasscodeHash(passwordEncoder.encode(passcode));
        settings.setPasscodeEnabled(true);
        settings.setPasscodeUpdatedAt(java.time.LocalDateTime.now());
        settingsRepository.saveAndFlush(settings);
    }

    public void disablePasscode(User user, String currentPasscode) {
        if (isPasscodeEnabled(user) && !verifyPasscode(user, currentPasscode)) {
            throw new IllegalArgumentException("Current passcode is incorrect");
        }
        FinancePlusSettings settings = getSettings(user);
        settings.setPasscodeEnabled(false);
        settings.setPasscodeHash(null);
        settings.setPasscodeUpdatedAt(java.time.LocalDateTime.now());
        settingsRepository.saveAndFlush(settings);
    }

    public boolean isPasscodeEnabled(User user) {
        return Boolean.TRUE.equals(getSettings(user).getPasscodeEnabled())
                && settingsNotBlank(user);
    }

    public boolean verifyPasscode(User user, String passcode) {
        FinancePlusSettings settings = getSettings(user);
        return Boolean.TRUE.equals(settings.getPasscodeEnabled())
                && settings.getPasscodeHash() != null
                && passwordEncoder.matches(passcode == null ? "" : passcode, settings.getPasscodeHash());
    }

    public long unlockExpiresAt() {
        return System.currentTimeMillis() + 30L * 60L * 1000L;
    }

    public String passcodeVersion(User user) {
        FinancePlusSettings settings = getSettings(user);
        return settings.getPasscodeUpdatedAt() == null ? "" : settings.getPasscodeUpdatedAt().toString();
    }

    public String getOrCreateVaultSalt(User user) {
        FinancePlusSettings settings = getSettings(user);
        if (settings.getVaultSalt() == null || settings.getVaultSalt().isBlank()) {
            byte[] salt = new byte[16];
            SECURE_RANDOM.nextBytes(salt);
            settings.setVaultSalt(Base64.getEncoder().encodeToString(salt));
            settingsRepository.save(settings);
        }
        return settings.getVaultSalt();
    }

    private boolean settingsNotBlank(User user) {
        String hash = getSettings(user).getPasscodeHash();
        return hash != null && !hash.isBlank();
    }

    private BigDecimal normalizeThreshold(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return new BigDecimal("1000.00");
        }
        if (value.compareTo(new BigDecimal("1000000")) > 0) {
            return new BigDecimal("1000000.00");
        }
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
