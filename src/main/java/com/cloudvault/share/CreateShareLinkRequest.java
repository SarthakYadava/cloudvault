package com.cloudvault.share;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateShareLinkRequest(
        @Min(15) @Max(10080) int expirationMinutes
) {
}
