package com.cloudvault.storage;

import java.time.Instant;
import java.util.Map;

public record PresignedStorageUrl(
        String url,
        String method,
        Map<String, String> requiredHeaders,
        Instant expiresAt
) {
}
