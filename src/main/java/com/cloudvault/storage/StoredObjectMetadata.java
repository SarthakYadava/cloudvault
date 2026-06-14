package com.cloudvault.storage;

public record StoredObjectMetadata(
        long contentLength,
        String contentType
) {
}
