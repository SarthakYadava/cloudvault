package com.cloudvault.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceMembershipRepository
        extends JpaRepository<WorkspaceMembership, UUID> {

    List<WorkspaceMembership> findAllByUserIdOrderByCreatedAtDesc(UUID userId);

    List<WorkspaceMembership> findAllByWorkspaceIdOrderByCreatedAtAsc(UUID workspaceId);

    Optional<WorkspaceMembership> findByWorkspaceIdAndUserId(
            UUID workspaceId,
            UUID userId
    );

    boolean existsByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    long countByWorkspaceId(UUID workspaceId);
}
