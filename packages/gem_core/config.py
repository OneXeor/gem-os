from __future__ import annotations

import os
from pathlib import Path
from typing import Any, Literal

import yaml
from pydantic import BaseModel, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    gem_home: Path = Field(default_factory=lambda: Path(os.environ.get("GEM_HOME", ".")).resolve())
    gem_env: str = "local"
    admin_host: str = "0.0.0.0"
    admin_port: int = 8000
    provider_router_host: str = "0.0.0.0"
    provider_router_port: int = 8010
    database_url: str = "postgresql+psycopg://gem:gem@postgres:5432/gem"
    redis_url: str = "redis://redis:6379/0"
    qdrant_url: str = "http://qdrant:6333"
    litellm_base_url: str = "http://litellm:4000"


class GemIdentity(BaseModel):
    name: str = "Gem"
    role: str
    principles: list[str] = Field(default_factory=list)


class UserProfile(BaseModel):
    display_name: str
    slack_user_ids: list[str] = Field(default_factory=list)
    timezone: str = "Europe/Warsaw"
    preferences: dict[str, Any] = Field(default_factory=dict)


class IdentityConfig(BaseModel):
    gem: GemIdentity
    users: dict[str, UserProfile] = Field(default_factory=dict)


class ProviderPolicy(BaseModel):
    default_chat_provider: str = "litellm"
    default_code_provider: str = "codex"


class ProjectConfig(BaseModel):
    id: str
    name: str
    owner: str
    type: Literal["work", "personal"]
    status: str = "planned"
    aliases: list[str] = Field(default_factory=list)
    repos: list[str] = Field(default_factory=list)
    pipelines: list[str] = Field(default_factory=list)
    provider_policy: ProviderPolicy = Field(default_factory=ProviderPolicy)


class ProjectsConfig(BaseModel):
    projects: list[ProjectConfig] = Field(default_factory=list)


class ChatProviderOption(BaseModel):
    base_url_env: str | None = None
    api_key_env: str | None = None
    default_model: str = "default"


class CodeProviderOption(BaseModel):
    mode: str
    default_model: str = "latest"


class ChatProviders(BaseModel):
    default: str = "litellm"
    options: dict[str, ChatProviderOption] = Field(default_factory=dict)


class CodeAgentProviders(BaseModel):
    default: str = "codex"
    options: dict[str, CodeProviderOption] = Field(default_factory=dict)


class ProvidersConfig(BaseModel):
    providers: dict[str, Any] = Field(default_factory=dict)

    @property
    def chat(self) -> ChatProviders:
        return ChatProviders.model_validate(self.providers.get("chat", {}))

    @property
    def code_agent(self) -> CodeAgentProviders:
        return CodeAgentProviders.model_validate(self.providers.get("code_agent", {}))


class PipelineSchedule(BaseModel):
    cron: str
    timezone: str = "Europe/Warsaw"


class PipelineProvider(BaseModel):
    chat: str | None = None
    code_agent: str | None = None


class PipelineSafety(BaseModel):
    require_branch: bool = False
    require_tests: bool = False
    require_human_approval_for_pr: bool = True


class PipelineOutputs(BaseModel):
    slack: bool = True
    admin: bool = True


class PipelineConfig(BaseModel):
    id: str
    name: str
    stage: str
    enabled: bool = False
    project_ids: list[str] = Field(default_factory=list)
    schedule: PipelineSchedule | None = None
    mode: str = "manual-only"
    provider: PipelineProvider = Field(default_factory=PipelineProvider)
    outputs: PipelineOutputs = Field(default_factory=PipelineOutputs)
    safety: PipelineSafety = Field(default_factory=PipelineSafety)


class PipelinesConfig(BaseModel):
    pipelines: list[PipelineConfig] = Field(default_factory=list)


class GemConfig(BaseModel):
    settings: Settings
    identity: IdentityConfig
    projects: ProjectsConfig
    providers: ProvidersConfig
    pipelines: PipelinesConfig


def _read_yaml(path: Path) -> dict[str, Any]:
    if not path.exists():
        raise FileNotFoundError(f"Missing config file: {path}")
    with path.open("r", encoding="utf-8") as handle:
        return yaml.safe_load(handle) or {}


def _config_path(gem_home: Path, name: str) -> Path:
    real = gem_home / "config" / f"{name}.yaml"
    if real.exists():
        return real
    return gem_home / "config" / f"{name}.example.yaml"


def load_config(gem_home: str | Path | None = None) -> GemConfig:
    settings = Settings()
    root = Path(gem_home).resolve() if gem_home else settings.gem_home
    return GemConfig(
        settings=settings.model_copy(update={"gem_home": root}),
        identity=IdentityConfig.model_validate(_read_yaml(_config_path(root, "identity"))),
        projects=ProjectsConfig.model_validate(_read_yaml(_config_path(root, "projects"))),
        providers=ProvidersConfig.model_validate(_read_yaml(_config_path(root, "providers"))),
        pipelines=PipelinesConfig.model_validate(_read_yaml(_config_path(root, "pipelines"))),
    )
