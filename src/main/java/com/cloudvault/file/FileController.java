package com.cloudvault.file;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@Tag(name = "Files", description = "Upload and manage private files")
@SecurityRequirement(name = "bearerAuth")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a file")
    public ResponseEntity<FileResponse> upload(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(201)
                .body(fileService.upload(userId(jwt), file));
    }

    @GetMapping
    @Operation(summary = "List uploaded files")
    public Page<FileResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "uploadedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction
    ) {
        return fileService.list(userId(jwt), page, size, query, sort, direction);
    }

    @PostMapping("/upload-requests")
    @Operation(summary = "Create a direct-to-S3 upload URL")
    public ResponseEntity<UploadUrlResponse> createUploadRequest(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateUploadRequest request
    ) {
        return ResponseEntity.status(201)
                .body(fileService.createUploadRequest(userId(jwt), request));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Verify and complete a direct S3 upload")
    public FileResponse completeUpload(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return fileService.completeUpload(userId(jwt), id);
    }

    @GetMapping("/{id}/download-url")
    @Operation(summary = "Create a temporary direct S3 download URL")
    public DownloadUrlResponse createDownloadUrl(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        return fileService.createDownloadUrl(userId(jwt), id);
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Download a file")
    public ResponseEntity<InputStreamResource> download(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        FileDownload download = fileService.download(userId(jwt), id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.contentLength())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(download.content()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a file")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID id
    ) {
        fileService.delete(userId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
