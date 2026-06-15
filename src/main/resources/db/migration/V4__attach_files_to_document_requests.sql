alter table document_requests
    add column submitted_file_id uuid
        references stored_files (id) on delete set null;

create index idx_document_requests_submitted_file
    on document_requests (submitted_file_id);
