# MVP

## Goal

Build the smallest useful Gem OS that can run continuously, talk through Slack,
route work to AI providers, and execute one measurable automation pipeline.

## Included

1. Portable runtime
   - Dev container for development.
   - Docker Compose for local deployment.
   - Persistent volumes for database, logs, and vector/cache stores.

2. Slack bot
   - DM support.
   - Mention support can follow after DM is stable.
   - Thread-scoped sessions.
   - Basic commands: `status`, `projects`, `run aso`, `logs`.

3. Identity and project awareness
   - Gem identity.
   - Viktor profile.
   - Hopin.it projects.
   - Personal projects.
   - Active project selection.

4. Provider routing
   - One abstraction for chat/model calls.
   - One abstraction for code-agent calls.
   - Initial providers: Codex and Claude as configured backends.
   - LiteLLM as shared model gateway for non-code-agent calls.

5. Admin web app
   - Service health.
   - Scheduler jobs.
   - Recent runs.
   - Logs.
   - Cost/token counters.
   - Project registry view.

6. ASO Fabric pipeline
   - Scheduled run.
   - Slack report.
   - Admin run page.
   - Stores inputs, outputs, hypotheses, and outcome metrics.

## Excluded From MVP

- Full autonomous code implementation.
- Pull request creation.
- iOS simulator automation.
- Android emulator automation.
- Multi-agent debate.
- Long-term autonomous memory extraction.
- Production-grade permissions beyond an allowlist.

## MVP Success Criteria

- `docker compose up` starts the stack on a clean machine after `.env` is filled.
- Slack DM `status` returns live service state.
- Slack DM `projects` lists configured projects.
- Slack DM `run aso` starts ASO Fabric and returns a run ID.
- Admin app shows the same run, logs, provider used, cost, and result.
- Scheduler can run ASO Fabric by cron.

