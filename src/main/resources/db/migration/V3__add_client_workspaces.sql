create table workspaces (
    id uuid primary key,
    name varchar(120) not null,
    created_by uuid not null references users (id) on delete cascade,
    created_at timestamp with time zone not null
);

create table workspace_memberships (
    id uuid primary key,
    workspace_id uuid not null references workspaces (id) on delete cascade,
    user_id uuid not null references users (id) on delete cascade,
    role varchar(20) not null,
    created_at timestamp with time zone not null,
    unique (workspace_id, user_id)
);

create index idx_workspace_memberships_user
    on workspace_memberships (user_id, created_at desc);

create table document_requests (
    id uuid primary key,
    workspace_id uuid not null references workspaces (id) on delete cascade,
    title varchar(160) not null,
    description varchar(1000),
    assigned_to uuid references users (id) on delete set null,
    created_by uuid not null references users (id) on delete cascade,
    due_date date,
    status varchar(20) not null,
    created_at timestamp with time zone not null,
    submitted_at timestamp with time zone,
    approved_at timestamp with time zone
);

create index idx_document_requests_workspace_created
    on document_requests (workspace_id, created_at desc);
