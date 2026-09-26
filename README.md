# ThirdEye AI — Backend

Spring Boot REST API for the ThirdEye AI code review and test generation platform.

---

## ⚠️ Important: Analysis is currently MOCK

The analysis engine (`MockAnalysisService`) is a **deterministic pattern-matcher**, not real AI.
It scans the submitted code diff for common patterns (hardcoded credentials, SQL injection, TODO comments, etc.)
and returns realistic-looking findings to allow the frontend to work end-to-end.

**Real IBM Granite / watsonx analysis will replace this in the next task.**
All mock findings include `[MOCK]` in their description so they are clearly identifiable.

---

## Tech Stack

| Layer       | Technology                           |
|-------------|--------------------------------------|
| Framework   | Spring Boot 4.1.1, Java 17           |
| Database    | H2 (in-memory, dev) / MySQL (prod)   |
| ORM         | Spring Data JPA / Hibernate          |
| Validation  | Jakarta Bean Validation              |
| Build       | Maven                                |

---

## Running the backend

```bash
cd thirdeye-backend
./mvnw spring-boot:run
```

The server starts on **http://localhost:8080**.
The H2 console is available at **http://localhost:8080/h2-console**
(JDBC URL: `jdbc:h2:mem:thirdeye`, user: `sa`, password: blank).

### Environment / Configuration

| Property | Default | Description |
|---|---|---|
| `thirdeye.cors.allowed-origins` | `http://localhost:5174` | Frontend origin for CORS |
| `server.port` | `8080` | Server port |

Override any property via environment variable (e.g. `THIRDEYE_CORS_ALLOWED_ORIGINS=https://app.example.com`).

---

## Running the tests

```bash
cd thirdeye-backend
./mvnw test
```

Tests use a separate in-memory H2 database (`thirdeye_test`) and are fully isolated.

---

## API Reference

All responses use `Content-Type: application/json`. Errors use the shape:

```json
{
  "timestamp": "2025-01-01T12:00:00",
  "status": 404,
  "error": "Not Found",
  "message": "Review not found with id: 42"
}
```

### Projects

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/projects` | List all projects |
| GET | `/api/projects/{id}` | Get project by ID |
| POST | `/api/projects` | Create a project |

**POST /api/projects** — request body:
```json
{
  "name": "My Project",
  "description": "Optional description",
  "repositoryUrl": "https://github.com/org/repo",
  "status": "ACTIVE"
}
```

---

### Reviews

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/projects/{projectId}/reviews` | List reviews for a project |
| POST | `/api/projects/{projectId}/reviews` | Create a review and start analysis |
| GET | `/api/reviews/{reviewId}` | Get review by ID |
| GET | `/api/reviews/{reviewId}/diff` | Get the raw code diff (text/plain) |
| POST | `/api/reviews/{reviewId}/analyze` | Re-run (or trigger) analysis |

**POST /api/projects/{projectId}/reviews** — request body:
```json
{
  "title": "Feature: add user auth",
  "codeDiff": "+++ b/src/main/java/App.java\n+String password = \"secret\";",
  "filesChanged": 3,
  "linesAdded": 42,
  "linesRemoved": 10,
  "commits": 2
}
```

Response returns the created `Review` with `status: "PENDING"`.
Analysis runs asynchronously; poll `GET /api/reviews/{id}` to watch `status` progress through
`PENDING → ANALYZING → COMPLETED` (or `FAILED`).

**Review status values:**

| Status | Meaning |
|--------|---------|
| `PENDING` | Created, waiting for analysis to start |
| `ANALYZING` | Analysis in progress |
| `COMPLETED` | Analysis finished; findings are available |
| `FAILED` | Analysis encountered an unrecoverable error |

---

### Findings

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/reviews/{reviewId}/findings` | List all findings for a review |
| POST | `/api/reviews/{reviewId}/findings` | Create a finding manually |
| GET | `/api/reviews/{reviewId}/findings/summary` | Severity summary counts |

---

### Generated Tests

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/reviews/{reviewId}/tests` | List previously generated test stubs |
| POST | `/api/reviews/{reviewId}/tests/generate` | Generate (or re-generate) mock test stubs from findings |

**POST /api/reviews/{reviewId}/tests/generate** requires the review to be `COMPLETED`. Returns HTTP 400 otherwise.

[MOCK] Tests are template stubs — they are never executed.

**GET /api/reviews/{reviewId}/findings/summary** — example response:
```json
{
  "reviewId": 1,
  "total": 5,
  "critical": 1,
  "high": 2,
  "medium": 1,
  "low": 1
}
```

**Finding severity values:** `critical`, `high`, `medium`, `low`

**Finding category values** (from mock engine): `security`, `bugs`, `quality`, `coverage`

---

## Mock Analysis Patterns

The mock engine detects the following patterns on **added lines** (`+`) in the diff:

| Pattern | Severity | Category |
|---------|----------|----------|
| Hardcoded credentials (password/secret/token + `=` + quoted value) | critical | security |
| SQL string concatenation with SELECT/WHERE | high | security |
| TODO / FIXME / HACK comments | low | quality |
| Empty catch blocks | medium | bugs |
| `Optional.get()` without presence check | high | bugs |
| No test changes in diff | medium | coverage |
| Diff adds > 200 lines | low | quality |
| Empty diff submitted | low | quality |

---

## Next Task — IBM Bob / watsonx Integration

Replace `MockAnalysisService.analyzeAsync()` with a real IBM Granite / watsonx call:

1. Add the IBM watsonx SDK or HTTP client dependency to `pom.xml`
2. Create `WatsonxAnalysisService` implementing the same interface as `MockAnalysisService`
3. Build a prompt that includes the `codeDiff` and requests structured JSON findings
4. Parse the LLM response into `Finding` entities
5. Configure `watsonx.api.url`, `watsonx.api.key`, and `watsonx.project.id` in `application.properties`
6. Swap the `@Async` call in `ReviewService.createReview()` from mock to real service (or make it a strategy)
7. Remove `[MOCK]` prefixes from finding descriptions

The rest of the backend (entities, repositories, controllers, error handling, tests) requires **no changes** for this integration.
