package com.cloudvault.share;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareLinkRepository extends JpaRepository<ShareLink, UUID> {

    Optional<ShareLink> findByTokenHash(String tokenHash);

    Optional<ShareLink> findByIdAndOwnerId(UUID id, UUID ownerId);

    List<ShareLink> findAllByFileIdAndOwnerIdOrderByCreatedAtDesc(UUID fileId, UUID ownerId);
}
