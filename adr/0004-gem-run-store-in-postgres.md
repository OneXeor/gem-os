# ADR 0004: Gem Run Store In Postgres

## Status

Accepted

## Context

Gem needs durable run records before Slack, pipelines, and code agents execute
real work. The local Compose stack already includes Postgres for LiteLLM.

## Decision

Gem stores run metadata and run events in Postgres using Flyway migrations and
a small JDBC repository.

For the current local runtime, Gem uses the same Postgres database as LiteLLM
and enables Flyway `baselineOnMigrate` because LiteLLM already creates tables in
the public schema.

## Consequences

- Brain decisions are persisted before execution.
- Admin can inspect runs and events.
- This is acceptable for local Stage 1/2 work.
- Before production hardening, Gem and LiteLLM should use separate schemas or
  separate databases to avoid ownership ambiguity.
