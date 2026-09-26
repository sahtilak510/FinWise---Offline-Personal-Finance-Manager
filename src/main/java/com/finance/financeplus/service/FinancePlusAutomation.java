package com.finance.financeplus.service;

import com.finance.financeplus.model.FinancePlusSettings;
import com.finance.financeplus.repository.FinancePlusSettingsRepository;
import com.finance.model.entity.User;
import com.finance.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Component
public class FinancePlusAutomation {

    private static final Logger log = LoggerFactory.getLogger(FinancePlusAutomation.class);

    private final UserRepository userRepository;
    private final FinancePlusSettingsRepository settingsRepository;
    private final FinancePlusRecurringService recurringService;
    private final FinancePlusNotificationService notificationService;
    private final FinancePlusBackupService backupService;

    public FinancePlusAutomation(UserRepository userRepository,
                                 FinancePlusSettingsRepository settingsRepository,
                                 FinancePlusRecurringService recurringService,
                                 FinancePlusNotificationService notificationService,
                                 FinancePlusBackupService backupService) {
        this.userRepository = userRepository;
        this.settingsRepository = settingsRepository;
        this.recurringService = recurringService;
        this.notificationService = notificationService;
        this.backupService = backupService;
    }

    @Scheduled(cron = "${app.finance-plus.automation-cron:0 10 0 * * *}")
    public void runDailyAutomation() {
        recurringService.processDue(LocalDate.now());
        for (User user : userRepository.findAll()) {
            try {
                notificationService.generateForUser(user);
            } catch (RuntimeException exception) {
                log.error("Unable to generate Finance Plus notifications for user {}", user.getId(), exception);
            }
        }
    }

    @Scheduled(cron = "${app.finance-plus.backup-cron:0 */5 * * * *}")
    @Transactional
    public void runAutomaticBackups() {
        int hour = LocalDateTime.now().getHour();
        for (FinancePlusSettings settings : settingsRepository.findByAutoBackupEnabledTrue()) {
            if (!Integer.valueOf(hour).equals(settings.getAutomaticBackupHour())
                    || !backupService.isVaultUnlocked(settings.getUser())) {
                continue;
            }
            boolean alreadyCreatedThisHour = backupService.getBackups(settings.getUser()).stream()
                    .filter(backup -> "AUTOMATIC".equalsIgnoreCase(backup.getBackupType()))
                    .anyMatch(backup -> backup.getCreatedAt() != null
                            && backup.getCreatedAt().getYear() == LocalDateTime.now().getYear()
                            && backup.getCreatedAt().getMonthValue() == LocalDateTime.now().getMonthValue()
                            && backup.getCreatedAt().getDayOfMonth() == LocalDateTime.now().getDayOfMonth()
                            && backup.getCreatedAt().getHour() == hour);
            if (alreadyCreatedThisHour) {
                continue;
            }
            try {
                backupService.createAutomaticBackup(settings.getUser());
            } catch (RuntimeException exception) {
                log.error("Unable to create automatic Finance Plus backup for user {}", settings.getUser().getId(), exception);
            }
        }
    }
}
