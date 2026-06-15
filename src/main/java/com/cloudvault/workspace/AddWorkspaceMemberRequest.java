package com.cloudvault.workspace;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddWorkspaceMemberRequest(
        @NotBlank @Email String email,
        @NotNull WorkspaceRole role
) {
}
