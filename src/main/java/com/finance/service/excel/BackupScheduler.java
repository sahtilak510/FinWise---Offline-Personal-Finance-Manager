package com.finance.service.excel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.backup.enabled", havingValue = "true", matchIfMissing = true)
public class BackupScheduler {

    private static final Logger log = LoggerFactory.getLogger(BackupScheduler.class);

    private final BackupRestoreService backupRestoreService;

    public BackupScheduler(BackupRestoreService backupRestoreService) {
        this.backupRestoreService = backupRestoreService;
    }

    @Scheduled(cron = "${app.backup.cron:0 0 2 * * *}")
    public void createScheduledBackup() {
        try {
            backupRestoreService.createBackup("SYSTEM");
        } catch (RuntimeException exception) {
            log.error("Scheduled encrypted backup failed", exception);
        }
    }
}
