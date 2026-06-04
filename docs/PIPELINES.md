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

## First Pipeline: ASO Fabric

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

