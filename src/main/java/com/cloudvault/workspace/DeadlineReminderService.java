package com.cloudvault.workspace;

import com.cloudvault.config.CloudVaultProperties;
import com.cloudvault.user.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class DeadlineReminderService {

    private static final Logger log =
            LoggerFactory.getLogger(DeadlineReminderService.class);

    private final DocumentRequestRepository requestRepository;
    private final UserAccountRepository userRepository;
    private final WorkspaceEmailNotifier notifier;
    private final int reminderDays;

    public DeadlineReminderService(
            DocumentRequestRepository requestRepository,
            UserAccountRepository userRepository,
            WorkspaceEmailNotifier notifier,
            CloudVaultProperties properties
    ) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.notifier = notifier;
        this.reminderDays = properties.notifications().deadlineReminderDays();
    }

    @Scheduled(cron = "${cloudvault.notifications.deadline-reminder-cron}")
    @Transactional
    public void sendDueSoonReminders() {
        LocalDate today = LocalDate.now();
        requestRepository
                .findAllByStatusAndDueDateBetweenAndDeadlineReminderSentAtIsNull(
                        DocumentRequestStatus.PENDING,
                        today,
                        today.plusDays(reminderDays)
                )
                .forEach(this::sendReminder);
    }

    private void sendReminder(DocumentRequest request) {
        if (request.getAssignedTo() == null) {
            return;
        }
        userRepository.findById(request.getAssignedTo()).ifPresent(assignee -> {
            try {
                if (notifier.deadlineReminder(request, assignee)) {
                    request.markDeadlineReminderSent();
                    requestRepository.save(request);
                }
            } catch (RuntimeException exception) {
                log.warn(
                        "Could not send deadline reminder for request {}",
                        request.getId(),
                        exception
                );
            }
        });
    }
}
