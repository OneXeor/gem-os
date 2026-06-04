# Gem OS

Gem OS is a portable AI operations system for Viktor's work and personal projects.
It is inspired by the earlier GEM system, but designed from the start around
containers, provider routing, observability, and measurable pipelines.

## Core Goals

- Run reproducibly on a Mac mini or another host through dev containers and
  Docker Compose.
- Provide a Slack-first assistant that understands identity, projects, context,
  and long-running tasks.
- Support multiple AI providers: Codex, Claude, and LiteLLM-routed models.
- Expose system health, runs, logs, cost, and pipeline statistics through an
  admin web app.
- Automate useful pipelines before risky code-writing workflows.
- Add implementation agents only after routing, observability, project context,
  and safety rails are stable.

## First MVP

The MVP proves this loop:

```text
Slack message -> Gem identity/context -> provider router -> pipeline run
              -> persisted logs/costs -> admin status -> Slack result
```

The first real pipeline should be ASO Fabric because it is bounded,
cron-friendly, and measurable.

## Documents

- [MVP](docs/MVP.md)
- [Roadmap](docs/ROADMAP.md)
- [Architecture](docs/ARCHITECTURE.md)
- [Runtime](docs/RUNTIME.md)
- [Pipelines](docs/PIPELINES.md)
- [Decisions](adr/)

