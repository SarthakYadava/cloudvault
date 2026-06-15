package com.cloudvault.workspace;

import jakarta.validation.constraints.NotNull;

public record UpdateDocumentRequestStatus(
        @NotNull DocumentRequestStatus status
) {
}
