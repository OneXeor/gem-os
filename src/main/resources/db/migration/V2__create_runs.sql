create table if not exists runs (
    id text primary key,
    kind text not null,
    status text not null,
    user_id text,
    project_id text,
    pipeline_id text,
    provider text,
    route text,
    input_json jsonb,
    decision_json jsonb,
    result_json jsonb,
    error text,
    created_at timestamptz not null default now(),
    started_at timestamptz,
    finished_at timestamptz
);

create index if not exists idx_runs_created_at on runs (created_at desc);
create index if not exists idx_runs_status on runs (status);
create index if not exists idx_runs_project_id on runs (project_id);

create table if not exists run_events (
    id bigserial primary key,
    run_id text not null references runs(id) on delete cascade,
    level text not null,
    message text not null,
    payload_json jsonb,
    created_at timestamptz not null default now()
);

create index if not exists idx_run_events_run_id_created_at on run_events (run_id, created_at);
