package com.cloudvault.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDate;

public interface DocumentRequestRepository extends JpaRepository<DocumentRequest, UUID> {

    List<DocumentRequest> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<DocumentRequest> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    List<DocumentRequest>
    findAllByStatusAndDueDateBetweenAndDeadlineReminderSentAtIsNull(
            DocumentRequestStatus status,
            LocalDate from,
            LocalDate to
    );

    long countByWorkspaceIdAndStatus(
            UUID workspaceId,
            DocumentRequestStatus status
    );

    long countByWorkspaceIdAndStatusAndDueDateBefore(
            UUID workspaceId,
            DocumentRequestStatus status,
            LocalDate date
    );
}
