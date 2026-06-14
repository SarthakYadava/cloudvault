package com.cloudvault.share;

import com.cloudvault.audit.AuditAction;
import com.cloudvault.audit.AuditService;
import com.cloudvault.config.CloudVaultProperties;
import com.cloudvault.error.FileNotFoundException;
import com.cloudvault.error.FileNotReadyException;
import com.cloudvault.error.ShareLinkUnavailableException;
import com.cloudvault.file.FileStatus;
import com.cloudvault.file.StoredFile;
import com.cloudvault.file.StoredFileRepository;
import com.cloudvault.storage.ObjectStorage;
import com.cloudvault.storage.PresignedStorageUrl;
import org.springframework.http.HttpStatus;
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
import java.util.UUID;

@Service
public class ShareLinkService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ShareLinkRepository shareLinkRepository;
    private final StoredFileRepository fileRepository;
    private final ObjectStorage objectStorage;
    private final AuditService auditService;
    private final Duration presignedUrlExpiration;

    public ShareLinkService(
            ShareLinkRepository shareLinkRepository,
            StoredFileRepository fileRepository,
            ObjectStorage objectStorage,
            AuditService auditService,
            CloudVaultProperties properties
    ) {
        this.shareLinkRepository = shareLinkRepository;
        this.fileRepository = fileRepository;
        this.objectStorage = objectStorage;
        this.auditService = auditService;
        this.presignedUrlExpiration = properties.files().presignedUrlExpiration();
    }

    public ShareLinkCreation create(
            UUID ownerId,
            UUID fileId,
            int expirationMinutes
    ) {
        StoredFile file = findOwnedAvailableFile(ownerId, fileId);
        String token = generateToken();
        ShareLink link = shareLinkRepository.save(ShareLink.create(
                ownerId,
                fileId,
                hashToken(token),
                Instant.now().plus(Duration.ofMinutes(expirationMinutes))
        ));
        auditService.record(
                ownerId,
                fileId,
                file.getOriginalName(),
                AuditAction.SHARE_LINK_CREATED
        );
        return new ShareLinkCreation(link, token);
    }

    @Transactional(readOnly = true)
    public List<ShareLinkResponse> list(UUID ownerId, UUID fileId) {
        findOwnedAvailableFile(ownerId, fileId);
        return shareLinkRepository
                .findAllByFileIdAndOwnerIdOrderByCreatedAtDesc(fileId, ownerId)
                .stream()
                .map(ShareLinkResponse::listed)
                .toList();
    }

    public void revoke(UUID ownerId, UUID linkId) {
        ShareLink link = shareLinkRepository.findByIdAndOwnerId(linkId, ownerId)
                .orElseThrow(() -> unavailable(HttpStatus.NOT_FOUND));
        if (link.isRevoked()) {
            return;
        }
        StoredFile file = fileRepository.findByIdAndOwnerId(link.getFileId(), ownerId)
                .orElseThrow(() -> new FileNotFoundException(link.getFileId()));
        link.revoke();
        shareLinkRepository.save(link);
        auditService.record(
                ownerId,
                file.getId(),
                file.getOriginalName(),
                AuditAction.SHARE_LINK_REVOKED
        );
    }

    public PresignedStorageUrl resolve(String token) {
        ShareLink link = shareLinkRepository.findByTokenHash(hashToken(token))
                .orElseThrow(() -> unavailable(HttpStatus.NOT_FOUND));
        Instant now = Instant.now();
        if (link.isRevoked() || link.isExpired(now)) {
            throw unavailable(HttpStatus.GONE);
        }

        StoredFile file = fileRepository.findById(link.getFileId())
                .filter(candidate -> candidate.getOwnerId().equals(link.getOwnerId()))
                .orElseThrow(() -> unavailable(HttpStatus.NOT_FOUND));
        if (file.getStatus() != FileStatus.AVAILABLE) {
            throw unavailable(HttpStatus.GONE);
        }

        Duration remaining = Duration.between(now, link.getExpiresAt());
        Duration urlDuration = remaining.compareTo(presignedUrlExpiration) < 0
                ? remaining
                : presignedUrlExpiration;
        PresignedStorageUrl url = objectStorage.createDownloadUrl(
                file.getObjectKey(),
                file.getOriginalName(),
                urlDuration
        );
        auditService.record(
                link.getOwnerId(),
                file.getId(),
                file.getOriginalName(),
                AuditAction.SHARED_FILE_ACCESSED
        );
        return url;
    }

    private StoredFile findOwnedAvailableFile(UUID ownerId, UUID fileId) {
        StoredFile file = fileRepository.findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new FileNotFoundException(fileId));
        if (file.getStatus() != FileStatus.AVAILABLE) {
            throw new FileNotReadyException("The file upload has not been completed.");
        }
        return file;
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

    private ShareLinkUnavailableException unavailable(HttpStatus status) {
        return new ShareLinkUnavailableException(
                status,
                status == HttpStatus.GONE
                        ? "This share link has expired or been revoked."
                        : "This share link is invalid or unavailable."
        );
    }

    public record ShareLinkCreation(ShareLink link, String token) {
    }
}
