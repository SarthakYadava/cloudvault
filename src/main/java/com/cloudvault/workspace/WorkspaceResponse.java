package com.cloudvault.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceResponse(
        UUID id,
        String name,
        WorkspaceRole role,
        long memberCount,
        long pendingRequestCount,
        Instant createdAt
) {
}
