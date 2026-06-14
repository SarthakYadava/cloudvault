package com.cloudvault.file;

import java.time.Instant;

public record DownloadUrlResponse(
        String downloadUrl,
        String method,
        Instant expiresAt
) {
}
