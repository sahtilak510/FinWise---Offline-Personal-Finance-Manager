package com.finance.service;

import com.finance.financeplus.repository.FinancePlusAttachmentRepository;
import com.finance.financeplus.repository.FinancePlusBackupRepository;
import com.finance.financeplus.repository.FinancePlusNotificationRepository;
import com.finance.financeplus.repository.FinancePlusRecurringRepository;
import com.finance.financeplus.repository.FinancePlusSettingsRepository;
import com.finance.model.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminUserDeletionService {
    private final FinancePlusAttachmentRepository attachmentRepository;
    private final FinancePlusBackupRepository backupRepository;
    private final FinancePlusNotificationRepository notificationRepository;
    private final FinancePlusRecurringRepository recurringRepository;
    private final FinancePlusSettingsRepository settingsRepository;
    private final UserService userService;

    public AdminUserDeletionService(FinancePlusAttachmentRepository attachmentRepository,
                                    FinancePlusBackupRepository backupRepository,
                                    FinancePlusNotificationRepository notificationRepository,
                                    FinancePlusRecurringRepository recurringRepository,
                                    FinancePlusSettingsRepository settingsRepository,
                                    UserService userService) {
        this.attachmentRepository = attachmentRepository;
        this.backupRepository = backupRepository;
        this.notificationRepository = notificationRepository;
        this.recurringRepository = recurringRepository;
        this.settingsRepository = settingsRepository;
        this.userService = userService;
    }

    @Transactional
    public void deleteUserAndData(User user) {
        attachmentRepository.deleteByUser(user);
        backupRepository.deleteByUser(user);
        notificationRepository.deleteByUser(user);
        recurringRepository.deleteByUser(user);
        settingsRepository.deleteByUser(user);
        userService.deleteAccountAndData(user);
    }
}
