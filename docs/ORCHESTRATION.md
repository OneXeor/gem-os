# Orchestration

Gem is the controller of the system.

Slack is an interface. Codex, Claude Code, LiteLLM, pipelines, tools, project
registries, memories, and host runners are capabilities. Gem decides which
capabilities to use for a task.

## Request Flow

```text
Slack request
  -> identify user/project/thread
  -> build Gem/Viktor/project context
  -> classify task
  -> choose capability
  -> create run
  -> execute pipeline/agent/tool/provider
  -> store logs/artifacts/results
  -> report back to Slack/admin
```

## Capability Types

- `pipeline`: repeatable workflows such as ASO monitoring.
- `code_agent`: non-interactive Codex or Claude Code sessions.
- `chat`: bounded model calls through LiteLLM.
- `tool`: deterministic internal or host-runner commands.
- `memory`: project/user/system context retrieval.

## Planner Role

Codex or Claude Code can be used as a planner when a request needs reasoning
over context. The planner does not own the system. It receives a structured
context package from Gem and returns a plan, selected capability, or execution
result depending on the task.

## Viktor Emulation

Gem should use Viktor's preferences, project history, notes, and prior decisions
to choose defaults and phrase responses. It should not pretend to be Viktor in
ways that hide automation. In Slack/admin logs, runs remain attributable to Gem
with the selected provider and capability recorded.

## Safety

- Long-running work always becomes a run.
- Code agents run non-interactively.
- Code agents run in explicit repos/worktrees/branches.
- Pipelines and agents stream logs and produce artifacts.
- Slack request handlers never execute heavy work directly.
- Human approval is required for risky or externally visible changes until
  explicitly relaxed per pipeline.
