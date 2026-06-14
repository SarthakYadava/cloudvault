package com.cloudvault.audit;

import com.cloudvault.error.InvalidFileException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private static final int MAX_PAGE_SIZE = 100;

    private final AuditEventRepository repository;

    public AuditService(AuditEventRepository repository) {
        this.repository = repository;
    }

    public void record(
            UUID ownerId,
            UUID fileId,
            String filename,
            AuditAction action
    ) {
        repository.save(AuditEvent.create(ownerId, fileId, filename, action));
    }

    @Transactional(readOnly = true)
    public Page<AuditEventResponse> list(UUID ownerId, int page, int size) {
        if (page < 0) {
            throw new InvalidFileException("Page number cannot be negative.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidFileException("Page size must be between 1 and 100.");
        }
        return repository.findAllByOwnerIdOrderByOccurredAtDesc(
                        ownerId,
                        PageRequest.of(page, size)
                )
                .map(AuditEventResponse::from);
    }
}
