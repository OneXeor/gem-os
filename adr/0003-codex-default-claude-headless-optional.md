# ADR 0003: Codex Default, Claude Headless Optional

## Status

Accepted

## Context

Gem needs an LLM-backed planner and code-agent capability, but it should not
depend on an expensive or unstable headless provider path.

Anthropic documentation says that starting June 15, 2026, Claude Agent SDK and
`claude -p` usage on subscription plans draw from a separate monthly Agent SDK
credit pool rather than normal interactive usage limits.

## Decision

Codex CLI is the default non-interactive planner and code-agent path.

Claude Code headless remains modeled as a possible direct adapter, but it is
disabled by default and must be explicitly enabled with a budget decision.

LiteLLM remains the gateway for bounded chat/model calls, not code-agent
execution.

## Consequences

- MVP and default operation do not depend on Claude Code headless.
- Provider-router must expose whether optional adapters are enabled.
- Run records must capture provider, route, cost profile, and status.
- Claude Code can still be enabled later for tasks where its value justifies
  Agent SDK credit usage.
