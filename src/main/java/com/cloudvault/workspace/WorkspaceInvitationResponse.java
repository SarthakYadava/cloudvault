package com.cloudvault.workspace;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceInvitationResponse(
        UUID id,
        UUID workspaceId,
        String workspaceName,
        String email,
        WorkspaceRole role,
        String status,
        Instant createdAt,
        Instant expiresAt,
        Instant acceptedAt,
        String acceptanceUrl,
        String deliveryMode
) {
}
