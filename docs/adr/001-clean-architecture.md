# ADR 001: Clean Architecture with Port/Adapter Pattern

**Status:** Accepted  
**Date:** 2026-07-02

## Context

ProjectMind integrates many infrastructure concerns — filesystem scanning, Tree-sitter parsing, Ollama HTTP, ChromaDB, SQLite, HTML generation — while remaining testable and swappable. A monolithic Spring service would couple business logic to frameworks and make unit testing expensive.

## Decision

Adopt **Clean Architecture** with four Maven modules:

1. **core** — domain models and port interfaces (no Spring)
2. **application** — use case orchestration depending only on core
3. **adapters** — port implementations (`@Component` beans)
4. **bootstrap** — Spring Boot wiring, REST, CLI

Dependency rule: inner layers never depend on outer layers. Use cases receive ports via constructor injection; Spring assembles them in `UseCaseConfiguration`.

## Consequences

**Positive:**
- Use cases are plain Java — fast unit tests with mocks
- Adapters can be replaced (e.g. in-memory vector index vs ChromaDB) without touching application code
- Clear boundaries for new contributors

**Negative:**
- More modules and boilerplate (`@Bean` factories in bootstrap)
- Application cannot directly use adapter utilities at compile time (test scope exception for integration tests)

## Alternatives considered

- **Single Spring module** — rejected; hard to enforce boundaries
- **Hexagonal with code generation** — rejected; unnecessary complexity for v0.1
