package com.cloudvault.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    @Query("""
            select file
            from StoredFile file
            where file.ownerId = :ownerId
              and (:query = '' or lower(file.originalName) like lower(concat('%', :query, '%')))
            """)
    Page<StoredFile> searchByOwner(
            @Param("ownerId") UUID ownerId,
            @Param("query") String query,
            Pageable pageable
    );

    Optional<StoredFile> findByIdAndOwnerId(UUID id, UUID ownerId);
}
