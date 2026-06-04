from __future__ import annotations

import os

import httpx
from fastapi import FastAPI

from gem_core.config import load_config
from gem_core.health import Health

app = FastAPI(title="Gem OS Provider Router", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str | float]:
    return Health(service="provider-router").as_dict()


@app.get("/providers")
def providers() -> dict[str, object]:
    cfg = load_config()
    return {
        "chat": cfg.providers.chat.model_dump(),
        "code_agent": cfg.providers.code_agent.model_dump(),
    }


@app.get("/providers/litellm/health")
def litellm_health() -> dict[str, object]:
    cfg = load_config()
    base_url = os.environ.get("LITELLM_BASE_URL", cfg.settings.litellm_base_url)
    try:
        response = httpx.get(f"{base_url.rstrip('/')}/health/liveliness", timeout=3)
        return {
            "status": "ok" if response.is_success else "degraded",
            "base_url": base_url,
            "http_status": response.status_code,
        }
    except Exception as exc:
        return {"status": "unreachable", "base_url": base_url, "error": str(exc)}
