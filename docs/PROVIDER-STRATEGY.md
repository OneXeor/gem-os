# Provider Strategy

Gem is an orchestrator. It understands identity, projects, memory, pipelines,
tools, and available agents, then chooses the correct execution path for each
task.

No single provider is "Gem." Codex, Claude Code, LiteLLM, pipelines, and future
agents are capabilities Gem can leverage.

## Slack/Gem Orchestration Path

```text
Slack -> Gem context builder -> capability router -> pipeline / agent / tool / provider
```

The router should consider:
- User identity and preferences.
- Project and repo context.
- Available pipelines.
- Available direct agents.
- Required tools.
- Cost and latency.
- Safety level.
- Whether the task needs a durable run ID, branch, worktree, or cron schedule.

Planner defaults:
- primary planner: `codex`
- fallback planner: `claude`
- mode: capability router

The prompt/context for any selected agent must include Gem identity, Viktor
profile, project context, relevant memory/notes, task instructions, safety
constraints, and expected output format.

## Chat Path

Cheap, bounded chat, summarization, classification, and routing workloads can
go through LiteLLM when repo-level code-agent execution is unnecessary.

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
