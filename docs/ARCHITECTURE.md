# ProjectMind Architecture

## Overview

ProjectMind is a **stateless-AI, persistent-memory** system for software repositories. The Ollama model never retains state; all long-term knowledge lives on disk under `.ai-memory/` inside each repository.

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           User Interfaces                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────────────────┐  │
│  │  CLI (Picocli)│  │  REST API    │  │  Generated HTML Documentation │  │
│  └──────┬───────┘  └──────┬───────┘  └──────────────────────────────┘  │
└─────────┼─────────────────┼────────────────────────────────────────────┘
          │                 │
          ▼                 ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                      Application Layer (Use Cases)                      │
│  ScanRepository │ UpdateRepository │ GenerateDocs │ AskQuestion │ ...  │
└─────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                         Domain Ports (Interfaces)                         │
│ Scanner │ Parser │ DependencyAnalyzer │ KnowledgeGraph │ MemoryManager │
│ VectorIndex │ OllamaClient │ DocGenerator │ HtmlGenerator │ Plugins    │
└─────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Infrastructure Adapters (Implementations)            │
│ TreeSitterParser │ ChromaVectorStore │ SQLiteMetadataStore │ ...       │
└─────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     Persistent Storage (.ai-memory/)                    │
│ project.json │ repository_index.json │ dependency_graph.json │ ...     │
└─────────────────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    Ollama HTTP API (Local, Stateless)                   │
│                    Qwen2.5-Coder-14B-Instruct                           │
└─────────────────────────────────────────────────────────────────────────┘
```

## Design Principles

| Principle | Application |
|-----------|-------------|
| **Clean Architecture** | Domain has no framework dependencies. Application depends on ports. Adapters implement ports. |
| **Dependency Injection** | Spring Boot wires all components. Interfaces enable testing and swapping implementations. |
| **Stateless AI** | Ollama is invoked per-request with retrieved context only. No session state in the model. |
| **Offline First** | All storage is local: JSON, SQLite, ChromaDB (local instance). No cloud dependencies. |
| **Incremental Updates** | File hashes drive selective re-indexing, re-embedding, and partial doc regeneration. |
| **Modularity** | Each capability is a separate package/module with a well-defined port interface. |

## Maven Module Structure

```
projectmind/
├── pom.xml                           # Parent POM
├── projectmind-core/                 # Domain models + port interfaces
├── projectmind-application/          # Use case orchestration
├── projectmind-adapters/             # Infrastructure implementations
├── projectmind-plugin-spring-boot/   # Spring Boot SPI plugin
└── projectmind-bootstrap/            # Spring Boot, REST API, CLI entry points
```

### Module Dependencies

```
bootstrap → application → core
bootstrap → adapters → core
adapters  → application (optional, for shared DTOs)
```

## Package Organization

```
com.projectmind
├── core
│   ├── domain          # Entities, value objects, enums
│   └── port            # Interface contracts (ScannerPort, ParserPort, ...)
├── application
│   ├── service         # Use case implementations
│   └── dto             # Application-level data transfer objects
├── adapter
│   ├── scanner         # RepositoryScanner
│   ├── parser          # TreeSitterJavaParser
│   ├── dependency      # DependencyAnalyzer
│   ├── knowledge       # KnowledgeGraphBuilder
│   ├── memory          # MemoryManager (JSON + SQLite)
│   ├── vector          # ChromaVectorIndex
│   ├── ollama          # OllamaHttpClient
│   ├── docs            # DocumentationGenerator
│   ├── html            # HtmlGenerator
│   ├── incremental     # IncrementalUpdateEngine
│   ├── config          # ConfigurationManager
│   ├── plugin          # PluginRegistry
│   ├── cache           # FileCache
│   ├── metrics         # MetricsCollector
│   └── logging         # Structured logging helpers
├── api                 # REST controllers
└── cli                 # Picocli commands
```

## Persistent Memory Layout

Each analyzed repository contains:

```
<repo>/.ai-memory/
├── project.json                 # Project metadata, scan timestamps, config
├── repository_index.json        # File inventory with content hashes
├── dependency_graph.json        # Inter-component relationships
├── embeddings/                  # Vector embedding metadata
├── summaries/                   # Per-file AI summaries
├── package_summaries/
├── class_summaries/
├── api_summaries/
├── diagrams/                    # Mermaid diagram sources
├── documentation/               # Generated markdown sections
├── history/                     # Change history snapshots
└── cache/                       # Temporary computation cache
```

Additionally, a global SQLite database stores cross-project metadata and queryable graph edges.

## Data Flow: Initial Scan

```
1. CLI/API receives repository path
2. RepositoryScanner walks filesystem (respecting skip rules)
3. For each source file:
   a. LanguageParser extracts AST structures (Tree-sitter)
   b. DependencyAnalyzer builds import/call relationships
   c. OllamaClient generates file summary (batched)
   d. VectorIndex embeds summary + code chunks
   e. MemoryManager persists all artifacts to .ai-memory/
