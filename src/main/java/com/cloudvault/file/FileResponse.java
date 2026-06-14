package com.cloudvault.file;

import java.time.Instant;
import java.util.UUID;

public record FileResponse(
        UUID id,
        String originalName,
        String contentType,
        long sizeBytes,
        FileStatus status,
        Instant uploadedAt
) {

    static FileResponse from(StoredFile file) {
        return new FileResponse(
                file.getId(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getStatus(),
                file.getUploadedAt()
        );
    }
}
