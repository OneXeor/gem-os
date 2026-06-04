alter table runs
    add column if not exists parent_run_id text references runs(id) on delete set null;

create index if not exists idx_runs_parent_run_id on runs (parent_run_id);
