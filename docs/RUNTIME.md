# Runtime

## Development Runtime

Development should use a dev container:

- Source code bind-mounted.
- Python/Node/tooling inside the container.
- `.venv` stored in a named Docker volume.
- Qdrant/Redis/Postgres exposed as compose services.
- Host SSH or provider credentials mounted only through local overrides.

## Deployment Runtime

Deployment should use Docker Compose:

- Immutable service images.
- Named volumes for durable data.
- `.env` for secrets and host-specific config.
- Cloudflared sidecar for public ingress.
- Restart policies on long-running services.

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
litellm
slack-bot
scheduler
admin
cloudflared
```

## Ports

- Admin: `8000`
- LiteLLM: `4000`
- Qdrant: `6333`
- Postgres: internal only by default
- Redis: internal only by default

