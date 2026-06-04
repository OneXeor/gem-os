# Slack Interface

The Slack service is the first usable Gem operator interface.

## Runtime

Service:

```text
slack-bot
```

Local port:

```text
8030
```

Endpoints:

- `GET /health`
- `GET /metrics`
- `POST /slack/events`

## Environment

Required for real Slack usage:

```text
SLACK_BOT_TOKEN=xoxb-...
SLACK_SIGNING_SECRET=...
SLACK_ALLOWED_USERS=U...
```

Optional:

```text
SLACK_PORT=8030
BRAIN_BASE_URL=http://brain:8020
```

`SLACK_ALLOWED_USERS` is comma-separated. If it is empty, the bot accepts all
Slack users. For real workspace usage, keep it restricted.

## Slack App Setup

Use Slack Events API with the public tunnel URL:

```text
https://<your-tunnel-domain>/slack/events
```

Subscribe to bot events:

- `message.im`
- `app_mention`

Bot token scopes:

- `chat:write`
- `im:history`
- `app_mentions:read`

If the app is used in channels, invite the bot to the channel first.

## Local Run

Start the Slack service with Brain:

```bash
docker-compose --profile slack up -d brain slack-bot
```

Health check:

```bash
curl -fsS http://localhost:8030/health
```

For Slack to reach the local Mac mini, also run the tunnel profile after
`CLOUDFLARED_TUNNEL_TOKEN` is configured:

```bash
docker-compose --profile slack --profile tunnel up -d cloudflared
```

## Current Behavior

For each valid Slack message or app mention, Gem:

1. Verifies Slack request signing when `SLACK_SIGNING_SECRET` is configured.
2. Rejects users outside `SLACK_ALLOWED_USERS` when the allow-list is set.
3. Sends the text to Brain through `POST /decide`.
4. Creates a parent `brain_decision` run and a selected child run.
5. Replies in the Slack thread with run ID, decision, route, project, pipeline,
   provider, child run, and reason.

The Slack handler does not execute heavy work directly. It only talks to Brain
and returns the run information.
