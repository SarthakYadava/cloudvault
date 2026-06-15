package com.cloudvault.workspace;

import com.cloudvault.user.UserAccount;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record DocumentRequestResponse(
        UUID id,
        UUID workspaceId,
        String title,
        String description,
        UUID assignedTo,
        String assigneeName,
        String assigneeEmail,
        LocalDate dueDate,
        DocumentRequestStatus status,
        Instant createdAt,
        Instant submittedAt,
        Instant approvedAt
) {

    static DocumentRequestResponse from(
            DocumentRequest request,
            UserAccount assignee
    ) {
        return new DocumentRequestResponse(
                request.getId(),
                request.getWorkspaceId(),
                request.getTitle(),
                request.getDescription(),
                request.getAssignedTo(),
                assignee == null ? null : assignee.getName(),
                assignee == null ? null : assignee.getEmail(),
                request.getDueDate(),
                request.getStatus(),
                request.getCreatedAt(),
                request.getSubmittedAt(),
                request.getApprovedAt()
        );
    }
}
