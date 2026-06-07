# Knowledge Indexing

Gem needs a continuously refreshed map of projects, repositories, docs, notes,
and selected code.

## Sources

- GitHub repositories from Viktor's account.
- GitHub repositories from Hopin.it accounts/orgs.
- Repository docs: `README.md`, `docs/`, `ADR`, `CLAUDE.md`, `AGENTS.md`.
- Selected active code repositories.
- Obsidian vaults, if used for personal/project notes.

## Pipelines

### Repository Catalog

Purpose: know what exists.

Inputs:
- GitHub account/org names.
- GitHub API token.

Outputs:
- Repository inventory.
- Repo metadata summaries.
- Project/repo mapping candidates.

Schedule:
- Daily.

### Documentation Index

Purpose: index high-signal project knowledge before code.

Inputs:
- Repo checkout.
- Allowed doc paths.

Outputs:
- Text chunks.
- Embeddings.
- Qdrant points with repo, branch, commit, path, section metadata.

Schedule:
- Daily or on manual request.

### Code Index

Purpose: help agents understand active codebases.

Inputs:
- Selected repositories only.
- Include/exclude path rules.

Outputs:
- File/symbol chunks.
- Embeddings.
- Qdrant points with repo, branch, commit, path, language, symbol metadata.

Schedule:
- Manual first, cron later for active repos.

### Obsidian Index

Purpose: use personal notes as context without making Obsidian the operational
database.

Inputs:
- Vault path.
- Include/exclude path rules.

Outputs:
- Note chunks.
- Embeddings.
- Qdrant points with vault, note path, tags, links metadata.

Schedule:
- Daily.

## Embeddings

Default local embedding model:
- BGE-M3.

Runtime:
- Docker Compose service: `bge-m3`.
- Serving engine: Infinity embeddings server.
- Internal endpoint: `http://bge-m3:7997`.
- Public/app endpoint: LiteLLM `/v1/embeddings` using model `bge-m3`.
- The service is not exposed through Cloudflare or host ports.
- On Apple Silicon, the current Infinity CPU image runs as `linux/amd64`
  under emulation because the image does not publish an ARM64 manifest.

The embedding provider should sit behind an interface:
- `local-bge-m3`
- future remote embedding providers if needed

## Storage

Qdrant collections:
- `gem_repos`
- `gem_docs`
- `gem_code`
- `gem_notes`

Each point should include:
- source type
- source id
- repo/vault
- branch
- commit SHA where applicable
- path
- chunk hash
- indexed timestamp

## Brain Usage

Before deciding or planning, Brain should retrieve context from Qdrant using:
- user text
- project hint
- resolved repo/project
- recent run history

Retrieved context is added to the planner prompt, but the run graph remains the
source of operational truth.
