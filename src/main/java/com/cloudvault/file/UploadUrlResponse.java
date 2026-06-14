package com.cloudvault.file;

import java.time.Instant;
import java.util.Map;

public record UploadUrlResponse(
        FileResponse file,
        String uploadUrl,
        String method,
        Map<String, String> requiredHeaders,
        Instant expiresAt
) {
}
