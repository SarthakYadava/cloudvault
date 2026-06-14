package com.cloudvault.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateUploadRequest(
        @NotBlank @Size(max = 255) String filename,
        @NotBlank @Size(max = 128) String contentType,
        @Positive long sizeBytes
) {
}
