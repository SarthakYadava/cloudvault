package com.cloudvault.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "document_requests")
public class DocumentRequest {

    @Id
    private UUID id;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DocumentRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    protected DocumentRequest() {
    }

    private DocumentRequest(
            UUID id,
            UUID workspaceId,
            String title,
            String description,
            UUID assignedTo,
            UUID createdBy,
            LocalDate dueDate,
            DocumentRequestStatus status,
            Instant createdAt
    ) {
        this.id = id;
        this.workspaceId = workspaceId;
        this.title = title;
        this.description = description;
        this.assignedTo = assignedTo;
        this.createdBy = createdBy;
        this.dueDate = dueDate;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static DocumentRequest create(
            UUID workspaceId,
            String title,
            String description,
            UUID assignedTo,
            UUID createdBy,
            LocalDate dueDate
    ) {
        return new DocumentRequest(
                UUID.randomUUID(),
                workspaceId,
                title,
                description,
                assignedTo,
                createdBy,
                dueDate,
                DocumentRequestStatus.PENDING,
                Instant.now()
        );
    }

    public void markSubmitted() {
        status = DocumentRequestStatus.SUBMITTED;
        submittedAt = Instant.now();
        approvedAt = null;
    }

    public void markApproved() {
        status = DocumentRequestStatus.APPROVED;
        approvedAt = Instant.now();
    }

    public void reopen() {
        status = DocumentRequestStatus.PENDING;
        submittedAt = null;
        approvedAt = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public UUID getAssignedTo() {
        return assignedTo;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public DocumentRequestStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public Instant getApprovedAt() {
        return approvedAt;
    }
}
