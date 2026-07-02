# Contributing to ProjectMind

Thank you for your interest in contributing. This guide covers local setup, coding conventions, and the pull request process.

## Prerequisites

- Java 17+
- Maven 3.9+
- Ollama (for integration tests involving LLM/embeddings)
- Git

## Getting started

```bash
git clone <repository-url>
cd LLM_EXAMPLE
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # adjust for your OS
mvn clean install
```

All modules must pass tests before submitting a PR:

```bash
mvn clean install
```

## Project layout

| Module | Purpose | May depend on |
|--------|---------|---------------|
| `projectmind-core` | Domain + ports | Nothing (Jackson annotations only) |
| `projectmind-application` | Use cases | `core` |
| `projectmind-adapters` | Port implementations | `core` |
| `projectmind-plugin-*` | SPI plugins | `core` only |
| `projectmind-bootstrap` | App entry, REST, CLI | All of the above |

**Clean architecture rule:** `application` must not depend on `adapters` at compile time. Adapters are wired at runtime via Spring in `bootstrap`.

## Coding conventions

### General

- Match existing naming, formatting, and package structure
- Keep changes focused — one concern per PR
- Prefer extending ports over adding framework code to use cases
- Comments only for non-obvious logic

### Java

- Use Java 17 features (records, `var` sparingly, pattern matching where clear)
- Port interfaces live in `com.projectmind.core.port`
- Domain types live in `com.projectmind.core.domain`
- No Spring annotations in `core` or `application`

### Tests

- Unit tests: JUnit 5 + AssertJ (+ Mockito where needed)
- REST tests: `@WebMvcTest` with mocked use cases
- Integration tests: temp directories via `@TempDir`
- Tag long-running benchmarks with `@Tag("benchmark")`

Run a single module's tests:

```bash
mvn -pl projectmind-adapters test
mvn -pl projectmind-bootstrap test -Dtest=ProjectMindControllerTest
```

## Making changes

### Adding a new adapter

1. Define or extend a port in `projectmind-core`
2. Implement the adapter in `projectmind-adapters` as a `@Component`
3. Wire use cases in `UseCaseConfiguration` (bootstrap) if needed
4. Add unit/integration tests in the adapter module

### Adding a new use case

1. Implement in `projectmind-application/src/main/java/.../service/`
2. Inject ports via constructor (plain Java, no Spring in the class)
3. Register as `@Bean` in `UseCaseConfiguration`
4. Expose via REST controller and/or CLI command

### Adding a plugin

See [docs/PLUGIN_GUIDE.md](docs/PLUGIN_GUIDE.md).

## Pull request process

1. Fork and create a feature branch from `main`
2. Make changes with tests
3. Ensure `mvn clean install` passes
4. Open a PR with:
   - Summary of what and why
   - Test plan (commands run)
   - Link to related issue if applicable
5. Address review feedback

### Commit messages

Use clear, imperative sentences:

```
Add embedding cache for unchanged file chunks

Reuse cached vectors during incremental update to avoid redundant
Ollama calls when content hash matches a prior embed.
```

## Architecture decisions

Significant design choices should be documented as ADRs in `docs/adr/`. Use the template:

```
docs/adr/NNN-short-title.md
```

See existing ADRs for format.

## Reporting issues

Include:

- Java and OS version
- Steps to reproduce
- Expected vs actual behavior
- Relevant logs from `com.projectmind` logger

## Code of conduct

Be respectful and constructive in all project interactions.
