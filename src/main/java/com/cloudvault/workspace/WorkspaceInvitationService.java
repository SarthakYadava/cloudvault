package com.cloudvault.workspace;

import com.cloudvault.config.CloudVaultProperties;
import com.cloudvault.error.InvalidWorkspaceException;
import com.cloudvault.error.WorkspaceAccessException;
import com.cloudvault.error.WorkspaceNotFoundException;
import com.cloudvault.notification.EmailDelivery;
import com.cloudvault.notification.EmailMessage;
import com.cloudvault.user.UserAccount;
import com.cloudvault.user.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class WorkspaceInvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WorkspaceInvitationRepository invitationRepository;
    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMembershipRepository membershipRepository;
    private final UserAccountRepository userRepository;
    private final EmailDelivery emailDelivery;
    private final Duration expiration;
    private final String appBaseUrl;

    public WorkspaceInvitationService(
            WorkspaceInvitationRepository invitationRepository,
            WorkspaceRepository workspaceRepository,
            WorkspaceMembershipRepository membershipRepository,
            UserAccountRepository userRepository,
            EmailDelivery emailDelivery,
            CloudVaultProperties properties
    ) {
        this.invitationRepository = invitationRepository;
        this.workspaceRepository = workspaceRepository;
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.emailDelivery = emailDelivery;
        this.expiration = properties.invitations().expiration();
        this.appBaseUrl = stripTrailingSlash(
                properties.notifications().appBaseUrl()
        );
    }

    @Transactional
    public WorkspaceInvitationResponse create(
            UUID userId,
            UUID workspaceId,
            CreateWorkspaceInvitationRequest request
    ) {
        requireOwner(userId, workspaceId);
        if (request.role() == WorkspaceRole.OWNER) {
            throw new InvalidWorkspaceException(
                    "Invitations can grant STAFF or CLIENT access."
            );
        }

        String email = normalizeEmail(request.email());
        userRepository.findByEmail(email).ifPresent(user -> {
            if (membershipRepository.existsByWorkspaceIdAndUserId(
                    workspaceId,
                    user.getId()
            )) {
                throw new InvalidWorkspaceException(
                        "That user is already a member of this workspace."
                );
            }
        });

        invitationRepository
                .findFirstByWorkspaceIdAndEmailAndAcceptedAtIsNullOrderByCreatedAtDesc(
                        workspaceId,
                        email
                )
                .filter(invitation -> !invitation.isExpired(Instant.now()))
                .ifPresent(invitation -> {
                    throw new InvalidWorkspaceException(
                            "A pending invitation already exists for that email address."
                    );
                });

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new);
        UserAccount inviter = userRepository.findById(userId).orElseThrow();
        String token = generateToken();
        WorkspaceInvitation invitation = invitationRepository.save(
                WorkspaceInvitation.create(
                        workspaceId,
                        userId,
                        email,
                        request.role(),
                        hashToken(token),
                        Instant.now().plus(expiration)
                )
        );
        String acceptanceUrl = acceptanceUrl(token);
        emailDelivery.send(new EmailMessage(
                email,
                "You have been invited to " + workspace.getName(),
                invitationBody(inviter, workspace, invitation, acceptanceUrl)
        ));
        return response(invitation, workspace.getName(), acceptanceUrl);
    }

    @Transactional(readOnly = true)
    public List<WorkspaceInvitationResponse> list(UUID userId, UUID workspaceId) {
        requireOwner(userId, workspaceId);
        String workspaceName = workspaceRepository.findById(workspaceId)
                .orElseThrow(WorkspaceNotFoundException::new)
                .getName();
        return invitationRepository
                .findAllByWorkspaceIdOrderByCreatedAtDesc(workspaceId)
                .stream()
                .map(invitation -> response(invitation, workspaceName, null))
                .toList();
    }

    @Transactional(readOnly = true)
    public WorkspaceInvitationResponse preview(String token) {
        WorkspaceInvitation invitation = findByToken(token);
        Workspace workspace = workspaceRepository
                .findById(invitation.getWorkspaceId())
                .orElseThrow(WorkspaceNotFoundException::new);
        return response(invitation, workspace.getName(), null);
    }

    @Transactional
    public WorkspaceInvitationResponse accept(UUID userId, String token) {
        WorkspaceInvitation invitation = findByToken(token);
        Instant now = Instant.now();
        if (invitation.isAccepted()) {
            throw new InvalidWorkspaceException(
                    "This invitation has already been accepted."
            );
        }
        if (invitation.isExpired(now)) {
            throw new InvalidWorkspaceException("This invitation has expired.");
        }

        UserAccount user = userRepository.findById(userId).orElseThrow();
        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            throw new WorkspaceAccessException(
                    "Sign in with the email address that received this invitation."
            );
        }

        if (!membershipRepository.existsByWorkspaceIdAndUserId(
                invitation.getWorkspaceId(),
                userId
        )) {
            try {
                membershipRepository.saveAndFlush(WorkspaceMembership.create(
                        invitation.getWorkspaceId(),
                        userId,
                        invitation.getRole()
                ));
            } catch (DataIntegrityViolationException exception) {
                throw new InvalidWorkspaceException(
                        "You already belong to this workspace."
                );
            }
        }
        invitation.accept();
        invitationRepository.save(invitation);
        Workspace workspace = workspaceRepository
                .findById(invitation.getWorkspaceId())
                .orElseThrow(WorkspaceNotFoundException::new);
        return response(invitation, workspace.getName(), null);
    }

    private WorkspaceInvitation findByToken(String token) {
        if (token == null || token.isBlank()) {
            throw new InvalidWorkspaceException("This invitation is invalid.");
        }
        return invitationRepository.findByTokenHash(hashToken(token))
                .orElseThrow(() -> new InvalidWorkspaceException(
                        "This invitation is invalid."
                ));
    }

    private void requireOwner(UUID userId, UUID workspaceId) {
        if (!workspaceRepository.existsById(workspaceId)) {
            throw new WorkspaceNotFoundException();
        }
        WorkspaceMembership membership = membershipRepository
                .findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(WorkspaceNotFoundException::new);
        if (membership.getRole() != WorkspaceRole.OWNER) {
            throw new WorkspaceAccessException(
                    "Only the workspace owner can manage invitations."
            );
        }
    }

    private WorkspaceInvitationResponse response(
            WorkspaceInvitation invitation,
            String workspaceName,
            String acceptanceUrl
    ) {
        return new WorkspaceInvitationResponse(
                invitation.getId(),
                invitation.getWorkspaceId(),
                workspaceName,
                invitation.getEmail(),
                invitation.getRole(),
                status(invitation),
                invitation.getCreatedAt(),
                invitation.getExpiresAt(),
                invitation.getAcceptedAt(),
                acceptanceUrl,
                acceptanceUrl == null ? null : emailDelivery.mode()
        );
    }

    private String status(WorkspaceInvitation invitation) {
        if (invitation.isAccepted()) {
            return "ACCEPTED";
        }
        return invitation.isExpired(Instant.now()) ? "EXPIRED" : "PENDING";
    }

    private String invitationBody(
            UserAccount inviter,
            Workspace workspace,
            WorkspaceInvitation invitation,
            String acceptanceUrl
    ) {
        return """
                %s invited you to the "%s" workspace in CloudVault as %s.

                Accept the invitation:
                %s

                This invitation expires on %s. Sign in or create an account using %s.
                """.formatted(
                inviter.getName(),
                workspace.getName(),
                invitation.getRole().name().toLowerCase(Locale.ROOT),
                acceptanceUrl,
                invitation.getExpiresAt(),
                invitation.getEmail()
        );
    }

    private String acceptanceUrl(String token) {
        return appBaseUrl + "/?invite=" + token;
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(token.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String stripTrailingSlash(String value) {
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
