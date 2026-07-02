# Plugin Development Guide

ProjectMind extends framework-specific behavior through Java SPI plugins. Plugins enrich the knowledge graph and can contribute documentation sections without modifying core code.

## Architecture

```
projectmind-core          ProjectMindPlugin interface + GraphEnhancementContext
projectmind-application   PluginEnhancementService (orchestration)
projectmind-adapters      SpiPluginRegistry (ServiceLoader discovery)
projectmind-plugin-*      Your plugin JAR + META-INF/services registration
projectmind-bootstrap     Aggregates plugins on the classpath
```

Plugins are discovered at startup via `ServiceLoader`. The bootstrap module includes `projectmind-plugin-spring-boot` by default.

## The `ProjectMindPlugin` interface

```java
public interface ProjectMindPlugin {
    String getName();
    default Set<FileType> supportedFileTypes() { ... }
    default boolean appliesTo(ParsedFile file) { ... }
    void enhance(GraphEnhancementContext context, ParsedFile file);
    default void finalizeGraph(GraphEnhancementContext context) { }
    default void onLoad(PluginContext context) { }
    default void onShutdown() { }
    default List<DocSectionType> additionalSections() { ... }
    default List<DocSection> generateDocSections(...) { ... }
}
```

### Lifecycle

1. **Discovery** — `SpiPluginRegistry` loads all `ProjectMindPlugin` implementations from the classpath
2. **Initialization** — enabled plugins receive `onLoad(PluginContext)`
3. **Enhancement** — after the base graph is built, each parsed file is passed to matching plugins via `enhance()`
4. **Finalization** — `finalizeGraph()` runs once per plugin for cross-file logic (e.g. layer-flow edges)
5. **Shutdown** — `onShutdown()` on application exit

### `GraphEnhancementContext`

Mutable view of the knowledge graph:

```java
context.updateNodeType(typeId, GraphNodeType.SERVICE);
context.addOrUpdateNode(new GraphNode(...));
context.addEdge(new GraphEdge(sourceId, targetId, GraphEdgeType.INJECTS, label));
```

Use `GraphNodeIds` from `com.projectmind.core.graph` for stable node identifiers.

## Creating a plugin module

### 1. Maven module

```xml
<artifactId>projectmind-plugin-myframework</artifactId>
<dependencies>
    <dependency>
        <groupId>com.projectmind</groupId>
        <artifactId>projectmind-core</artifactId>
    </dependency>
</dependencies>
```

### 2. Implementation

```java
package com.projectmind.plugin.myframework;

public class MyFrameworkPlugin implements ProjectMindPlugin {

    @Override
    public String getName() {
        return "my-framework";
    }

    @Override
    public void enhance(GraphEnhancementContext context, ParsedFile file) {
        for (ParsedType type : file.types()) {
            if (isFrameworkComponent(type)) {
                String typeId = GraphNodeIds.typeId(file.packageName(), type.name());
                context.updateNodeType(typeId, GraphNodeType.SERVICE);
            }
        }
    }
}
```

### 3. SPI registration

Create `src/main/resources/META-INF/services/com.projectmind.core.port.ProjectMindPlugin`:

```
com.projectmind.plugin.myframework.MyFrameworkPlugin
```

### 4. Add to bootstrap classpath

In `projectmind-bootstrap/pom.xml`:

```xml
<dependency>
    <groupId>com.projectmind</groupId>
    <artifactId>projectmind-plugin-myframework</artifactId>
</dependency>
```

Rebuild and restart. Verify with:

```bash
curl http://localhost:8080/api/v1/plugins
```

## Reference: Spring Boot plugin

See `projectmind-plugin-spring-boot/` for a complete example:

- Detects `@Controller`, `@Service`, `@Repository`, `@Entity`, `@FeignClient`
- Adds entity→database, security, and layer-flow edges
- Contributes a Spring layer summary doc section

## Configuration

```yaml
projectmind:
  plugins:
    enabled: true
    include: []          # empty = all discovered; or ["spring-boot", "my-framework"]
    exclude: []          # disable specific plugins
```

Disable all plugins: `projectmind.plugins.enabled: false`

## Documentation sections

Override `generateDocSections()` to add plugin-specific markdown:

```java
@Override
public List<DocSection> generateDocSections(
        Path repositoryPath,
        ProjectMetadata metadata,
        RepositoryIndex index,
        KnowledgeGraph graph) {
    return List.of(new DocSection(
            DocSectionType.ARCHITECTURE,
            "My Framework Overview",
            "## Components\n\n...",
            null));
}
```

Sections are merged into the generated HTML site during `docs` / `update`.

## Testing

Unit-test plugins against core domain types only (no Spring):

```java
GraphEnhancementContext context = new GraphEnhancementContext(emptyGraph);
plugin.enhance(context, parsedFile);
plugin.finalizeGraph(context);
KnowledgeGraph result = context.toGraph();
// assert on nodes and edges
```

See `SpringBootPluginTest` in `projectmind-plugin-spring-boot` for a full example.

## Design rules

1. **Depend only on `projectmind-core`** — plugins must not import adapters or Spring
2. **No side effects in `enhance()`** — mutate the context only; persistence is handled by use cases
3. **Fail soft** — log and skip on errors; one bad file must not break the scan
4. **Unique names** — duplicate plugin names are ignored at discovery
5. **Idempotent edges** — `addEdge()` deduplicates by source, target, and type

## Troubleshooting

| Issue | Check |
|-------|-------|
| Plugin not listed | `META-INF/services` file present? JAR on bootstrap classpath? |
| Plugin disabled | `include`/`exclude` config, `enabled: false` |
| No graph changes | Does `appliesTo()` return true for your file types? |
| Startup error | Plugin `onLoad()` throwing? Check logs for `Failed to initialize plugin` |
