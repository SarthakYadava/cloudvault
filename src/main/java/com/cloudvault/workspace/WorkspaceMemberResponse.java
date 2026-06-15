package com.cloudvault.workspace;

import com.cloudvault.user.UserAccount;

import java.time.Instant;
import java.util.UUID;

public record WorkspaceMemberResponse(
        UUID userId,
        String name,
        String email,
        WorkspaceRole role,
        Instant joinedAt
) {

    static WorkspaceMemberResponse from(
            WorkspaceMembership membership,
            UserAccount user
    ) {
        return new WorkspaceMemberResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                membership.getRole(),
                membership.getCreatedAt()
        );
    }
}
