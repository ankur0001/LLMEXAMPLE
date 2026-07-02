# ProjectMind

Persistent AI memory system for software repositories using locally running [Ollama](https://ollama.com).

ProjectMind scans repositories, builds structured knowledge on disk, and lets you ask questions about your codebase — all completely offline. The AI model stays stateless; long-term memory lives in `.ai-memory/` inside each repository.

## Features

- **Repository scanning** — file inventory, content hashes, resume after interruption
- **AST parsing** — Tree-sitter for Java, Kotlin, YAML, Maven, SQL, and more
- **Knowledge graph** — imports, calls, injections, Spring layer detection (via plugin)
- **Vector search** — semantic retrieval with Ollama embeddings (ChromaDB or in-memory)
- **Documentation site** — 21-section HTML docs with Mermaid diagrams
- **Incremental updates** — hash-based diff; only changed files are re-processed
- **CLI + REST API** — same operations via terminal or HTTP
- **Plugin system** — SPI-based framework extensions (Spring Boot plugin included)

## Requirements

| Component | Version | Required |
|-----------|---------|----------|
| Java | 17+ | Yes |
| Maven | 3.9+ | Yes |
| Ollama | latest | For summaries, Q&A, embeddings |
| ChromaDB | latest | Optional (in-memory fallback) |

### Ollama setup

```bash
# Install and start Ollama, then pull any models you prefer:
ollama pull phi3              # example completion model
ollama pull nomic-embed-text  # example embedding model
```

ProjectMind **auto-selects** from whatever models you have installed. Configured names in `application.yml` are optional preferences, not requirements:

```yaml
projectmind:
  ollama:
    model: auto          # or a preferred model name
    embed-model: auto    # or a preferred embedding model
```

Ollama must be reachable at `http://localhost:11434` (configurable).

## Quick start

### 1. Build

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # macOS example
mvn clean install
```

### 2. Scan a repository

**CLI:**

```bash
java -jar projectmind-bootstrap/target/projectmind-bootstrap-0.1.0-SNAPSHOT.jar \
  scan /path/to/your/repo
```

**REST:**

```bash
# Start the server (no args → web mode on port 8080)
java -jar projectmind-bootstrap/target/projectmind-bootstrap-0.1.0-SNAPSHOT.jar

curl -X POST http://localhost:8080/api/v1/scan \
  -H "Content-Type: application/json" \
  -d '{"path":"/path/to/your/repo"}'
```

### 3. Ask a question

```bash
java -jar projectmind-bootstrap/target/projectmind-bootstrap-0.1.0-SNAPSHOT.jar \
  ask /path/to/your/repo "How does authentication work?"

# Stream tokens as they arrive:
java -jar projectmind-bootstrap/target/projectmind-bootstrap-0.1.0-SNAPSHOT.jar \
  ask /path/to/your/repo "Explain the service layer" --stream
```

### 4. Generate documentation

```bash
java -jar projectmind-bootstrap/target/projectmind-bootstrap-0.1.0-SNAPSHOT.jar \
  docs /path/to/your/repo
# Output: <repo>/.ai-memory/documentation/index.html
```

## CLI reference

| Command | Description |
|---------|-------------|
| `scan <path>` | Full repository scan and memory build |
| `resume <path>` | Resume an interrupted scan |
| `update <path>` | Incremental update (hash diff) |
| `docs <path>` | Generate HTML documentation |
| `ask <path> "<question>"` | Q&A with source citations (`--stream` optional) |
| `status <path>` | Scan status (`-v` for memory overview) |
| `export <path> [outDir]` | Export `.ai-memory` bundle |
| `clean <path>` | Delete all persisted memory |

Run without subcommands to start the web UI and REST API on port 8080.

**Web UI:** [http://localhost:8080](http://localhost:8080) — scan, ask, graph, docs, and all actions in the browser.

## REST API

Base URL: `http://localhost:8080/api/v1`

Interactive docs: [Swagger UI](http://localhost:8080/swagger-ui.html)

See [docs/API.md](docs/API.md) for the full endpoint reference.

## Project structure

```
projectmind-core/                 Domain models + port interfaces
projectmind-application/          Use case orchestration
projectmind-adapters/             Infrastructure (scanner, parser, Ollama, memory, vector)
projectmind-plugin-spring-boot/   Built-in Spring Boot SPI plugin
projectmind-bootstrap/            Spring Boot app, REST API, CLI
docs/                             Architecture, API, plugin guide, ADRs
```

## Persistent memory

Each scanned repository gets a `.ai-memory/` directory (add to `.gitignore`):

```
.ai-memory/
├── project.json              # Metadata, scan status, timestamps
├── repository_index.json     # File inventory + content hashes
├── dependency_graph.json     # Knowledge graph nodes and edges
├── summaries/                # Per-file AI summaries
├── documentation/            # Generated HTML site
├── diagrams/                 # Mermaid diagram sources
├── history/                  # Change snapshots
└── cache/                    # Embedding and summary cache
```

A global SQLite database at `~/.projectmind/projectmind.db` indexes cross-project metadata.

## Configuration

Key settings in `projectmind-bootstrap/src/main/resources/application.yml`:

```yaml
projectmind:
  ollama:
    base-url: http://localhost:11434
    model: auto          # auto | preferred completion model name
    embed-model: auto    # auto | preferred embedding model name
  vector:
    backend: auto          # auto | memory | chroma
  plugins:
    enabled: true
  performance:
    ollama-embed-concurrency: 4
    summary-concurrency: 4
```

Override at runtime with Spring Boot external config or environment variables (e.g. `PROJECTMIND_OLLAMA_BASE_URL`).

## Documentation

| Document | Description |
|----------|-------------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | System design and data flows |
| [docs/API.md](docs/API.md) | REST API reference |
| [docs/PLUGIN_GUIDE.md](docs/PLUGIN_GUIDE.md) | Writing custom plugins |
| [docs/adr/](docs/adr/) | Architecture decision records |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Development workflow |
| [ROADMAP.md](ROADMAP.md) | Phase progress tracker |

## Development

```bash
mvn clean install          # Build + run all tests
mvn -pl projectmind-adapters test -Dtest=PerformanceBenchmarkTest  # Benchmarks
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for coding standards and PR process.

## License

MIT


# One-time (while online) — refreshes Lib with test deps
./scripts/populate-lib.sh

# Offline build
mvn -s .mvn/settings-offline.xml clean install
