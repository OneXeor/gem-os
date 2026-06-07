create table if not exists slack_thread_messages (
    id bigserial primary key,
    slack_channel_id text not null,
    slack_thread_ts text not null,
    slack_event_ts text,
    slack_user_id text,
    direction text not null check (direction in ('user', 'gem')),
    text text not null,
    brain_run_id text references runs(id) on delete set null,
    created_at timestamptz not null default now()
);

create unique index if not exists idx_slack_thread_messages_event_direction
    on slack_thread_messages (slack_channel_id, slack_event_ts, direction)
    where slack_event_ts is not null;

create index if not exists idx_slack_thread_messages_thread_created_at
    on slack_thread_messages (slack_channel_id, slack_thread_ts, created_at);
