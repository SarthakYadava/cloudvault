package com.cloudvault.file;

import com.cloudvault.audit.AuditAction;
import com.cloudvault.audit.AuditService;
import com.cloudvault.config.CloudVaultProperties;
import com.cloudvault.error.InvalidFileException;
import com.cloudvault.storage.ObjectStorage;
import com.cloudvault.storage.PresignedStorageUrl;
import com.cloudvault.storage.StoredObjectMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private StoredFileRepository repository;

    @Mock
    private ObjectStorage objectStorage;

    @Mock
    private AuditService auditService;

    private FileService fileService;
    private UUID ownerId;

    @BeforeEach
    void setUp() {
        CloudVaultProperties properties = new CloudVaultProperties(
                new CloudVaultProperties.Aws("eu-north-1", "test-bucket"),
                new CloudVaultProperties.Files(
                        10 * 1024 * 1024,
                        Set.of("application/pdf", "image/png"),
                        Duration.ofMinutes(10)
                ),
                new CloudVaultProperties.Auth(
                        "cloudvault-test-secret-key-at-least-32-characters",
                        "cloudvault-test",
                        Duration.ofHours(1)
                )
        );
        fileService = new FileService(repository, objectStorage, auditService, properties);
        ownerId = UUID.randomUUID();
    }

    @Test
    void uploadsWithGeneratedObjectKeyAndPersistsMetadata() {
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "client-contract.pdf",
                "application/pdf",
                "contract".getBytes()
        );
        when(repository.save(any(StoredFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FileResponse response = fileService.upload(ownerId, upload);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(objectStorage).upload(
                keyCaptor.capture(),
                any(InputStream.class),
                org.mockito.ArgumentMatchers.eq(upload.getSize()),
                org.mockito.ArgumentMatchers.eq("application/pdf")
        );
        assertThat(keyCaptor.getValue())
                .startsWith("users/" + ownerId + "/files/")
                .endsWith(".pdf")
                .doesNotContain("client-contract");
        assertThat(response.originalName()).isEqualTo("client-contract.pdf");
        assertThat(response.sizeBytes()).isEqualTo(upload.getSize());
        verify(auditService).record(
                ownerId,
                response.id(),
                "client-contract.pdf",
                AuditAction.FILE_UPLOADED
        );
    }

    @Test
    void rejectsUnsupportedContentTypeBeforeCallingStorage() {
        MockMultipartFile upload = new MockMultipartFile(
                "file",
                "script.exe",
                "application/octet-stream",
                new byte[]{1}
        );

        assertThatThrownBy(() -> fileService.upload(ownerId, upload))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("Unsupported file type");
        verifyNoInteractions(objectStorage, repository);
    }

    @Test
    void deletesObjectBeforeMetadata() {
        StoredFile file = StoredFile.createAvailable(
                ownerId,
                "photo.png",
                "users/" + ownerId + "/files/" + UUID.randomUUID() + ".png",
                "image/png",
                10
        );
        when(repository.findByIdAndOwnerId(file.getId(), ownerId))
                .thenReturn(Optional.of(file));

        fileService.delete(ownerId, file.getId());

        verify(objectStorage).delete(file.getObjectKey());
        verify(repository).delete(file);
        verify(auditService).record(
                ownerId,
                file.getId(),
                file.getOriginalName(),
                AuditAction.FILE_DELETED
        );
    }

    @Test
    void recordsStreamedDownloadsInAuditHistory() {
        StoredFile file = StoredFile.createAvailable(
                ownerId,
                "statement.pdf",
                "users/" + ownerId + "/files/" + UUID.randomUUID() + ".pdf",
                "application/pdf",
                9
        );
        when(repository.findByIdAndOwnerId(file.getId(), ownerId))
                .thenReturn(Optional.of(file));
        when(objectStorage.download(file.getObjectKey()))
                .thenReturn(new ByteArrayInputStream("statement".getBytes()));

        FileDownload download = fileService.download(ownerId, file.getId());

        assertThat(download.filename()).isEqualTo("statement.pdf");
        verify(auditService).record(
                ownerId,
                file.getId(),
                file.getOriginalName(),
                AuditAction.DOWNLOAD_LINK_CREATED
        );
    }

    @Test
    void createsPendingDirectUploadRequest() {
        when(repository.save(any(StoredFile.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(objectStorage.createUploadUrl(
                any(String.class),
                org.mockito.ArgumentMatchers.eq("application/pdf"),
                org.mockito.ArgumentMatchers.eq(Duration.ofMinutes(10))
        )).thenReturn(new PresignedStorageUrl(
                "https://example.test/upload",
                "PUT",
                java.util.Map.of("Content-Type", "application/pdf"),
                Instant.parse("2030-01-01T00:00:00Z")
        ));

        UploadUrlResponse response = fileService.createUploadRequest(
                ownerId,
                new CreateUploadRequest("contract.pdf", "application/pdf", 100)
        );

        assertThat(response.file().status()).isEqualTo(FileStatus.PENDING);
        assertThat(response.uploadUrl()).isEqualTo("https://example.test/upload");
        assertThat(response.method()).isEqualTo("PUT");
    }

    @Test
    void verifiesDirectUploadBeforeMarkingItAvailable() {
        StoredFile file = StoredFile.createPending(
                ownerId,
                "contract.pdf",
                "users/" + ownerId + "/files/" + UUID.randomUUID() + ".pdf",
                "application/pdf",
                100
        );
        when(repository.findByIdAndOwnerId(file.getId(), ownerId))
                .thenReturn(Optional.of(file));
        when(objectStorage.stat(file.getObjectKey()))
                .thenReturn(Optional.of(new StoredObjectMetadata(100, "application/pdf")));
        when(repository.save(file)).thenReturn(file);

        FileResponse response = fileService.completeUpload(ownerId, file.getId());

        assertThat(response.status()).isEqualTo(FileStatus.AVAILABLE);
        verify(repository).save(file);
        verify(auditService).record(
                ownerId,
                file.getId(),
                file.getOriginalName(),
                AuditAction.FILE_UPLOADED
        );
    }

    @Test
    void rejectsAndRemovesMismatchedDirectUpload() {
        StoredFile file = StoredFile.createPending(
                ownerId,
                "contract.pdf",
                "users/" + ownerId + "/files/" + UUID.randomUUID() + ".pdf",
                "application/pdf",
                100
        );
        when(repository.findByIdAndOwnerId(file.getId(), ownerId))
                .thenReturn(Optional.of(file));
        when(objectStorage.stat(file.getObjectKey()))
                .thenReturn(Optional.of(new StoredObjectMetadata(99, "application/pdf")));

        assertThatThrownBy(() -> fileService.completeUpload(ownerId, file.getId()))
                .isInstanceOf(InvalidFileException.class)
                .hasMessageContaining("does not match");

        verify(objectStorage).delete(file.getObjectKey());
        verify(repository).delete(file);
    }
}
