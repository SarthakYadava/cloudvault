package com.cloudvault.workspace;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/invitations")
@Tag(name = "Workspace invitations")
public class WorkspaceInvitationController {

    private final WorkspaceInvitationService invitationService;

    public WorkspaceInvitationController(
            WorkspaceInvitationService invitationService
    ) {
        this.invitationService = invitationService;
    }

    @GetMapping("/{token}")
    @Operation(summary = "Preview a workspace invitation")
    public WorkspaceInvitationResponse preview(@PathVariable String token) {
        return invitationService.preview(token);
    }

    @PostMapping("/{token}/accept")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Accept a workspace invitation")
    public WorkspaceInvitationResponse accept(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String token
    ) {
        return invitationService.accept(
                UUID.fromString(jwt.getSubject()),
                token
        );
    }
}
