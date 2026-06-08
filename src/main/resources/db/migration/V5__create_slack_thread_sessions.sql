create table if not exists slack_thread_sessions (
    id text primary key,
    slack_channel_id text not null,
    slack_thread_ts text not null,
    owner_slack_user_id text,
    status text not null check (status in ('active', 'paused', 'closed')),
    current_run_id text references runs(id) on delete set null,
    last_decision text,
    last_route text,
    last_project_id text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    last_message_at timestamptz not null default now()
);

create unique index if not exists idx_slack_thread_sessions_active_thread
    on slack_thread_sessions (slack_channel_id, slack_thread_ts)
    where status = 'active';

create index if not exists idx_slack_thread_sessions_updated_at
    on slack_thread_sessions (updated_at desc);

alter table slack_thread_messages
    add column if not exists slack_session_id text references slack_thread_sessions(id) on delete set null;

create index if not exists idx_slack_thread_messages_session_created_at
    on slack_thread_messages (slack_session_id, created_at);