4. KnowledgeGraphBuilder aggregates relationships
5. DocumentationGenerator produces all sections
6. HtmlGenerator renders final documentation site
```

## Data Flow: Incremental Update

```
1. RepositoryScanner computes file hashes
2. IncrementalUpdateEngine diffs against repository_index.json
3. For changed/new files: re-parse, re-summarize, re-embed
4. For deleted files: remove from index, graph, vectors
5. KnowledgeGraphBuilder updates affected edges only
6. DocumentationGenerator regenerates affected sections only
```

## Data Flow: Ask Question

```
1. User question → VectorIndex semantic search
2. Retrieve top-K relevant file summaries + source paths
3. Load actual source code for top matches
4. Load related knowledge graph context
5. Build prompt with retrieved context (no model memory)
6. OllamaClient generates answer
7. Return answer with source citations
```

## Knowledge Graph Schema

Nodes: `Package`, `Class`, `Interface`, `Enum`, `Method`, `Configuration`, `Entity`, `Controller`, `Service`, `Repository`, `Filter`, `Database`

Edges: `IMPORTS`, `EXTENDS`, `IMPLEMENTS`, `CALLS`, `INJECTS`, `MAPS_TO`, `SECURES`, `CONFIGURES`

All edges stored in `dependency_graph.json` and indexed in SQLite for querying.

## REST API Endpoints

See [API.md](API.md) for the full reference. Swagger UI: `http://localhost:8080/swagger-ui.html`

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/plugins` | List discovered plugins |
| GET | `/api/v1/status?path=` | Repository scan status |
| POST | `/api/v1/scan` | Trigger full scan |
| POST | `/api/v1/resume` | Resume interrupted scan |
| POST | `/api/v1/update` | Incremental update |
| POST | `/api/v1/docs` | Generate documentation |
| POST | `/api/v1/export` | Export `.ai-memory` |
| POST | `/api/v1/ask` | Ask AI about repository |
| GET | `/api/v1/memory?path=` | Memory overview |
| GET | `/api/v1/history?path=` | Change history |
| GET | `/api/v1/graph?path=` | Knowledge graph query |
| GET | `/api/v1/graph/mermaid?path=` | Mermaid diagram |
| DELETE | `/api/v1/cache?path=` | Clear cache |
| DELETE | `/api/v1/memory?path=` | Delete all memory |

## CLI Commands

| Command | Description |
|---------|-------------|
| `projectmind scan <path>` | Full repository scan |
| `projectmind update <path>` | Incremental update |
| `projectmind docs <path>` | Generate documentation |
| `projectmind ask <path> "<question>"` | Ask about repository |
| `projectmind clean <path>` | Remove .ai-memory |
| `projectmind status <path>` | Show scan status |
| `projectmind resume <path>` | Resume interrupted scan |
| `projectmind export <path>` | Export memory bundle |

## Plugin System

Plugins implement `ProjectMindPlugin` and register via Java SPI. See [PLUGIN_GUIDE.md](PLUGIN_GUIDE.md).

Built-in plugin: **spring-boot** (`projectmind-plugin-spring-boot`) — Spring stereotype detection, Feign clients, layer-flow edges.

## Technology Choices

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| Runtime | Java 17 | LTS baseline; records, sealed types, pattern matching |
| Framework | Spring Boot 3.4 | DI, REST, configuration, testing |
| Build | Maven | Multi-module, widely adopted |
| CLI | Picocli | Type-safe, Spring Boot integration |
| Parser | Tree-sitter (Java binding) | Accurate AST, multi-language ready |
| Vector Store | ChromaDB (HTTP) | Local, embeddable, standard API |
| Metadata DB | SQLite | Zero-config, portable, queryable |
| AI | Ollama HTTP API | Local, offline, model-agnostic |
| Docs | Mermaid + Highlight.js | Rich diagrams and syntax highlighting |
| Logging | SLF4J + Logback | Structured JSON logging |
| Metrics | Micrometer | Spring Boot native metrics |

## Performance Strategy

- **Parallel hashing** — scan uses `ParallelExecutor` (virtual threads on Java 21+)
- **Parallel parsing, embedding, summarization** — configurable concurrency limits
- **Content-hash caching** — skip unchanged files; embedding/summary cache in `.ai-memory/cache/`
- **Checkpoint throttling** — scan progress persisted every N batches
- **In-memory hot cache** — LRU layer in `JsonMemoryManager` during indexing runs

See `projectmind.performance.*` settings in `application.yml`.

## Security Considerations

- All data stays local; no external network except Ollama (localhost)
- `.ai-memory/` should be gitignored by default (configurable)
- API runs on localhost by default
- No credentials stored; Ollama has no auth in local mode

## Testing Strategy

| Layer | Test Type | Tools |
|-------|-----------|-------|
| Domain | Unit | JUnit 5, AssertJ |
| Application | Unit + Integration | Mockito, @SpringBootTest |
| Adapters | Integration | Testcontainers (optional), temp dirs |
| API | Integration | MockMvc, WebTestClient |
| CLI | Integration | Picocli test harness |

## Configuration

`application.yml` + environment variables:

```yaml
projectmind:
  ollama:
    base-url: http://localhost:11434
    model: qwen2.5-coder:14b-instruct
    embed-model: nomic-embed-text
  vector:
    chroma-url: http://localhost:8000
    backend: auto
  memory:
    global-db: ~/.projectmind/projectmind.db
    cache-enabled: true
  scan:
    batch-size: 100
    skip-dirs: [.git, target, build, node_modules, dist, out, .ai-memory]
  plugins:
    enabled: true
  performance:
    ollama-embed-concurrency: 4
    summary-concurrency: 4
  docs:
    output-dir: documentation
```
