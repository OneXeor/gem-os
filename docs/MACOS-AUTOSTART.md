# macOS Autostart

Gem OS should recover after a Mac mini reboot or short power outage.

Autostart has two layers:

1. Docker restart policy: long-running containers use `restart: unless-stopped`.
2. macOS LaunchAgent: starts Docker/Colima if needed and runs the Gem compose
   stack on login.

## Install

```bash
make macos-autostart-install
```

This installs:

```text
~/Library/LaunchAgents/com.onexeor.gem-os.plist
```

The LaunchAgent runs:

```text
ops/macos/start-gem-os.sh
```

## What It Starts

The script starts:

- `postgres`
- `redis`
- `qdrant`
- `litellm`
- `provider-router`
- `brain`
- `admin`
- `slack-bot`

It starts `cloudflared` only when `.env` contains a non-empty
`CLOUDFLARED_TUNNEL_TOKEN`.

## Logs

Startup logs:

```text
logs/startup.log
logs/launchd.out.log
logs/launchd.err.log
```

## Uninstall

```bash
make macos-autostart-uninstall
```

This removes the LaunchAgent. It does not remove Docker containers or volumes.
