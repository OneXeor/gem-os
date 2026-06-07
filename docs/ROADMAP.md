# Roadmap

## Stage 0: Product And Architecture

Purpose: define the system before implementation.

Deliverables:
- Product README.
- MVP scope.
- Architecture document.
- Runtime design.
- Initial config schemas.
- ADRs for key decisions.

Exit criteria:
- The service list, data flow, provider boundaries, and MVP are clear enough to
  implement without re-litigating scope every day.

## Stage 1: Portable Runtime

Purpose: make the system reproducible on any target host.

Deliverables:
- `.devcontainer/`.
- `docker-compose.yml`.
- Services: `slack-bot`, `admin`, `scheduler`, `litellm`, `cloudflared`,
  database, Redis, Qdrant.
- Shared env and secrets conventions.
- Health checks.

Exit criteria:
- A clean machine can start the stack.
- Admin health page can show all services.

## Stage 2: Identity And Project Registry

Purpose: make Gem understand who it is helping and which projects exist.

Deliverables:
- `identity.yaml`.
- `projects.yaml`.
- Project context loader.
- Slack prompt/context builder.
- Project aliases and ownership.

Exit criteria:
- Slack bot can answer who Gem is, who Viktor is, and which projects exist.
- Bot can resolve a user message to a project when enough context exists.

## Stage 3: Capability Router And Providers

Purpose: route work to pipelines, tools, Codex, LiteLLM, and optional providers
without hardwiring workflows to one backend.

Deliverables:
- Provider registry.
- Chat provider interface.
- Code-agent provider interface.
- Direct non-interactive Codex adapter.
- Optional Claude Code adapter marked disabled by default because `claude -p`
  uses separate Agent SDK credits starting June 15, 2026.
- Cost/token tracking.
- Provider health checks.
- Per-project defaults and overrides.

Exit criteria:
- Slack/Gem can route a request to a pipeline, Codex, or LiteLLM by capability.
- Implementation jobs can run Codex directly without LiteLLM.
- Claude Code is not required for MVP or default operation.
- Run records include provider, model, tokens, cost, and status.

## Stage 4: Slack Bot

Purpose: make Slack the operator interface.

Deliverables:
- DM handler.
- Thread session store.
- Command router.
- Fast chat replies for `help`, `status`, `projects`, and identity questions.
- Socket Mode support for workspaces that do not deliver Events API over HTTP.
- Allowlist.
- Provider selection.
- Run status and logs commands.

Exit criteria:
- Gem feels like a Slack chat assistant, not only a run router.
- Gem remembers context within a Slack thread.
- Viktor can start and inspect a pipeline run from Slack.

## Stage 5: Memory Foundation

Purpose: give Gem enough memory to behave like a useful chat assistant before
deep automation is added.

Deliverables:
- Postgres thread memory.
- Run memory through existing runs and run events.
- Markdown durable notes for projects, preferences, and decisions.
- Clear Redis role for deduplication, locks, rate limits, and temporary cache.
- Memory design documented in `docs/MEMORY.md`.

Exit criteria:
- Gem can resolve simple follow-ups like "run that" inside the same Slack
  thread.
- Important facts have an obvious place to live as markdown notes.
- Redis is not used as a source of truth.

## Stage 6: Admin Web App

Purpose: make the system observable and debuggable.

Deliverables:
- Dashboard.
- Service status.
- Scheduler jobs.
- Runs list/detail.
- Logs viewer.
- Provider/cost stats.
- Project registry view.
- Config validation page.

Exit criteria:
- Any pipeline failure can be diagnosed from admin without SSHing first.

## Stage 7: Scheduler Execution

Purpose: make child runs actually execute instead of only being recorded.

Deliverables:
- Worker loop for `created -> running -> completed/failed`.
- Execution adapters for `context_answer`, `planner`, `pipeline`, and
  `code_agent`.
- Run event/result persistence.
- Slack final-result updates.

Exit criteria:
- Slack can receive both the immediate chat reply and the final run result.

## Stage 8: ASO Fabric Pipeline

Purpose: ship the first useful real automation.

Deliverables:
- ASO Fabric project connector.
- Metric collection.
- Competitor/keyword collection.
- Hypothesis generation.
- Recommendation/report generation.
- Outcome monitoring.
- Cron job.
- Slack and admin reporting.

Exit criteria:
- ASO runs on schedule and produces measurable recommendations.
- Later runs can say what improved, degraded, or stayed inconclusive.

## Stage 9: Knowledge Indexing

Purpose: make Gem understand available repositories, docs, selected code, and
operator notes before serious agents execute work.

Deliverables:
- Repository catalog pipeline for Viktor and Hopin.it GitHub accounts/orgs.
- Documentation indexing pipeline.
- Selected code indexing pipeline.
- Optional Obsidian vault indexing pipeline.
- BGE-M3 embedding provider. Deployed locally through Docker Compose with
  Infinity and exposed to apps through LiteLLM as `bge-m3`.
- Qdrant collections for repos, docs, code, and notes.
- Retrieval API for Brain.

Exit criteria:
- Gem can answer what repos/projects exist from indexed sources.
- Brain can retrieve relevant docs/notes before choosing a capability.
- Indexing can run by cron and manual trigger.

## Stage 10: Learning And Memory

Purpose: turn outcomes into reusable system knowledge.

Deliverables:
- Memory model.
- Decision store.
- Pipeline lesson store.
- Retrieval into prompts.
- Admin review queue for uncertain memories.

Exit criteria:
- ASO and operator decisions can be reused in later runs.

## Stage 11: Implementation Pipeline

Purpose: safely automate code changes.

Deliverables:
- Task loader.
- Planning agent.
- Worktree/branch manager.
- Code-agent execution.
- Build/test runner.
- PR creation.
- Feedback loop for failures.

Exit criteria:
- Gem can implement a small task in a test repo and produce a verified PR.

## Stage 12: Host Runners For Mobile Automation

Purpose: support platform checks that cannot live cleanly inside Linux
containers.

Deliverables:
- Host runner protocol.
- macOS launch agent or daemon.
- iOS simulator runner.
- Android emulator runner.
- Screenshots/videos/log artifacts.

Exit criteria:
- Implementation pipeline can request simulator/emulator checks and get
  structured results.

## Stage 13: Hardening

Purpose: make the system reliable enough to leave running.

Deliverables:
- Backup/restore.
- Secrets rotation.
- Alerting.
- Rate-limit handling.
- Provider fallback.
- Run cancellation.
- Rollback playbooks.
