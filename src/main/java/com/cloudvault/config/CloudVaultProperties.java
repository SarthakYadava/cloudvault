package com.cloudvault.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;
import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "cloudvault")
public record CloudVaultProperties(
        @Valid Aws aws,
        @Valid Files files,
        @Valid Auth auth
) {

    public record Aws(
            @NotBlank String region,
            @NotBlank String bucket
    ) {
    }

    public record Files(
            @Positive long maxSizeBytes,
            @NotEmpty Set<String> allowedContentTypes,
            @NotNull Duration presignedUrlExpiration
    ) {
    }

    public record Auth(
            @NotBlank @Size(min = 32) String jwtSecret,
            @NotBlank String issuer,
            @NotNull Duration tokenExpiration
    ) {
    }
}
