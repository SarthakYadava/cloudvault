package com.cloudvault.workspace;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateWorkspaceInvitationRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull WorkspaceRole role
) {
}
