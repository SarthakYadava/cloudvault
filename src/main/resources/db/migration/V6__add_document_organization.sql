alter table stored_files
    add column folder varchar(80) not null default 'Unfiled';

create table stored_file_tags (
    file_id uuid not null references stored_files (id) on delete cascade,
    tag varchar(30) not null,
    primary key (file_id, tag)
);

create index idx_stored_files_owner_folder
    on stored_files (owner_id, folder);

create index idx_stored_file_tags_tag
    on stored_file_tags (tag);

alter table document_requests
    add column category varchar(30) not null default 'GENERAL';

create index idx_document_requests_workspace_status_due
    on document_requests (workspace_id, status, due_date);
