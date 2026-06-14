package com.cloudvault.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        UUID fileId,
        String filename,
        AuditAction action,
        Instant occurredAt
) {

    static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getFileId(),
                event.getFilename(),
                event.getAction(),
                event.getOccurredAt()
        );
    }
}
