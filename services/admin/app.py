from __future__ import annotations

from fastapi import FastAPI

from gem_core.config import load_config
from gem_core.health import Health

app = FastAPI(title="Gem OS Admin", version="0.1.0")


@app.get("/health")
def health() -> dict[str, str | float]:
    return Health(service="admin").as_dict()


@app.get("/status")
def status() -> dict[str, object]:
    cfg = load_config()
    return {
        "service": "admin",
        "env": cfg.settings.gem_env,
        "gem": cfg.identity.gem.name,
        "projects": len(cfg.projects.projects),
        "pipelines": len(cfg.pipelines.pipelines),
        "providers": {
            "chat_default": cfg.providers.chat.default,
            "code_agent_default": cfg.providers.code_agent.default,
        },
    }


@app.get("/projects")
def projects() -> dict[str, object]:
    cfg = load_config()
    return {"projects": [project.model_dump() for project in cfg.projects.projects]}


@app.get("/pipelines")
def pipelines() -> dict[str, object]:
    cfg = load_config()
    return {"pipelines": [pipeline.model_dump() for pipeline in cfg.pipelines.pipelines]}
