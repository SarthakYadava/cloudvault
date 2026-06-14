package com.cloudvault.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(length = 255)
    private String filename;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AuditAction action;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected AuditEvent() {
    }

    private AuditEvent(
            UUID id,
            UUID ownerId,
            UUID fileId,
            String filename,
            AuditAction action,
            Instant occurredAt
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.fileId = fileId;
        this.filename = filename;
        this.action = action;
        this.occurredAt = occurredAt;
    }

    public static AuditEvent create(
            UUID ownerId,
            UUID fileId,
            String filename,
            AuditAction action
    ) {
        return new AuditEvent(
                UUID.randomUUID(),
                ownerId,
                fileId,
                filename,
                action,
                Instant.now()
        );
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getFileId() {
        return fileId;
    }

    public String getFilename() {
        return filename;
    }

    public AuditAction getAction() {
        return action;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
