# ADR 0001: Dev Container First

## Status

Accepted

## Context

The old GEM system was useful but host-specific. Recreating it directly on a
Mac mini would preserve too many assumptions about installed Python versions,
paths, credentials, scheduler state, and local services.

## Decision

Gem OS will be designed dev-container first and Docker Compose first.

The development environment and deployable runtime should be reproducible from
the repo plus `.env` and secrets.

## Consequences

- Most services run in containers.
- iOS simulator and possibly Android emulator are treated as host-runner
  responsibilities.
- Provider CLI auth may need explicit host mounts or API-mode alternatives.
- The system can be moved to another Mac or Linux host with less drift.

