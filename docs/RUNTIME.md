# Runtime

## Development Runtime

Development should use a dev container:

- Source code bind-mounted.
- JDK/Gradle tooling inside the container.
- Gradle cache stored in a named Docker volume.
- Qdrant/Redis/Postgres exposed as compose services.
- Host SSH or provider credentials mounted only through local overrides.

## Deployment Runtime

Deployment should use Docker Compose:

- Immutable service images.
- Named volumes for durable data.
- `.env` for secrets and host-specific config.
- Cloudflared sidecar for public ingress.
- Restart policies on long-running services.
- Gem application tables use the `gem_os` database. LiteLLM keeps its own
  database so Prisma tables do not interfere with Gem Flyway migrations.

## What Belongs Outside Containers

- iOS simulator execution.
- Potentially Android emulator execution on Mac.
- Provider CLI login flows if they require local browser/device auth.

These should be exposed through a `host-runner` protocol later.

## MVP Compose Services

```text
postgres
redis
qdrant
bge-m3
litellm
provider-router
brain
slack-bot
scheduler
admin
prometheus
grafana
cloudflared
```

## Ports

- Admin: `8000`
- Brain: `8020`
- Provider router: `8010`
- LiteLLM: `4000`
- BGE-M3 embeddings: `7997` internal Compose service only,
  `/embeddings` endpoint, model `BAAI/bge-m3`, vector size `1024`
- Qdrant: `6333`
- Prometheus: `9090`
- Grafana: `3000`
- Postgres: internal only by default
- Redis: internal only by default
