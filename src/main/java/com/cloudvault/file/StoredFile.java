package com.cloudvault.file;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_files")
public class StoredFile {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FileStatus status;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    protected StoredFile() {
    }

    private StoredFile(
            UUID id,
            UUID ownerId,
            String originalName,
            String objectKey,
            String contentType,
            long sizeBytes,
            FileStatus status,
            Instant uploadedAt
    ) {
        this.id = id;
        this.ownerId = ownerId;
        this.originalName = originalName;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.status = status;
        this.uploadedAt = uploadedAt;
    }

    public static StoredFile createAvailable(
            UUID ownerId,
            String originalName,
            String objectKey,
            String contentType,
            long sizeBytes
    ) {
        return new StoredFile(
                UUID.randomUUID(),
                ownerId,
                originalName,
                objectKey,
                contentType,
                sizeBytes,
                FileStatus.AVAILABLE,
                Instant.now()
        );
    }

    public static StoredFile createPending(
            UUID ownerId,
            String originalName,
            String objectKey,
            String contentType,
            long sizeBytes
    ) {
        return new StoredFile(
                UUID.randomUUID(),
                ownerId,
                originalName,
                objectKey,
                contentType,
                sizeBytes,
                FileStatus.PENDING,
                Instant.now()
        );
    }

    public void markAvailable() {
        this.status = FileStatus.AVAILABLE;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOriginalName() {
        return originalName;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public FileStatus getStatus() {
        return status;
    }

    public Instant getUploadedAt() {
        return uploadedAt;
    }
}
