package com.cloudvault.workspace;

import com.cloudvault.notification.EmailDelivery;
import com.cloudvault.notification.EmailMessage;
import com.cloudvault.user.UserAccount;
import com.cloudvault.user.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class WorkspaceEmailNotifier {

    private static final Logger log =
            LoggerFactory.getLogger(WorkspaceEmailNotifier.class);
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, uuuu");

    private final EmailDelivery emailDelivery;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final UserAccountRepository userRepository;

    public WorkspaceEmailNotifier(
            EmailDelivery emailDelivery,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            UserAccountRepository userRepository
    ) {
        this.emailDelivery = emailDelivery;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
    }

    public void requestAssigned(DocumentRequest request, UserAccount assignee) {
        if (assignee == null) {
            return;
        }
        String workspaceName = workspaceName(request.getWorkspaceId());
        sendSafely(new EmailMessage(
                assignee.getEmail(),
                "Document requested: " + request.getTitle(),
                """
                        A document has been requested from you in the "%s" workspace.

                        Request: %s
                        Category: %s
                        Due: %s
                        Instructions: %s

                        Sign in to CloudVault to upload the requested document.
                        """.formatted(
                        workspaceName,
                        request.getTitle(),
                        request.getCategory().name().toLowerCase(),
                        dueDate(request),
                        request.getDescription() == null
                                ? "No additional instructions."
                                : request.getDescription()
                )
        ));
    }

    public void submissionReceived(DocumentRequest request, UserAccount submitter) {
        String workspaceName = workspaceName(request.getWorkspaceId());
        managerRecipients(request.getWorkspaceId()).stream()
                .filter(recipient -> !recipient.getId().equals(submitter.getId()))
                .forEach(recipient -> sendSafely(new EmailMessage(
                        recipient.getEmail(),
                        "Document ready for review: " + request.getTitle(),
                        """
                                %s submitted a document for "%s" in the "%s" workspace.

                                Sign in to CloudVault to download, review, and approve it.
                                """.formatted(
                                submitter.getName(),
                                request.getTitle(),
                                workspaceName
                        )
                )));
    }

    public void requestApproved(DocumentRequest request) {
        if (request.getAssignedTo() == null) {
            return;
        }
        userRepository.findById(request.getAssignedTo()).ifPresent(assignee ->
                sendSafely(new EmailMessage(
                        assignee.getEmail(),
                        "Document approved: " + request.getTitle(),
                        """
                                Your document request "%s" in the "%s" workspace was approved.
                                """.formatted(
                                request.getTitle(),
                                workspaceName(request.getWorkspaceId())
                        )
                ))
        );
    }

    public boolean deadlineReminder(DocumentRequest request, UserAccount assignee) {
        return sendSafely(new EmailMessage(
                assignee.getEmail(),
                "Due soon: " + request.getTitle(),
                """
                        The document request "%s" in the "%s" workspace is due %s.

                        Sign in to CloudVault to upload the requested document.
                        """.formatted(
                        request.getTitle(),
                        workspaceName(request.getWorkspaceId()),
                        dueDate(request)
                )
        ));
    }

    private boolean sendSafely(EmailMessage message) {
        try {
            emailDelivery.send(message);
            return true;
        } catch (RuntimeException exception) {
            log.warn(
                    "Could not deliver CloudVault notification to {}",
                    message.recipient(),
                    exception
            );
            return false;
        }
    }

    private List<UserAccount> managerRecipients(UUID workspaceId) {
        List<UUID> managerIds = membershipRepository
                .findAllByWorkspaceIdOrderByCreatedAtAsc(workspaceId)
                .stream()
                .filter(membership -> membership.getRole() != WorkspaceRole.CLIENT)
                .map(WorkspaceMembership::getUserId)
                .toList();
        return userRepository.findAllById(managerIds);
    }

    private String workspaceName(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .map(Workspace::getName)
                .orElse("CloudVault");
    }

    private String dueDate(DocumentRequest request) {
        return request.getDueDate() == null
                ? "No deadline"
                : DATE_FORMAT.format(request.getDueDate());
    }
}
