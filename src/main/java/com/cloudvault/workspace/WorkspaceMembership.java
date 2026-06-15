package com.cloudvault.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspace_memberships")
public class WorkspaceMembership {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkspaceMembership() {
    }

    private WorkspaceMembership(
            UUID id,
            UUID workspaceId,
            UUID userId,
            WorkspaceRole role,
            Instant createdAt
    ) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.createdAt = createdAt;
    }

    public static WorkspaceMembership create(
            UUID workspaceId,
            UUID userId,
            WorkspaceRole role
    ) {
        return new WorkspaceMembership(
                UUID.randomUUID(),
                workspaceId,
                userId,
                role,
                Instant.now()
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
