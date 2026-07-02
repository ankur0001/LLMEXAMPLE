# ProjectMind REST API

Base URL: `http://localhost:8080/api/v1`

Interactive documentation: [Swagger UI](http://localhost:8080/swagger-ui.html)  
OpenAPI spec: [http://localhost:8080/api/v1/openapi](http://localhost:8080/api/v1/openapi)

## Authentication

No authentication by default. The API binds to `localhost:8080` and is intended for local development. Do not expose without a reverse proxy and auth layer.

## Common headers

| Header | Value |
|--------|-------|
| `Content-Type` | `application/json` (POST bodies) |
| `Accept` | `application/json` or `text/plain` (Mermaid endpoint) |

## Error responses

Validation and application errors return a structured body:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "details": ["path: must not be blank"],
  "timestamp": "2026-07-02T12:00:00Z"
}
```

| HTTP status | Meaning |
|-------------|---------|
| 400 | Invalid request body or query parameter |
| 404 | Repository not found / no memory |
| 409 | Conflict (e.g. scan already in progress) |
| 500 | Unexpected server error |

---

## Endpoints

### GET `/plugins`

List discovered and enabled plugins.

**Response 200:**

```json
{
  "plugins": [
    { "name": "spring-boot", "enabled": true }
  ]
}
```

---

### GET `/status?path={repoPath}`

Repository scan status.

**Query parameters:**

| Name | Required | Description |
|------|----------|-------------|
| `path` | Yes | Absolute or relative repository root |

**Response 200:** `ProjectMetadata` JSON  
**Response 404:** No memory for this path

---

### POST `/scan`

Full repository scan.

**Request body:**

```json
{ "path": "/Users/dev/my-app" }
```

**Response 200:** `ProjectMetadata` with `status: "INDEXED"`

---

### POST `/resume`

Resume an interrupted scan from saved progress.

**Request body:** Same as `/scan`

---

### POST `/update`

Incremental update using content-hash diff.

**Request body:** Same as `/scan`

**Response 200:** `FileChangeSet`

```json
{
  "added": [],
  "modified": [{ "relativePath": "src/Main.java", "...": "..." }],
  "deleted": []
}
```

---

### POST `/docs`

Generate HTML documentation site.

**Request body:** Same as `/scan`

**Response 200:**

```json
{ "indexPath": "/Users/dev/my-app/.ai-memory/documentation/index.html" }
```

---

### POST `/export`

Export `.ai-memory` to a directory.

**Request body:**

```json
{
  "path": "/Users/dev/my-app",
  "outputDir": "/tmp/export"
}
```

`outputDir` is optional (defaults to current directory).

**Response 200:**

```json
{ "exportPath": "/tmp/export/.ai-memory" }
```

---

### POST `/ask`

Ask a question about the repository.

**Request body:**

```json
{
  "path": "/Users/dev/my-app",
  "question": "How does authentication work?"
}
```

**Response 200:**

```json
{
  "question": "How does authentication work?",
  "answer": "...",
  "sourceFiles": ["src/main/java/com/example/SecurityConfig.java"],
  "model": "qwen2.5-coder:14b-instruct"
}
```

---

### GET `/memory?path={repoPath}`

High-level memory overview (graph counts, summary counts, recent history).

**Response 404:** Repository not scanned

---

### GET `/history?path={repoPath}&limit={n}`

Recent change history snapshots.

| Parameter | Default | Range |
|-----------|---------|-------|
| `limit` | 10 | 1–100 |

**Response 200:** Array of `HistorySnapshot`

---

### GET `/graph?path={repoPath}`

Query the knowledge graph.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `path` | Yes | Repository path |
| `nodeId` | No | Return neighborhood subgraph |
| `depth` | No | Traversal depth (1–10, default 1) |
| `nodeType` | No | Filter: `CONTROLLER`, `SERVICE`, etc. |
| `packagePrefix` | No | Filter by package prefix |

**Response 200:** `KnowledgeGraph` with `nodes` and `edges`

---

### GET `/graph/mermaid?path={repoPath}`

Mermaid diagram for the graph (same query params as `/graph`).

**Response:** `text/plain` Mermaid source

---

### DELETE `/cache?path={repoPath}`

Clear cached embeddings and summaries for a repository.

**Response 204:** No content

---

### DELETE `/memory?path={repoPath}`

Delete all persisted memory (equivalent to CLI `clean`).

**Response 204:** No content

---

## Actuator endpoints

| Path | Description |
|------|-------------|
| `/actuator/health` | Health check |
| `/actuator/info` | Application info |
| `/actuator/metrics` | Micrometer metrics |

---

## CORS

Browser clients are allowed from origins configured in `projectmind.api.cors-allowed-origins` (default: `localhost:3000`, `localhost:8080`).

---

## Example workflow

```bash
# Start server
java -jar projectmind-bootstrap/target/projectmind-bootstrap-0.1.0-SNAPSHOT.jar

# Scan
curl -X POST http://localhost:8080/api/v1/scan \
  -H "Content-Type: application/json" \
  -d '{"path":"'"$PWD"'"}'

# Ask
curl -X POST http://localhost:8080/api/v1/ask \
  -H "Content-Type: application/json" \
  -d '{"path":"'"$PWD"'","question":"What are the main entry points?"}'

# View graph as Mermaid
curl "http://localhost:8080/api/v1/graph/mermaid?path=$PWD"
```
