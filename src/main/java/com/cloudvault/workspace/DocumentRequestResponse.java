package com.cloudvault.workspace;

import com.cloudvault.file.StoredFile;
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
        UUID submittedFileId,
        String submittedFileName,
        Long submittedFileSizeBytes,
        String submittedByName,
        Instant createdAt,
        Instant submittedAt,
        Instant approvedAt
) {

    static DocumentRequestResponse from(
            DocumentRequest request,
            UserAccount assignee,
            StoredFile submission,
            UserAccount submittedBy
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
                submission == null ? null : submission.getId(),
                submission == null ? null : submission.getOriginalName(),
                submission == null ? null : submission.getSizeBytes(),
                submittedBy == null ? null : submittedBy.getName(),
                request.getCreatedAt(),
                request.getSubmittedAt(),
                request.getApprovedAt()
        );
    }
}
