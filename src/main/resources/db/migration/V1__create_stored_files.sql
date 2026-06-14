create table users (
    id uuid primary key,
    name varchar(100) not null,
    email varchar(254) not null unique,
    password_hash varchar(100) not null,
    role varchar(20) not null,
    created_at timestamp with time zone not null
);

create table stored_files (
    id uuid primary key,
    owner_id uuid not null references users (id) on delete cascade,
    original_name varchar(255) not null,
    object_key varchar(512) not null unique,
    content_type varchar(128) not null,
    size_bytes bigint not null check (size_bytes >= 0),
    status varchar(20) not null,
    uploaded_at timestamp with time zone not null
);

create index idx_stored_files_uploaded_at
    on stored_files (uploaded_at desc);

create index idx_stored_files_owner_uploaded_at
    on stored_files (owner_id, uploaded_at desc);
