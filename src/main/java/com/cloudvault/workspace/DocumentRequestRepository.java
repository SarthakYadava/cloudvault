package com.cloudvault.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRequestRepository extends JpaRepository<DocumentRequest, UUID> {

    List<DocumentRequest> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<DocumentRequest> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    long countByWorkspaceIdAndStatus(
            UUID workspaceId,
            DocumentRequestStatus status
    );
}
