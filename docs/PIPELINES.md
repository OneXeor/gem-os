# Pipelines

## Pipeline Contract

Every pipeline should follow the same lifecycle:

```text
scheduled/manual trigger
  -> load project config
  -> create run record
  -> collect inputs
  -> execute agent/tool steps
  -> persist artifacts
  -> compute status and metrics
  -> notify Slack
  -> expose in admin
```

## First Pipeline: ASO Factory

Repository:
- `git@github.com:OneXeor/ASO-Factory.git`
- Local checkout during development: `/Users/onexeor/src/ASO-Factory`

Gem owns orchestration, run tracking, Slack/admin visibility, and scheduling.
ASO Factory owns the ASO domain workflow, database schema, CLI commands,
agents, prompts, and App Store specific integrations.

Inputs:
- App/project config.
- Store listing metadata.
- Keyword rankings.
- Competitor metadata.
- Existing ASO hypotheses.
- Historical outcome metrics.

Outputs:
- Daily/weekly ASO report.
- Recommended metadata changes.
- Confidence and rationale.
- Experiments to run.
- Outcome analysis: helped, hurt, inconclusive.

Initial mode:
- Report-only.
- First integration command: `python -m aso_factory status --bundle
  it.hopin.motivation --locale en-US`.

Gem should call ASO Factory as an external CLI pipeline through a host runner or
dedicated scheduler worker. Gem should not copy ASO Factory logic into Gem OS.

Later modes:
- Draft metadata update.
- Create task/PR.
- Auto-submit only after explicit approval flow exists.

## Implementation Pipeline

Inputs:
- Task/ticket.
- Project registry.
- Repository context.
- Knowledge/memory.
- Provider policy.

Outputs:
- Plan.
- Branch/worktree.
- Code changes.
- Test/build results.
- Simulator/emulator artifacts when available.
- PR.
- Slack/admin report.

This comes after ASO and observability are stable.
