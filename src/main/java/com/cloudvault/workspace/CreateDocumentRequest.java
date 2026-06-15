package com.cloudvault.workspace;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record CreateDocumentRequest(
        @NotBlank @Size(max = 160) String title,
        @Size(max = 1000) String description,
        @Email String assigneeEmail,
        LocalDate dueDate
) {
}
