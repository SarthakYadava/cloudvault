package com.cloudvault.workspace;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceInvitationRepository
        extends JpaRepository<WorkspaceInvitation, UUID> {

    List<WorkspaceInvitation> findAllByWorkspaceIdOrderByCreatedAtDesc(UUID workspaceId);

    Optional<WorkspaceInvitation> findByTokenHash(String tokenHash);

    Optional<WorkspaceInvitation>
    findFirstByWorkspaceIdAndEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(
            UUID workspaceId,
            String email
    );
}
