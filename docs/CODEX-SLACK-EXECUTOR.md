# Codex Slack Executor

Purpose: let Gem continue real work from Slack while keeping Slack, Brain, and
Codex responsibilities separate.

## Shape

```text
Slack thread
  -> slack-bot stores session/message
  -> Brain routes complex work to planner/code_agent
  -> slack-bot posts queued/running status
  -> host codex-runner starts `codex exec`
  -> stdout/stderr/result become run events
  -> slack-bot posts final result in the same thread
```

## Boundary

Codex CLI runs on the Mac host, not inside Docker.

Reasons:
- Codex auth is host-owned.
- Git/SSH credentials are host-owned.
- Work repos live on the host.
- Docker Linux containers cannot use a macOS Codex binary directly.
- iOS/Android tooling will also need host access later.

Docker services call the host runner over HTTP:

```text
CODEX_RUNNER_BASE_URL=http://host.docker.internal:8040
```

## Status Lifecycle

Slack should see:

```text
queued
running
completed
failed
```

Each transition is also stored as a run event in Postgres.

## Runner Contract

Request:

```json
{
  "runId": "run_...",
  "user": "U...",
  "text": "task text",
  "contextMessages": [
    {"role": "user", "text": "..."},
    {"role": "assistant", "text": "..."}
  ],
  "projectId": "gem-os"
}
```

Response:

```json
{
  "ok": true,
  "exitCode": 0,
  "output": "final Codex answer",
  "stderr": "",
  "durationMs": 1234
}
```

## First Version

The first version is intentionally thin:
- no file uploads yet
- no Slack Block Kit tables yet
- no cancellation yet
- no multi-repo project resolver yet
- no automatic PR creation

It proves that Slack can launch Codex work, show status, persist run events,
and return a result.

Non-goal: live data lookup. Weather, current prices, latest news, and similar
requests should use a live-data provider, not Codex.

## Later

Next layers:
- `cancel <run>`
- `logs <run>`
- file attachment ingestion
- Slack Block Kit status/result formatting
- per-project workdir/branch policy
- Qdrant context retrieval before Codex prompt creation
