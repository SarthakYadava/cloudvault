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
@Table(name = "workspace_invitations")
public class WorkspaceInvitation {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Column(nullable = false, length = 254)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceRole role;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    protected WorkspaceInvitation() {
    }

    private WorkspaceInvitation(
            UUID id,
            UUID workspaceId,
            UUID invitedBy,
            String email,
            WorkspaceRole role,
            String tokenHash,
            Instant createdAt,
            Instant expiresAt
    ) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.invitedBy = invitedBy;
        this.email = email;
        this.role = role;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public static WorkspaceInvitation create(
            UUID workspaceId,
            UUID invitedBy,
            String email,
            WorkspaceRole role,
            String tokenHash,
            Instant expiresAt
    ) {
        return new WorkspaceInvitation(
                UUID.randomUUID(),
                workspaceId,
                invitedBy,
                email,
                role,
                tokenHash,
                Instant.now(),
                expiresAt
        );
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isAccepted() {
        return acceptedAt != null;
    }

    public void accept() {
        acceptedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getInvitedBy() {
        return invitedBy;
    }

    public String getEmail() {
        return email;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
