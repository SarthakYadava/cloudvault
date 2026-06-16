package com.cloudvault.file;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface StoredFileRepository extends JpaRepository<StoredFile, UUID> {

    @Query("""
            select file
            from StoredFile file
            where file.ownerId = :ownerId
              and (
                :query = ''
                or lower(file.originalName) like lower(concat('%', :query, '%'))
                or exists (
                    select tag
                    from file.tags tag
                    where lower(tag) like lower(concat('%', :query, '%'))
                )
              )
              and (:folder = '' or lower(file.folder) = lower(:folder))
              and (
                :tag = ''
                or exists (
                    select matchedTag
                    from file.tags matchedTag
                    where lower(matchedTag) = lower(:tag)
                )
              )
            """)
    Page<StoredFile> searchByOwner(
            @Param("ownerId") UUID ownerId,
            @Param("query") String query,
            @Param("folder") String folder,
            @Param("tag") String tag,
            Pageable pageable
    );

    Optional<StoredFile> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("""
            select distinct file.folder
            from StoredFile file
            where file.ownerId = :ownerId
            order by file.folder
            """)
    List<String> findDistinctFoldersByOwner(@Param("ownerId") UUID ownerId);

    @Query(
            value = """
                    select exists (
                        select 1
                        from document_requests request
                        where request.submitted_file_id = :fileId
                    )
                    """,
            nativeQuery = true
    )
    boolean isAttachedToDocumentRequest(@Param("fileId") UUID fileId);
}
