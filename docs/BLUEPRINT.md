# Gem OS Blueprint

Gem OS is an orchestration system.

```text
Interfaces
  Slack
  Admin
  Cron
    |
    v
Brain
  identity
  project registry
  memory retrieval
  capability router
    |
    v
Run Graph
  parent run: request / decision
  child runs: pipeline / agent / tool / chat
    |
    v
Capabilities
  pipelines
    ASO Factory
    knowledge indexing
    implementation workflow
  agents
    Codex CLI
    optional Claude Code headless
  model gateway
    LiteLLM
  tools
    GitHub discovery
    repository checkout/update
    host runners
    simulator/emulator runners later
    |
    v
Storage
  Postgres: runs, events, operational state
  Qdrant: vector index
  Redis: queue/cache/locks
  filesystem: checked-out repos and artifacts
    |
    v
Observability
  Admin API/UI
  Prometheus
  Grafana
  Slack reports
```

## Main Rule

Gem does not equal any one LLM. Gem chooses and controls capabilities.

## Request Flow

```text
Slack/cron/manual request
  -> Brain builds context
  -> Brain creates parent run
  -> Brain selects capability
  -> Gem creates child run
  -> Scheduler/worker executes child run
  -> Logs/artifacts/results are persisted
  -> Brain/Admin/Slack can explain what happened
```

## Knowledge Flow

```text
repo/account/source discovery
  -> fetch docs/code/notes
  -> chunk
  -> embed with local BGE-M3
  -> store vectors in Qdrant
  -> retrieve relevant context before planning/execution
```

## Default Provider Policy

- Planner/code-agent default: Codex CLI non-interactive.
- Claude Code headless: optional, disabled by default.
- LiteLLM: bounded chat/model gateway.
- BGE-M3: local embedding model for memory/indexing.

## Near-Term Build Order

1. Scheduler executes child runs.
2. Knowledge indexing pipelines.
3. Brain retrieval from Qdrant.
4. Slack bot connected to Brain.
5. ASO Factory execution through scheduler.
6. Codex host-runner adapter.
