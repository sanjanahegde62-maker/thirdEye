# ThirdEye AI — Static Analysis Engine (Milestone 1)

## Overview

Milestone 1 replaces the previous mock/placeholder analysis pipeline with a
genuine deterministic static-analysis engine.  The engine examines the
**added lines** of a submitted unified diff using a set of pattern-matching
rules and produces findings that are persisted through the existing
`Finding` entity and REST API.

**What this is NOT**:
- Not an AI or LLM model. No external API calls are made.
- Not a full compiler, parser, or data-flow analyser.
- Does not perform type inference, cross-file analysis, or interprocedural
  taint tracking.

A finding means a pattern matched — it indicates a **potential** issue, not
a confirmed exploitable vulnerability.  Each finding includes the rule ID so
results are traceable.

---

## Architecture

```
ReviewController
    └─> ReviewService.createReview / triggerAnalysis
            └─> MockAnalysisService.analyzeAsync  (@Async)
                    └─> StaticAnalysisEngine.analyze(review, diff)
                            ├─> DiffParser.parse(diff)  → List<DiffLine>
                            ├─> SqlInjectionRule.analyze(lines)
                            ├─> HardcodedCredentialRule.analyze(lines)
                            ├─> DangerousExecutionRule.analyze(lines)
                            └─> UnsafeDeserializationRule.analyze(lines)
                    └─> FindingRepository.saveAll(findings)
```

All new code lives in `com.thirdeye.backend.analysis` and its `rules`
sub-package.  No existing entities, repositories, controllers, or DTOs
were changed.

---

## Supported Rules

| Rule ID   | Title                                | Severity (max) | Category  |
|-----------|--------------------------------------|----------------|-----------|
| SQLI-001  | SQL Injection via String Concatenation | critical     | security  |
| CRED-001  | Hardcoded Credential Literal           | critical     | security  |
| EXEC-001  | Dangerous OS Command Execution         | high         | security  |
| DESER-001 | Unsafe Deserialization                 | high         | security  |

### SQLI-001 — SQL Injection via String Concatenation

Detects lines where a SQL keyword (`SELECT`, `INSERT`, `UPDATE`, `DELETE`,
`MERGE`) in a string literal is combined with a `+` concatenation operator.
Severity is elevated to `critical` when a `Statement.executeQuery/execute`
call is also present on the same line.

**False-positive risk**: ORM HQL/JPQL strings that happen to use
concatenation may trigger this rule. Verify that the concatenated values
originate from untrusted input before treating as confirmed.

### CRED-001 — Hardcoded Credential Literal

Detects string literals assigned to identifiers named `password`, `passwd`,
`secret`, `api_key`, `apikey`, `token`, `authtoken`, `access_key`, or
`private_key`.

**False-positive reduction**:
- Empty strings are ignored (placeholders).
- Common placeholder words (`changeme`, `example`, `placeholder`, etc.)
  are ignored.
- Spring/config property substitution patterns (`${...}`) are ignored.
- `System.getenv(...)` calls are not flagged.
- Pure comment lines are skipped.
- Test files (`*Test.java`, `*Tests.java`) produce `low` severity instead
  of `critical`.

### EXEC-001 — Dangerous OS Command Execution

Detects `Runtime.getRuntime().exec(...)` and `new ProcessBuilder(...).start()`
calls.  Severity is `high` when string concatenation is present in the same
line (indicating a potentially dynamic/user-controlled command); otherwise
`medium`.

### DESER-001 — Unsafe Deserialization

Detects `new ObjectInputStream(...)`, `.readObject()`, `.readUnshared()`,
XStream's `fromXML(...)`, and `SerializationUtils.deserialize(...)`.
Severity is `high` when a network/request stream reference is on the same
line; otherwise `medium`.

---

## Diff Parsing

The `DiffParser` parses standard unified diff format (output of `git diff`)
and extracts:
- **File path** from `+++ b/...` headers (git prefix `b/` is stripped).
- **Line number** from `@@ -a,b +c,d @@` hunk headers; the target line
  counter is incremented for each added or context line, and held steady
  for removed lines.

If the submitted input is raw code rather than a proper unified diff (no
`+++` header), file is reported as `unknown` and line as `0`.

---

## Limitations

1. **Line-by-line only**: multi-line patterns (e.g. a SQL query built
   across three lines) are not detected unless each individual line
   contains enough of the pattern.
2. **No data-flow**: the engine cannot determine whether a concatenated
   value actually originates from user input.
3. **Java-centric**: rules are written with Java syntax in mind but will
   also match similar patterns in Kotlin, Groovy, and Scala.
4. **No framework awareness**: Spring Data JPA `@Query` with concatenation
   may produce a false positive for SQLI-001.
5. **Regex, not AST**: no abstract syntax tree is built. Patterns may
   produce false positives on unusual formatting.
6. **Historical data**: the engine does not retroactively rewrite findings
   from previous reviews. The recovered historical review (ID 1) retains
   its original findings.

---

## Running the Tests

```bash
# From the project root (thirdeye-backend)
.\mvnw.cmd test
```

Unit tests are in:
- `src/test/java/com/thirdeye/backend/analysis/StaticAnalysisEngineTest.java`
  — pure in-memory tests, no database, no Spring context.
- `src/test/java/com/thirdeye/backend/ThirdeyeBackendApplicationTests.java`
  — Spring Boot integration tests using an isolated H2 in-memory database.

---

## Adding a New Rule

1. Create a class in `com.thirdeye.backend.analysis.rules` that implements
   `AnalysisRule`.
2. Implement `ruleId()`, `name()`, and `analyze(List<DiffLine>)`.
3. Add an instance to the `rules` list in `StaticAnalysisEngine`'s
   constructor.
4. Add unit tests covering at least: one positive detection, one negative
   case, and one false-positive guard.

---

## Testing the Feature Locally

### Sample diff with SQL injection + hardcoded credential

```
--- a/UserService.java
+++ b/UserService.java
@@ -0,0 +1,6 @@
+Connection conn = DriverManager.getConnection("jdbc:mysql://localhost/db", "root", "mysecret");
+Statement stmt = conn.createStatement();
+String query = "SELECT * FROM users WHERE name='" + username + "' AND pass='" + password + "'";
+ResultSet rs = stmt.executeQuery(query);
```

### Steps

1. Start the backend: `.\mvnw.cmd spring-boot:run`
2. Start the frontend: `npm run dev` (from `thirdeye-ai-frontend`)
3. Open `http://localhost:5173` → select a project → **Code diff** page
4. Paste the sample diff above in the diff text area, enter a title, click
   **Submit for review**
5. Navigate to **Overview** — status will change from `PENDING` → `ANALYZING`
   → `COMPLETED` (auto-refreshes every 3 seconds)
6. Navigate to **Findings** — you should see:
   - One `critical` or `high` security finding for the `SQLI-001` SQL
     injection pattern
   - One `critical` security finding for the `CRED-001` hardcoded credential
     (`"mysecret"`)
7. Verify file attribution: both findings should report
   `file = UserService.java`

### Verification via API

```bash
# List reviews for project 2 (Lost & Found)
curl http://localhost:8080/api/projects/2/reviews

# Get findings for the new review (replace {id} with actual review ID)
curl http://localhost:8080/api/reviews/{id}/findings

# Get severity summary
curl http://localhost:8080/api/reviews/{id}/findings/summary
```

### Verify historical data is intact

```bash
curl http://localhost:8080/api/reviews/1/findings
# Should return the original recovered finding: "No test changes detected in diff"
```
