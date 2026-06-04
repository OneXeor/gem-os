# ADR 0002: ASO Pipeline Before Implementation Agents

## Status

Accepted

## Context

Implementation agents are high-impact and need strong observability, provider
routing, project context, and safety controls before they can be trusted.

ASO automation is bounded, cron-friendly, and measurable. It can prove the
platform without touching production code.

## Decision

The first real pipeline will be ASO Fabric in report-only mode.

Implementation agents will be added after Slack, admin, scheduler, provider
routing, identity, project registry, and ASO runs are stable.

## Consequences

- MVP delivers useful automation earlier.
- The system's learning and measurement loop is tested before code-writing.
- Implementation pipeline design can reuse mature run/log/cost infrastructure.

