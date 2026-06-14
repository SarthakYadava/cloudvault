package com.cloudvault.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    Page<StoredFile> findAllByOwnerIdOrderByUploadedAtDesc(UUID ownerId, Pageable pageable);

    Optional<StoredFile> findByIdAndOwnerId(UUID id, UUID ownerId);
}
