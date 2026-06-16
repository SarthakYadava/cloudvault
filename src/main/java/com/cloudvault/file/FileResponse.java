package com.cloudvault.file;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record FileResponse(
        UUID id,
        String originalName,
        String contentType,
        long sizeBytes,
        FileStatus status,
        Instant uploadedAt,
        String folder,
        Set<String> tags
) {

    static FileResponse from(StoredFile file) {
        return new FileResponse(
                file.getId(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                file.getStatus(),
                file.getUploadedAt(),
                file.getFolder(),
                file.getTags()
        );
    }
}
