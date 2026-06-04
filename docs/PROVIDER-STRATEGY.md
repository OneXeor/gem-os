# Provider Strategy

Gem uses two provider paths.

## Chat Path

Chat, summarization, classification, and normal prompt/response workloads go
through LiteLLM.

Gem services know only:

```text
LITELLM_BASE_URL
LITELLM_API_KEY
```

Provider keys, model aliases, and chat fallback policy belong to LiteLLM.

## Code-Agent Path

Code-writing sessions bypass LiteLLM.

Codex and Claude Code run through direct non-interactive adapters because they
need repo context, worktree control, command streaming, logs, permissions, and
different cost controls than normal chat completions.

Required behavior:
- Non-interactive execution only.
- Explicit project/repo/worktree.
- Explicit branch.
- Captured stdout/stderr and artifacts.
- Run ID for every session.
- No execution directly inside Slack request handling.

Configured adapters:
- `codex`: direct CLI, host auth, non-interactive.
- `claude`: direct CLI, host auth, non-interactive.

LiteLLM remains the chat gateway, not the code-agent runtime.
