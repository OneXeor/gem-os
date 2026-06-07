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
2. Better Slack chat replies.
3. Markdown durable notes.
4. Qdrant indexing.
5. Brain retrieval from Qdrant.
6. Redis dedup/cache.
