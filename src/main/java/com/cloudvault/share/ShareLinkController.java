package com.cloudvault.share;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@Tag(name = "Sharing", description = "Create and revoke expiring file links")
@SecurityRequirement(name = "bearerAuth")
public class ShareLinkController {

    private final ShareLinkService shareLinkService;

    public ShareLinkController(ShareLinkService shareLinkService) {
        this.shareLinkService = shareLinkService;
    }

    @PostMapping("/files/{fileId}/shares")
    @Operation(summary = "Create an expiring share link")
    public ResponseEntity<ShareLinkResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID fileId,
            @Valid @RequestBody CreateShareLinkRequest request
    ) {
        ShareLinkService.ShareLinkCreation creation = shareLinkService.create(
                userId(jwt),
                fileId,
                request.expirationMinutes()
        );
        String url = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/s/{token}")
                .buildAndExpand(creation.token())
                .toUriString();
        return ResponseEntity.status(201)
                .body(ShareLinkResponse.created(creation.link(), url));
    }

    @GetMapping("/files/{fileId}/shares")
    @Operation(summary = "List a file's share links")
    public List<ShareLinkResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID fileId
    ) {
        return shareLinkService.list(userId(jwt), fileId);
    }

    @DeleteMapping("/shares/{linkId}")
    @Operation(summary = "Revoke a share link")
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID linkId
    ) {
        shareLinkService.revoke(userId(jwt), linkId);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
