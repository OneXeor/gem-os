# Orchestration

Gem is the controller of the system.

Slack is an interface. Codex, LiteLLM, pipelines, tools, project
registries, memories, and host runners are capabilities. Gem decides which
capabilities to use for a task.

## Request Flow

```text
Slack request
  -> identify user/project/thread
  -> build Gem/Viktor/project context
  -> brain handles exact local commands or packages planner context
  -> planner LLM chooses semantic capability
  -> create run
  -> create child run for selected capability
  -> execute pipeline/agent/tool/provider
  -> store logs/artifacts/results
  -> report back to Slack/admin
```

## Capability Types

- `pipeline`: repeatable workflows such as ASO monitoring.
- `code_agent`: non-interactive Codex sessions by default; optional Claude Code
  headless sessions only when explicitly enabled and budgeted.
- `chat`: bounded model calls through LiteLLM.
- `tool`: deterministic internal or host-runner commands.
- `memory`: project/user/system context retrieval.

## Planner Role

Codex is the default planner when a request needs reasoning over context. The
planner does not own the system. It receives a structured context package from
Gem and returns a plan, selected capability, or execution result depending on
the task.

Brain should avoid large keyword decision trees. Outside of empty requests and
exact local commands such as `help`, `status`, `projects`, `runs`, `session`,
and `continue`, Brain delegates semantic routing to the planner. The planner
prompt includes configured projects, scripts/pipelines, recent Slack context,
and the allowed decision modes:

- direct answer
- configured default value
- one configured script/pipeline
- multi-script execution plan
- code-agent work
- deep investigation
- clarification only when necessary

Claude Code headless can be added as an explicit paid fallback later, but Gem
must not depend on it for default operation.

## Viktor Emulation

Gem should use Viktor's preferences, project history, notes, and prior decisions
to choose defaults and phrase responses. It should not pretend to be Viktor in
ways that hide automation. In Slack/admin logs, runs remain attributable to Gem
with the selected provider and capability recorded.

## Safety

- Long-running work always becomes a run.
- Brain decisions create parent runs; selected capabilities create child runs.
- Code agents run non-interactively.
- Code agents run in explicit repos/worktrees/branches.
- Pipelines and agents stream logs and produce artifacts.
- Slack request handlers never execute heavy work directly.
- Human approval is required for risky or externally visible changes until
  explicitly relaxed per pipeline.
