# Architecture

## System Shape

```text
Slack
  |
  v
slack-bot ---> brain ---> provider-router
  |            |              |
  |            v              v
  |       identity/project   LiteLLM chat / direct code agents
  |       memory/db
  |
  v
scheduler ---> pipelines ---> artifacts/logs/results
  |
  v
admin web app <--- db/log store/status
  |
  v
prometheus -> grafana
  |
  v
cloudflared -> public admin/bot endpoints when needed
```

## Services

### slack-bot

Responsibilities:
- Receive Slack DMs, mentions, and commands.
- Maintain thread sessions.
- Build context from identity, user, project, and memory.
- Dispatch requests to brain.
- Report progress and final results.

Non-goals:
- Running heavy pipelines inside the Slack request handler.
- Storing secrets in Slack messages.

### brain

Responsibilities:
- Build Gem/Viktor/project context.
- Classify requests.
- Choose pipeline, agent, tool, or provider capability.
- Return structured decisions.
- Create run records once persistence is added.

Non-goals:
- Executing heavy work directly.
- Replacing provider-router, scheduler, or pipeline services.

### provider-router

Responsibilities:
- Normalize provider calls.
- Route chat calls to LiteLLM or direct provider APIs.
- Route code-agent calls to direct CLI adapters.
- Track provider, model, tokens, cost, latency, and errors.

Provider classes:
- `chat`: text/vision/reasoning calls that do not edit local repos directly.
- `code_agent`: long-running coding sessions that can read/write repos.
  These bypass LiteLLM and use direct non-interactive CLI adapters.

### scheduler

Responsibilities:
- Run cron jobs.
- Start pipeline runs.
- Enforce single-run locks per pipeline/project.
- Persist run state and logs.
- Trigger Slack/admin notifications.

### admin

Responsibilities:
- Show system status.
- Show run history and logs.
- Show provider costs.
- Show scheduler status.
- Validate config.
- Provide operator controls later.

### litellm

Responsibilities:
- Central model gateway.
- Model aliases.
- API key routing.
- Cost visibility.
- Fallback policies for non-code-agent calls.

Non-goals:
- Running Codex or Claude Code sessions.
- Owning code-agent authentication, repo worktrees, or execution logs.

### code agents

Responsibilities:
- Run Codex in non-interactive mode as the default code-agent path.
- Keep Claude Code headless as an optional disabled adapter unless explicitly
  enabled for paid Agent SDK credit usage.
- Use host-owned CLI/API authentication rather than LiteLLM.
- Execute inside explicit worktrees/branches.
- Stream logs and final results back to scheduler/admin/Slack.

Non-goals:
- Running from the Slack request handler.
- Sharing LiteLLM provider keys or budgets.

### cloudflared

Responsibilities:
- Expose selected local services through tunnels.
- Keep public ingress independent from the app services.

### observability

Responsibilities:
- Expose Ktor service metrics through Micrometer.
- Scrape service metrics with Prometheus.
- Show system status and operational dashboards in Grafana.
- Keep business pipeline statistics backed by persisted run data, not only
  process metrics.

### data stores

Recommended MVP:
- Postgres for durable operational data.
- Redis for queues/cache if needed.
- Qdrant for vector memory/knowledge after MVP base is working.

SQLite can be used for early prototypes, but Postgres is cleaner once multiple
services need concurrent reads/writes.

## Container Boundary

Containerized:
- Slack bot.
- Provider router.
- Scheduler.
- Admin app.
- LiteLLM.
- Cloudflared.
- Postgres/Redis/Qdrant.

Host-controlled:
- iOS simulator.
- Android emulator initially.
- Local IDE/editor.
- Optional provider CLI auth files.
- Codex CLI authentication.
- Optional Claude Code CLI/API authentication if headless usage is enabled.

Reason: Linux containers cannot run iOS simulator. Android emulator can run in
containers, but on Mac it adds complexity before the core system is stable.

## Run Model

Every meaningful action becomes a `run`.

Run fields:
- `run_id`
- `kind`: `slack_command`, `pipeline`, `provider_call`, `implementation`
- `project_id`
- `pipeline_id`
- `provider`
- `model`
- `status`
- `started_at`
- `finished_at`
- `cost_usd`
- `input_ref`
- `output_ref`
- `log_ref`

## Safety Principles

- Default to observe/report before auto-change.
- Never let Slack request timeouts own long-running work.
- All long-running work has a run ID.
- All provider calls are logged with enough metadata to debug cost and failures.
- Code-writing agents run in worktrees/branches only.
- Host runners execute constrained commands, not arbitrary shell from Slack.
