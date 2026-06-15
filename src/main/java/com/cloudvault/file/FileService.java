package com.cloudvault.file;

import com.cloudvault.audit.AuditAction;
import com.cloudvault.audit.AuditService;
import com.cloudvault.config.CloudVaultProperties;
import com.cloudvault.error.FileNotFoundException;
import com.cloudvault.error.FileNotReadyException;
import com.cloudvault.error.InvalidFileException;
import com.cloudvault.storage.ObjectStorage;
import com.cloudvault.storage.PresignedStorageUrl;
import com.cloudvault.storage.StoredObjectMetadata;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class FileService {

    private static final int MAX_PAGE_SIZE = 100;

    private final StoredFileRepository repository;
    private final ObjectStorage objectStorage;
    private final AuditService auditService;
    private final long maxSizeBytes;
    private final Set<String> allowedContentTypes;
    private final Duration presignedUrlExpiration;

    public FileService(
            StoredFileRepository repository,
            ObjectStorage objectStorage,
            AuditService auditService,
            CloudVaultProperties properties
    ) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.auditService = auditService;
        this.maxSizeBytes = properties.files().maxSizeBytes();
        this.allowedContentTypes = properties.files().allowedContentTypes();
        this.presignedUrlExpiration = properties.files().presignedUrlExpiration();
    }

    public FileResponse upload(UUID ownerId, MultipartFile multipartFile) {
        validate(multipartFile);

        String originalName = cleanFilename(multipartFile.getOriginalFilename());
        String contentType = multipartFile.getContentType().toLowerCase(Locale.ROOT);
        String objectKey = buildObjectKey(ownerId, originalName);

        try (InputStream content = multipartFile.getInputStream()) {
            objectStorage.upload(objectKey, content, multipartFile.getSize(), contentType);
        } catch (IOException exception) {
            throw new InvalidFileException("The uploaded file could not be read.", exception);
        }

        StoredFile storedFile = StoredFile.createAvailable(
                ownerId,
                originalName,
                objectKey,
                contentType,
                multipartFile.getSize()
        );

        try {
            StoredFile saved = repository.save(storedFile);
            auditService.record(
                    ownerId,
                    saved.getId(),
                    saved.getOriginalName(),
                    AuditAction.FILE_UPLOADED
            );
            return FileResponse.from(saved);
        } catch (DataAccessException exception) {
            compensateUpload(objectKey, exception);
            throw exception;
        }
    }

    public UploadUrlResponse createUploadRequest(
            UUID ownerId,
            CreateUploadRequest request
    ) {
        String originalName = cleanFilename(request.filename());
        String contentType = normalizeContentType(request.contentType());
        validateMetadata(originalName, contentType, request.sizeBytes());
        String objectKey = buildObjectKey(ownerId, originalName);

        StoredFile file = repository.save(StoredFile.createPending(
                ownerId,
                originalName,
                objectKey,
                contentType,
                request.sizeBytes()
        ));

        try {
            PresignedStorageUrl url = objectStorage.createUploadUrl(
                    objectKey,
                    contentType,
                    presignedUrlExpiration
            );
            return new UploadUrlResponse(
                    FileResponse.from(file),
                    url.url(),
                    url.method(),
                    url.requiredHeaders(),
                    url.expiresAt()
            );
        } catch (RuntimeException exception) {
            compensateReservation(file, exception);
            throw exception;
        }
    }

    public FileResponse completeUpload(UUID ownerId, UUID id) {
        StoredFile file = findFile(ownerId, id);
        if (file.getStatus() == FileStatus.AVAILABLE) {
            return FileResponse.from(file);
        }

        StoredObjectMetadata metadata = objectStorage.stat(file.getObjectKey())
                .orElseThrow(() -> new FileNotReadyException(
                        "The file has not been uploaded to S3 yet."
                ));
        if (metadata.contentLength() != file.getSizeBytes()
                || !file.getContentType().equalsIgnoreCase(metadata.contentType())) {
            objectStorage.delete(file.getObjectKey());
            repository.delete(file);
            throw new InvalidFileException(
                    "The uploaded object does not match the requested size and content type."
            );
        }

        file.markAvailable();
        StoredFile saved = repository.save(file);
        auditService.record(
                ownerId,
                saved.getId(),
                saved.getOriginalName(),
                AuditAction.FILE_UPLOADED
        );
        return FileResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public Page<FileResponse> list(
            UUID ownerId,
            int page,
            int size,
            String query,
            String sortBy,
            String direction
    ) {
        if (page < 0) {
            throw new InvalidFileException("Page number cannot be negative.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidFileException("Page size must be between 1 and 100.");
        }

        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.length() > 100) {
            throw new InvalidFileException("Search query cannot exceed 100 characters.");
        }

        Sort sort = Sort.by(parseDirection(direction), parseSortProperty(sortBy));
        return repository.searchByOwner(
                        ownerId,
                        normalizedQuery,
                        PageRequest.of(page, size, sort)
                )
                .map(FileResponse::from);
    }

    public FileDownload download(UUID ownerId, UUID id) {
        StoredFile file = findFile(ownerId, id);
        ensureAvailable(file);
        InputStream content = objectStorage.download(file.getObjectKey());
        auditService.record(
                ownerId,
                file.getId(),
                file.getOriginalName(),
                AuditAction.DOWNLOAD_LINK_CREATED
        );
        return new FileDownload(
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                content
        );
    }

    @Transactional
    public DownloadUrlResponse createDownloadUrl(UUID ownerId, UUID id) {
        StoredFile file = findFile(ownerId, id);
        ensureAvailable(file);
        PresignedStorageUrl url = objectStorage.createDownloadUrl(
                file.getObjectKey(),
                file.getOriginalName(),
                presignedUrlExpiration
        );
        auditService.record(
                ownerId,
                file.getId(),
                file.getOriginalName(),
                AuditAction.DOWNLOAD_LINK_CREATED
        );
        return new DownloadUrlResponse(url.url(), url.method(), url.expiresAt());
    }

    public void delete(UUID ownerId, UUID id) {
        StoredFile file = findFile(ownerId, id);
        objectStorage.delete(file.getObjectKey());
        repository.delete(file);
        auditService.record(
                ownerId,
                file.getId(),
                file.getOriginalName(),
                AuditAction.FILE_DELETED
        );
    }

    private StoredFile findFile(UUID ownerId, UUID id) {
        return repository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new FileNotFoundException(id));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("Select a non-empty file to upload.");
        }
        if (!StringUtils.hasText(file.getOriginalFilename())) {
            throw new InvalidFileException("The file must have a name.");
        }
        if (!StringUtils.hasText(file.getContentType())) {
            throw new InvalidFileException("The file must have a content type.");
        }
        validateMetadata(
                file.getOriginalFilename(),
                normalizeContentType(file.getContentType()),
                file.getSize()
        );
    }

    private void validateMetadata(String filename, String contentType, long sizeBytes) {
        if (!StringUtils.hasText(filename)) {
            throw new InvalidFileException("The file must have a name.");
        }
        if (sizeBytes < 1) {
            throw new InvalidFileException("The file cannot be empty.");
        }
        if (sizeBytes > maxSizeBytes) {
            throw new InvalidFileException("The file exceeds the 10 MB upload limit.");
        }
        if (!allowedContentTypes.contains(contentType)) {
            throw new InvalidFileException(
                    "Unsupported file type. Allowed types: " + String.join(", ", allowedContentTypes)
            );
        }
    }

    private String normalizeContentType(String contentType) {
        return contentType.trim().toLowerCase(Locale.ROOT);
    }

    private String cleanFilename(String filename) {
        String cleanName = StringUtils.cleanPath(filename);
        if (cleanName.contains("..") || cleanName.contains("/") || cleanName.contains("\\")) {
            throw new InvalidFileException("The filename contains an invalid path.");
        }
        if (cleanName.length() > 255) {
            throw new InvalidFileException("The filename cannot exceed 255 characters.");
        }
        return cleanName;
    }

    private String buildObjectKey(UUID ownerId, String filename) {
        String extension = StringUtils.getFilenameExtension(filename);
        String suffix = StringUtils.hasText(extension)
                ? "." + extension.toLowerCase(Locale.ROOT)
                : "";
        return "users/" + ownerId + "/files/" + UUID.randomUUID() + suffix;
    }

    private void compensateUpload(String objectKey, RuntimeException originalException) {
        try {
            objectStorage.delete(objectKey);
        } catch (RuntimeException compensationException) {
            originalException.addSuppressed(compensationException);
        }
    }

    private void compensateReservation(StoredFile file, RuntimeException originalException) {
        try {
            repository.delete(file);
        } catch (RuntimeException compensationException) {
            originalException.addSuppressed(compensationException);
        }
    }

    private void ensureAvailable(StoredFile file) {
        if (file.getStatus() != FileStatus.AVAILABLE) {
            throw new FileNotReadyException("The file upload has not been completed.");
        }
    }

    private String parseSortProperty(String sortBy) {
        if (sortBy == null || sortBy.isBlank() || sortBy.equals("uploadedAt")) {
            return "uploadedAt";
        }
        return switch (sortBy) {
            case "name" -> "originalName";
            case "size" -> "sizeBytes";
            default -> throw new InvalidFileException(
                    "Sort field must be uploadedAt, name, or size."
            );
        };
    }

    private Sort.Direction parseDirection(String direction) {
        if (direction == null || direction.isBlank() || direction.equalsIgnoreCase("desc")) {
            return Sort.Direction.DESC;
        }
        if (direction.equalsIgnoreCase("asc")) {
            return Sort.Direction.ASC;
        }
        throw new InvalidFileException("Sort direction must be asc or desc.");
    }
}
