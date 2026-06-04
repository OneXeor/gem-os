# Stage 1 Implementation Plan

## Objective

Create a portable runtime that can start locally through Docker Compose and
support later Slack, admin, provider, and pipeline services without changing the
deployment shape.

## Target Repo Structure

```text
gem-os/
├── .devcontainer/
├── config/
├── docs/
├── services/
│   ├── admin/
│   ├── slack-bot/
│   ├── scheduler/
│   └── provider-router/
├── packages/
│   └── gem-core/
├── pipelines/
│   └── aso-fabric/
├── docker-compose.yml
├── Dockerfile
├── Makefile
└── .env.example
```

## Runtime Stack

Services:
- `postgres`
- `redis`
- `qdrant`
- `litellm`
- `provider-router`
- `slack-bot`
- `scheduler`
- `admin`
- `cloudflared`

Initial code services can be thin health-check apps. The point of Stage 1 is to
prove packaging, networking, config loading, health checks, and persistence.

## Recommended Stack

- Python 3.12.
- FastAPI for admin/provider-router service APIs.
- Slack Bolt for Slack bot.
- APScheduler or custom lightweight scheduler for cron jobs.
- SQLAlchemy + Alembic for Postgres.
- Pydantic Settings for config.
- Docker Compose for local deployment.

## First Commits

1. Add Python project metadata and shared `gem-core`.
2. Add Dockerfile and dev container.
3. Add Compose with infra services only.
4. Add admin health endpoint.
5. Add provider-router health endpoint.
6. Add scheduler health/status endpoint or CLI.
7. Add Slack bot skeleton with `status` command stub.
8. Add Cloudflared service but keep it disabled by default.

## Verification

```bash
docker compose up -d --build
docker compose ps
curl http://localhost:8000/health
curl http://localhost:8010/health
docker compose logs scheduler
```

Expected result:
- All enabled services healthy.
- Admin can reach database.
- Provider router can reach LiteLLM health endpoint or report disabled/missing
  config clearly.
- Scheduler can load pipeline registry.

## Open Decisions Before Coding

- Use Postgres from day one or start with SQLite and migrate quickly.
- Use one Python monorepo package or separate per-service packages.
- Whether Slack bot talks to provider-router over HTTP or imports core directly.
- Whether scheduler runs pipelines in-process or as subprocesses.

Default recommendation:
- Postgres from day one.
- One monorepo with shared `gem-core`.
- HTTP boundary for provider-router.
- Scheduler starts pipelines as subprocesses or queue jobs, not imports, once
  pipelines become long-running.

