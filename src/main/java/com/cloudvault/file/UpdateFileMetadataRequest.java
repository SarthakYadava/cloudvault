package com.cloudvault.file;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Set;

public record UpdateFileMetadataRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 80) String folder,
        @NotNull @Size(max = 8) Set<@NotBlank @Size(max = 30) String> tags
) {
}
