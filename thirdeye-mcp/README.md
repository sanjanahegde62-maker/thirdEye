# ThirdEye AI — IBM Bob MCP Server

This is the **authentic IBM Bob integration** for ThirdEye AI.

It exposes the ThirdEye Spring Boot API as Model Context Protocol (MCP) tools so that **IBM Bob can drive the entire code-review workflow from its chat panel** — without leaving the IDE.

---

## What IBM Bob can do with this server

Once connected, you can ask Bob things like:

| Bob prompt | Tool invoked |
|---|---|
| "List my ThirdEye projects" | `list_projects` |
| "Show me reviews for project 1" | `list_reviews` |
| "Submit this diff for review" | `create_review` |
| "What's the status of review 3?" | `get_review` |
| "Show critical findings for review 3" | `get_findings` (severity=critical) |
| "Summarize the findings for review 3" | `get_findings_summary` |
| "Re-run analysis on review 3" | `trigger_analysis` |
| "Generate tests for review 3" | `generate_tests` |

---

## Prerequisites

- Node.js 18 or later
- The ThirdEye Spring Boot backend running on `http://localhost:8080`

---

## Build

```bash
cd thirdeye-mcp
npm install
npm run build
```

The compiled entry point is at `build/index.js`.

---

## Register in IBM Bob

Add the following to your Bob MCP configuration file (`mcp.json`):

```json
{
  "mcpServers": {
    "thirdeye": {
      "command": "node",
      "args": ["C:/Users/sanja/Downloads/thirdeye-backend/thirdeye-backend/thirdeye-mcp/build/index.js"],
      "env": {
        "THIRDEYE_API_BASE_URL": "http://localhost:8080"
      }
    }
  }
}
```

Replace the path in `args` with the absolute path to `build/index.js` on your machine.

After saving, Bob hot-reloads the server. You should see **thirdeye** listed in Bob's MCP panel.

---

## Tools

| Tool | Method | Endpoint |
|---|---|---|
| `list_projects` | GET | `/api/projects` |
| `list_reviews` | GET | `/api/projects/{id}/reviews` |
| `get_review` | GET | `/api/reviews/{id}` |
| `create_review` | POST | `/api/projects/{id}/reviews` |
| `trigger_analysis` | POST | `/api/reviews/{id}/analyze` |
| `get_findings` | GET | `/api/reviews/{id}/findings` |
| `get_findings_summary` | GET | `/api/reviews/{id}/findings/summary` |
| `generate_tests` | POST | `/api/reviews/{id}/tests/generate` |
| `get_tests` | GET | `/api/reviews/{id}/tests` |

---

## ⚠️ Analysis and tests are currently MOCK

The Spring Boot backend uses a deterministic pattern-matching engine (`MockAnalysisService`) and a template test generator (`TestGenerationService`). Neither calls a live AI service.

The MCP server correctly exposes these capabilities — when a real IBM Granite / watsonx engine replaces the mock, the MCP tools continue to work with no changes.
