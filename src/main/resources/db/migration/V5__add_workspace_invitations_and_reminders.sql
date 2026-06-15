create table workspace_invitations (
    id uuid primary key,
    workspace_id uuid not null references workspaces (id) on delete cascade,
    invited_by uuid not null references users (id) on delete cascade,
    email varchar(254) not null,
    role varchar(20) not null,
    token_hash varchar(64) not null unique,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    accepted_at timestamp with time zone
);

create index idx_workspace_invitations_workspace_created
    on workspace_invitations (workspace_id, created_at desc);

create index idx_workspace_invitations_email
    on workspace_invitations (email, accepted_at);

alter table document_requests
    add column deadline_reminder_sent_at timestamp with time zone;
