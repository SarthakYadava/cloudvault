package com.cloudvault.share;

import java.time.Instant;
import java.util.UUID;

public record ShareLinkResponse(
        UUID id,
        UUID fileId,
        String shareUrl,
        Instant createdAt,
        Instant expiresAt,
        Instant revokedAt,
        boolean active
) {

    static ShareLinkResponse created(ShareLink link, String shareUrl) {
        return new ShareLinkResponse(
                link.getId(),
                link.getFileId(),
                shareUrl,
                link.getCreatedAt(),
                link.getExpiresAt(),
                link.getRevokedAt(),
                true
        );
    }

    static ShareLinkResponse listed(ShareLink link) {
        Instant now = Instant.now();
        return new ShareLinkResponse(
                link.getId(),
                link.getFileId(),
                null,
                link.getCreatedAt(),
                link.getExpiresAt(),
                link.getRevokedAt(),
                !link.isRevoked() && !link.isExpired(now)
        );
    }
}
