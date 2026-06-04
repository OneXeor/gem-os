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
├── ops/
│   ├── grafana/
│   └── prometheus/
├── src/
│   ├── main/kotlin/com/onexeor/gemos/
│   └── test/kotlin/com/onexeor/gemos/
├── build.gradle.kts
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
- `prometheus`
- `grafana`

Initial code services can be thin health-check apps. The point of Stage 1 is to
prove packaging, networking, config loading, health checks, and persistence.

## Recommended Stack

- Kotlin/JVM 21.
- Ktor for admin/provider-router/slack service APIs.
- Kotlin Slack SDK or Slack Bolt Java/Kotlin interop for Slack.
- Gradle Kotlin DSL for builds.
- Micrometer Prometheus registry for runtime metrics.
- Prometheus for scraping metrics.
- Grafana for dashboards.
- Postgres for durable operational data.
- Docker Compose for local deployment.

## First Commits

1. Add Gradle Kotlin project metadata and shared core package.
2. Add Dockerfile and dev container.
3. Add Compose with infra services only.
4. Add admin health endpoint.
5. Add provider-router health endpoint.
6. Add scheduler health/status endpoint or CLI.
7. Add Slack bot skeleton with `status` command stub.
8. Add Cloudflared service but keep it disabled by default.
9. Add Prometheus scraping and Grafana datasource provisioning.

## Verification

```bash
docker-compose up -d --build
docker-compose ps
curl http://localhost:8000/health
curl http://localhost:8010/health
curl http://localhost:8000/metrics
curl http://localhost:9090/-/ready
docker-compose logs scheduler
```

Expected result:
- All enabled services healthy.
- Admin can reach database.
- Provider router can reach LiteLLM health endpoint or report disabled/missing
  config clearly.
- Scheduler can load pipeline registry.
- Prometheus scrapes Ktor services.
- Grafana starts with a provisioned Prometheus datasource.

## Open Decisions Before Coding

- Whether Slack bot talks to provider-router over HTTP or a typed internal client.
- Whether scheduler runs pipelines in-process or as subprocesses.

Default recommendation:
- Postgres from day one.
- One Kotlin monorepo with shared core code.
- HTTP boundary for provider-router.
- Scheduler starts pipelines as subprocesses or queue jobs, not imports, once
  pipelines become long-running.
