package com.cloudvault.workspace;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces")
@Tag(name = "Client workspaces", description = "Manage client access and document requests")
@SecurityRequirement(name = "bearerAuth")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @PostMapping
    @Operation(summary = "Create a client workspace")
    public ResponseEntity<WorkspaceResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateWorkspaceRequest request
    ) {
        return ResponseEntity.status(201)
                .body(workspaceService.create(userId(jwt), request));
    }

    @GetMapping
    @Operation(summary = "List workspaces available to the current user")
    public List<WorkspaceResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return workspaceService.list(userId(jwt));
    }

    @GetMapping("/{workspaceId}/members")
    @Operation(summary = "List workspace members")
    public List<WorkspaceMemberResponse> listMembers(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        return workspaceService.listMembers(userId(jwt), workspaceId);
    }

    @PostMapping("/{workspaceId}/members")
    @Operation(summary = "Add a registered user to a workspace")
    public ResponseEntity<WorkspaceMemberResponse> addMember(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AddWorkspaceMemberRequest request
    ) {
        return ResponseEntity.status(201)
                .body(workspaceService.addMember(userId(jwt), workspaceId, request));
    }

    @GetMapping("/{workspaceId}/requests")
    @Operation(summary = "List workspace document requests")
    public List<DocumentRequestResponse> listRequests(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId
    ) {
        return workspaceService.listRequests(userId(jwt), workspaceId);
    }

    @PostMapping("/{workspaceId}/requests")
    @Operation(summary = "Create a document request")
    public ResponseEntity<DocumentRequestResponse> createRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateDocumentRequest request
    ) {
        return ResponseEntity.status(201).body(
                workspaceService.createRequest(userId(jwt), workspaceId, request)
        );
    }

    @PatchMapping("/{workspaceId}/requests/{requestId}")
    @Operation(summary = "Submit, approve, or reopen a document request")
    public DocumentRequestResponse updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID workspaceId,
            @PathVariable UUID requestId,
            @Valid @RequestBody UpdateDocumentRequestStatus request
    ) {
        return workspaceService.updateStatus(
                userId(jwt),
                workspaceId,
                requestId,
                request
        );
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
