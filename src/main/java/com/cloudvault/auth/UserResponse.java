package com.cloudvault.auth;

import com.cloudvault.user.UserAccount;
import com.cloudvault.user.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        UserRole role,
        Instant createdAt
) {

    static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
