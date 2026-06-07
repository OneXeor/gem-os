# Gem Memory

Gem uses four memory layers. Each layer has a different job.

```text
Slack message
  -> thread memory
  -> project/config memory
  -> vector knowledge
  -> Brain reply or run
  -> run history
```

## 1. Thread Memory

Short-term chat memory for Slack threads.

Stores:

```text
channel + thread_ts
recent messages
current topic
referenced project/run
```

Why:

```text
"run that" can mean "run the ASO monitor" from earlier in the thread.
```

Storage:

```text
Postgres
```

## Cross-Thread Continuity

Gem should be able to continue a topic from another Slack thread, but only
through explicit retrieval.

How it works:

```text
new Slack thread
  -> load Viktor profile + Gem identity
  -> search recent thread summaries
  -> search related runs
  -> retrieve durable notes / vector knowledge
  -> answer or ask for confirmation
```

Rules:

- Same Slack thread memory is automatic.
- Cross-thread memory is retrieved and ranked.
- If the match is ambiguous, Gem asks before assuming.
- Stable facts should be promoted to markdown notes or indexed knowledge.
- KV cache may cache retrieval results briefly, but it is not memory.

Example:

```text
Viktor: continue the ASO monitor idea from yesterday
Gem: I found the ASO monitor discussion from the previous thread. Continue from
the cron/indexing plan?
```

## 2. Run Memory

Operational history of what Gem did.

Stores:

```text
runs
child runs
events
status
errors
results
```

Why:

```text
Gem can answer what happened, what failed, and what should be retried.
```

Storage:

```text
Postgres
```

## 3. Knowledge Memory

Semantic memory for docs, repos, code, and notes.

Stores indexed chunks from:

```text
GitHub repos
README/docs
selected code
ASO notes
markdown/Obsidian notes
past decisions
```

Why:

```text
Gem can answer with project context instead of guessing.
```

Storage:

```text
Qdrant + BGE-M3 embeddings
model: BAAI/bge-m3
vector size: 1024
```

## 4. Durable Notes

Human-readable facts and decisions.

Examples:

```text
memory/projects/gem-os.md
memory/projects/aso-factory.md
memory/preferences/viktor.md
memory/decisions/2026-06-07-slack-socket-mode.md
```

Why:

```text
Important knowledge stays editable, versioned, and indexable.
```

Storage:

```text
Markdown in repo
```

## Redis

Redis is not memory source of truth.

Use it only for:

```text
Slack event deduplication
locks
rate limits
temporary cache
```

## Build Order

1. Postgres thread memory.
2. Thread summaries for cross-thread continuity.
3. Better Slack chat replies.
4. Markdown durable notes.
5. Qdrant indexing.
6. Brain retrieval from Qdrant.
7. Redis dedup/cache.
