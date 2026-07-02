# ADR 002: Stateless AI with Persistent Repository Memory

**Status:** Accepted  
**Date:** 2026-07-02

## Context

LLM inference via Ollama is stateless — each request is independent. Users need durable knowledge about a repository that survives restarts, model changes, and incremental file edits. Cloud-hosted memory services conflict with the offline-first goal.

## Decision

1. **Ollama is never treated as a memory store.** Each `complete()` or `embed()` call includes only the context retrieved at request time (summaries, graph snippets, source files from vector search).

2. **All durable state lives in `.ai-memory/`** inside each repository:
   - File index with content hashes
   - Knowledge graph JSON
   - AI summaries, documentation sections
   - Embedding cache (content-hash keyed)

3. **A global SQLite database** (`~/.projectmind/projectmind.db`) indexes metadata and graph edges for cross-session queries.

4. **Incremental updates** compare content hashes; unchanged files skip re-parse, re-embed, and re-summarize when size/mtime match.

## Consequences

**Positive:**
- Fully offline after initial model pull
- Repository memory is portable (copy `.ai-memory/` or use `export`)
- Model can be swapped in config without losing memory
- Git-friendly: `.ai-memory/` is gitignored by default

**Negative:**
- Disk usage grows with repository size
- First scan is slow (Ollama calls per file/chunk)
- No shared memory across machines without explicit export/import

## Alternatives considered

- **Ollama session / context window as memory** — rejected; not durable, limited context
- **Central cloud vector DB** — rejected; violates offline-first requirement
