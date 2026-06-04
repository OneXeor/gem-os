# ASO Factory Integration

ASO Factory is the external ASO pipeline engine.

Repository:
- `git@github.com:OneXeor/ASO-Factory.git`
- Local checkout: `/Users/onexeor/src/ASO-Factory`

Gem OS should treat it as a capability, not rewrite it.

## Boundary

Gem OS owns:
- Slack request handling.
- Brain decisions.
- Parent/child run graph.
- Scheduling.
- Admin visibility.
- Cross-project orchestration.

ASO Factory owns:
- ASO app registration.
- Research pipeline.
- Review mining.
- Positioning hypotheses.
- Keyword generation/scoring.
- Metadata drafting and critic reports.
- Its own domain database and migrations.

## First Command

The first safe command for Gem to invoke is read-only/report-like:

```bash
python -m aso_factory status --bundle it.hopin.motivation --locale en-US
```

This maps to Gem pipeline:

```text
project: aso-fabric
pipeline: aso-monitor
execution.type: external-cli
```

## Next Integration Step

The scheduler should consume the `pipeline` child run created by Brain, execute
the configured ASO Factory command, capture stdout/stderr, and update the child
run status/events.
