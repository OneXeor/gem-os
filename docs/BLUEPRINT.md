# Gem OS Blueprint

Gem OS is the orchestration layer. It receives requests, understands context,
chooses the right capability, creates a run graph, executes work through
pipelines/agents/tools, and reports the result.

## System Map

```mermaid
flowchart TB
    subgraph interfaces["Interfaces"]
        slack["Slack"]
        adminUi["Admin UI/API"]
        cron["Cron"]
        manual["Manual CLI/API"]
    end

    subgraph control["Gem Control Plane"]
        brain["Brain\ncontext + capability router"]
        runs["Run Graph\nparent + child runs"]
        scheduler["Scheduler / Worker"]
        providerRouter["Provider Router"]
    end

    subgraph context["Context Sources"]
        identity["Identity\nGem + Viktor"]
        projects["Project Registry"]
        memory["Memory Retrieval"]
        indexed["Knowledge Index\nrepos + docs + code + notes"]
    end

    subgraph capabilities["Capabilities"]
        aso["ASO Factory\nexternal pipeline"]
        indexing["Knowledge Indexing\nrepo/docs/code/Obsidian"]
        implementation["Implementation Workflow"]
        codex["Codex CLI\nnon-interactive"]
        claude["Claude Code\noptional disabled"]
        litellm["LiteLLM\nbounded chat gateway"]
        tools["Tools\nGitHub + checkout + host runners"]
    end

    subgraph storage["Storage"]
        postgres["Postgres\nruns + events + operational state"]
        qdrant["Qdrant\nvectors"]
        redis["Redis\nqueue/cache/locks"]
        files["Filesystem\nrepos + artifacts"]
    end

    subgraph observability["Observability"]
        prometheus["Prometheus"]
        grafana["Grafana"]
        slackReports["Slack Reports"]
        logs["Run Logs / Artifacts"]
    end

    slack --> brain
    cron --> scheduler
    manual --> brain
    adminUi --> runs

    identity --> brain
    projects --> brain
    memory --> brain
    indexed --> brain

    brain --> runs
    brain --> providerRouter
    brain --> scheduler
    runs --> scheduler

    scheduler --> aso
    scheduler --> indexing
    scheduler --> implementation
    providerRouter --> codex
    providerRouter --> claude
    providerRouter --> litellm
    scheduler --> tools

    aso --> files
    indexing --> qdrant
    indexing --> files
    runs --> postgres
    scheduler --> redis
    tools --> files

    postgres --> adminUi
    qdrant --> memory
    files --> logs
    runs --> logs

    brain --> slackReports
    adminUi --> prometheus
    brain --> prometheus
    providerRouter --> prometheus
    prometheus --> grafana
```

## Decision Flow

```mermaid
flowchart LR
    request["Slack / cron / manual request"]
    context["Build context\nuser + project + memory"]
    parent["Create parent run\nbrain_decision"]
    decide["Brain decides capability"]
    child["Create child run"]
    execute["Scheduler / provider executes"]
    persist["Persist logs, result, artifacts"]
    report["Report to Slack/Admin"]

    request --> context --> parent --> decide --> child --> execute --> persist --> report

    decide --> pipeline["pipeline"]
    decide --> codeAgent["code_agent"]
    decide --> planner["planner"]
    decide --> chat["chat"]
    decide --> tool["tool"]

    pipeline --> child
    codeAgent --> child
    planner --> child
    chat --> child
    tool --> child
```

## Knowledge Flow

```mermaid
flowchart LR
    discover["Discover sources\nGitHub + repos + Obsidian"]
    fetch["Fetch/update content"]
    chunk["Chunk docs/code/notes"]
    embed["Embed\nBGE-M3"]
    store["Store vectors\nQdrant"]
    retrieve["Retrieve context\nbefore planning"]
    brain["Brain"]

    discover --> fetch --> chunk --> embed --> store --> retrieve --> brain
```

## Main Rules

- Gem is not one LLM. Gem chooses and controls capabilities.
- Every meaningful action starts as a run.
- Brain decisions are parent runs; selected capabilities are child runs.
- Codex is the default non-interactive planner/code-agent path.
- Claude Code headless is optional and disabled by default.
- LiteLLM is for bounded chat/model calls, not code-agent execution.
- Qdrant stores indexed knowledge; Postgres stores operational truth.

## Near-Term Build Order

1. Scheduler executes child runs.
2. Knowledge indexing pipelines.
3. Brain retrieval from Qdrant.
4. Slack bot connected to Brain.
5. ASO Factory execution through scheduler.
6. Codex host-runner adapter.
