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

## Thread Sessions

Gem treats every Slack thread as a persistent session.

```text
channel + thread_ts
  -> active Gem session
  -> transcript messages
  -> current run / route / project
```

On every accepted Slack message, the bot should:

1. Find or create the session for `channel + thread_ts`.
2. Store the user message in `slack_thread_messages`.
3. Load recent messages from the same session.
4. Send the request plus context to Brain.
5. Store Gem's reply.
6. Update the session with the latest run, decision, route, and project.

Thread commands:

These are plain Slack messages, not Slack slash commands yet.

- `session` shows the current session state.
- `reset session` closes the current session and starts fresh.
- `continue` means continue using the active thread context.
- `help` shows available text commands.
- `status` shows runtime assumptions.
- `projects` lists configured projects.
- `runs` shows run history status.

Short follow-up questions like `what do you mean?` and `why?` should be
answered from recent thread context. Today these are deterministic replies; the
general LLM chat fallback is still a later step.

Complex planning or code-agent requests can be handed to Codex through the
host runner when `CODEX_RUNNER_BASE_URL` is configured. Slack receives status
updates in the same thread while the run executes.

Run progress follows the work Gem pattern in a small Slack module:

- `SlackApiClient` owns Slack Web API calls.
- `SlackRunStatusReporter` owns Assistant thread status and the live heartbeat
  message.
- `CodexRunExecutor` owns Codex queued/running/completed/failed lifecycle and
  starts/stops the reporter around long-running runs.
- `SlackEventHandler` handles Slack events, memory, Brain decisions, and run
  delegation.

Live external data questions, such as weather, current prices, or latest news,
must not be routed to Codex. They need a dedicated live-data provider; until
that exists Gem should answer that the capability is not wired.

## Environment

Required for real Slack usage:

```text
SLACK_BOT_TOKEN=xoxb-...
SLACK_APP_TOKEN=xapp-...
SLACK_SOCKET_MODE=true
SLACK_SIGNING_SECRET=...
SLACK_REQUIRE_SIGNATURE=true
SLACK_ALLOWED_USERS=U...
```

Optional:

```text
SLACK_PORT=8030
BRAIN_BASE_URL=http://brain:8020
```

If Slack Socket Mode is enabled in the Slack app settings, set
`SLACK_SOCKET_MODE=true`. Slack will then deliver events over WebSocket using
`SLACK_APP_TOKEN` instead of calling `/slack/events`.

`SLACK_ALLOWED_USERS` is comma-separated. If it is empty, the bot accepts all
Slack users. For real workspace usage, keep it restricted.

`SLACK_REQUIRE_SIGNATURE` defaults to `true`. Keep it enabled for any public
tunnel. Disable it only for local fake-event tests.

## Public Tunnel Security

For public access through Cloudflare, use these minimum controls:

- Keep only `/slack/events` as the Slack Request URL.
- Keep `SLACK_SIGNING_SECRET` configured.
- Keep `SLACK_REQUIRE_SIGNATURE=true`.
- Set `SLACK_ALLOWED_USERS` to Viktor's Slack user ID.
- Do not expose admin, provider-router, LiteLLM, Grafana, or Prometheus through
  the public tunnel.
- Use Cloudflare Access for human/admin URLs later; do not put Cloudflare Access
  in front of `/slack/events`, because Slack cannot complete an interactive
  Access login.

## Slack App Setup

### Socket Mode

If Socket Mode is enabled, create an app-level token with:

```text
connections:write
```

Copy the `xapp-...` token into:

```text
SLACK_APP_TOKEN=...
SLACK_SOCKET_MODE=true
```

Slack will ignore the public Request URL for event delivery while Socket Mode
is enabled.

### HTTP Events API

If Socket Mode is disabled, use Slack Events API with the public tunnel URL:

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
