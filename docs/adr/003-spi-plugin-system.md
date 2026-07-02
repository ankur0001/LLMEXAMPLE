# ADR 003: SPI Plugin System for Framework Extensions

**Status:** Accepted  
**Date:** 2026-07-02

## Context

Framework-specific logic (Spring `@Controller`/`@Service` detection, Feign clients, React component graphs) does not belong in the generic dependency analyzer. Hardcoding every framework into adapters would bloat the core and violate the open/closed principle.

## Decision

1. Define `ProjectMindPlugin` in **core** with hooks:
   - `enhance(GraphEnhancementContext, ParsedFile)` — per-file graph enrichment
   - `finalizeGraph()` — cross-file post-processing
   - `generateDocSections()` — optional documentation contributions

2. Discover plugins via **Java SPI** (`ServiceLoader`) in `SpiPluginRegistry` (adapter).

3. Orchestrate plugins in **application** via `PluginEnhancementService`, called after base graph build in `RepositoryGraphBuilder`.

4. Ship **`projectmind-plugin-spring-boot`** as the first reference plugin; extract Spring-specific code from `GraphDependencyAnalyzer`.

5. Configure enable/disable via `projectmind.plugins.*` in `application.yml`.

## Consequences

**Positive:**
- New frameworks added without modifying core/adapters
- Plugins depend only on `projectmind-core`
- Third-party plugins possible as separate JARs

**Negative:**
- Plugin API must remain stable; breaking changes affect external plugins
- `GraphEnhancementContext` mutability requires careful use
- Duplicate plugin names are silently ignored

## Alternatives considered

- **Spring `@ConditionalOn*` beans replacing ports** — rejected; couples plugins to Spring, prevents standalone JARs
- **Script-based plugins (GraalJS)** — rejected for v0.1; type safety and debugging concerns
