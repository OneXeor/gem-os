from __future__ import annotations

import os

from gem_core.config import load_config


def status_text() -> str:
    cfg = load_config()
    return (
        f"{cfg.identity.gem.name} is online. "
        f"Projects: {len(cfg.projects.projects)}. "
        f"Pipelines: {len(cfg.pipelines.pipelines)}."
    )


def main() -> int:
    # Stage 1 stub: later this becomes a Slack Bolt Socket Mode app.
    token_state = "configured" if os.environ.get("SLACK_BOT_TOKEN") else "missing"
    app_token_state = "configured" if os.environ.get("SLACK_APP_TOKEN") else "missing"
    print(status_text())
    print(f"Slack bot token: {token_state}; app token: {app_token_state}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
