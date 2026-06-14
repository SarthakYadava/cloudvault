create table audit_events (
    id uuid primary key,
    owner_id uuid not null references users (id) on delete cascade,
    file_id uuid,
    filename varchar(255),
    action varchar(40) not null,
    occurred_at timestamp with time zone not null
);

create index idx_audit_events_owner_occurred_at
    on audit_events (owner_id, occurred_at desc);

create table share_links (
    id uuid primary key,
    owner_id uuid not null references users (id) on delete cascade,
    file_id uuid not null references stored_files (id) on delete cascade,
    token_hash varchar(64) not null unique,
    created_at timestamp with time zone not null,
    expires_at timestamp with time zone not null,
    revoked_at timestamp with time zone
);

create index idx_share_links_owner_created_at
    on share_links (owner_id, created_at desc);

create index idx_share_links_file_created_at
    on share_links (file_id, created_at desc);
