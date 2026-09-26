#!/usr/bin/env node
/**
 * ThirdEye AI — IBM Bob MCP Server
 *
 * Exposes the ThirdEye Spring Boot API as tools so that IBM Bob can:
 *   • list projects and their reviews
 *   • create new code reviews
 *   • read findings (with severity filtering)
 *   • get a severity summary
 *   • trigger or re-run analysis
 *   • generate test stubs from findings
 *
 * This is the genuine IBM Bob integration for ThirdEye AI.
 * Bob can invoke these tools from its chat panel to drive the full
 * code-review workflow without leaving the IDE.
 *
 * Configuration (environment variables):
 *   THIRDEYE_API_BASE_URL   URL of the running Spring Boot backend
 *                           (default: http://localhost:8080)
 *
 * Register in Bob's mcp.json:
 * {
 *   "mcpServers": {
 *     "thirdeye": {
 *       "command": "node",
 *       "args": ["/absolute/path/to/thirdeye-mcp/build/index.js"],
 *       "env": {
 *         "THIRDEYE_API_BASE_URL": "http://localhost:8080"
 *       }
 *     }
 *   }
 * }
 */

import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import { z } from "zod";

// ── Configuration ──────────────────────────────────────────────────────────────

const BASE_URL = (process.env.THIRDEYE_API_BASE_URL ?? "http://localhost:8080").replace(/\/+$/, "");

// ── HTTP helper ────────────────────────────────────────────────────────────────

async function api(method: string, path: string, body?: unknown): Promise<unknown> {
  const url = `${BASE_URL}${path}`;
  const opts: RequestInit = {
    method,
    headers: { "Content-Type": "application/json", Accept: "application/json" },
  };
  if (body !== undefined) opts.body = JSON.stringify(body);

  const res = await fetch(url, opts);
  const text = await res.text();

  let data: unknown;
  try { data = JSON.parse(text); } catch { data = text; }

  if (!res.ok) {
    const msg = (data && typeof data === "object" && data !== null && "message" in data)
      ? (data as Record<string, unknown>).message
      : text;
    throw new Error(`ThirdEye API ${method} ${path} → ${res.status}: ${msg}`);
  }
  return data;
}

function ok(text: string) {
  return { content: [{ type: "text" as const, text }] };
}
function err(e: unknown) {
  return { content: [{ type: "text" as const, text: String(e) }], isError: true as const };
}

// ── MCP Server ─────────────────────────────────────────────────────────────────

const server = new McpServer({ name: "thirdeye", version: "0.1.0" });

// ── Tool: list_projects ────────────────────────────────────────────────────────
server.registerTool(
  "list_projects",
  {
    description:
      "List all ThirdEye AI projects. Returns id, name, description, repositoryUrl, and status for each project.",
    inputSchema: z.object({}),
  },
  async () => {
    try {
      return ok(JSON.stringify(await api("GET", "/api/projects"), null, 2));
    } catch (e) { return err(e); }
  }
);

// ── Tool: list_reviews ─────────────────────────────────────────────────────────
server.registerTool(
  "list_reviews",
  {
    description:
      "List all reviews for a ThirdEye project. Returns id, title, status (PENDING/ANALYZING/COMPLETED/FAILED), progress (0-100), and change stats.",
    inputSchema: z.object({
      projectId: z.number().int().positive().describe("Numeric project ID"),
    }),
  },
  async ({ projectId }) => {
    try {
      return ok(JSON.stringify(await api("GET", `/api/projects/${projectId}/reviews`), null, 2));
    } catch (e) { return err(e); }
  }
);

// ── Tool: get_review ───────────────────────────────────────────────────────────
server.registerTool(
  "get_review",
  {
    description:
      "Get a single ThirdEye review by ID. Includes status, progress, and change statistics. Poll this tool to watch analysis progress.",
    inputSchema: z.object({
      reviewId: z.number().int().positive().describe("Numeric review ID"),
    }),
  },
  async ({ reviewId }) => {
    try {
      return ok(JSON.stringify(await api("GET", `/api/reviews/${reviewId}`), null, 2));
    } catch (e) { return err(e); }
  }
);

// ── Tool: create_review ────────────────────────────────────────────────────────
server.registerTool(
  "create_review",
  {
    description:
      "Submit a code diff to ThirdEye AI for review. The review is persisted immediately and asynchronous mock analysis starts automatically. Returns the created review with status PENDING.",
    inputSchema: z.object({
      projectId:    z.number().int().positive().describe("Project to attach the review to"),
      title:        z.string().min(1).describe("Short descriptive title, e.g. 'fix: remove hardcoded token'"),
      codeDiff:     z.string().describe("Unified diff string (output of git diff)"),
      filesChanged: z.number().int().min(0).optional().default(1),
      linesAdded:   z.number().int().min(0).optional().default(0),
      linesRemoved: z.number().int().min(0).optional().default(0),
      commits:      z.number().int().min(0).optional().default(1),
    }),
  },
  async ({ projectId, title, codeDiff, filesChanged, linesAdded, linesRemoved, commits }) => {
    try {
      return ok(JSON.stringify(
        await api("POST", `/api/projects/${projectId}/reviews`, {
          title, codeDiff, filesChanged, linesAdded, linesRemoved, commits,
        }),
        null, 2
      ));
    } catch (e) { return err(e); }
  }
);

// ── Tool: trigger_analysis ─────────────────────────────────────────────────────
server.registerTool(
  "trigger_analysis",
  {
    description:
      "Re-run (or trigger) analysis for an existing ThirdEye review. Idempotent — existing findings are deleted before re-analysis. Returns the review with status reset to PENDING.",
    inputSchema: z.object({
      reviewId: z.number().int().positive().describe("Review to (re)analyse"),
    }),
  },
  async ({ reviewId }) => {
    try {
      return ok(JSON.stringify(await api("POST", `/api/reviews/${reviewId}/analyze`), null, 2));
    } catch (e) { return err(e); }
  }
);

// ── Tool: get_findings ─────────────────────────────────────────────────────────
server.registerTool(
  "get_findings",
  {
    description:
      "Get all findings for a completed ThirdEye review. Each finding has title, severity (critical/high/medium/low), category, file, line, description, and suggestion.",
    inputSchema: z.object({
      reviewId:       z.number().int().positive().describe("Review ID"),
      severityFilter: z.enum(["all", "critical", "high", "medium", "low"]).optional().default("all"),
    }),
  },
  async ({ reviewId, severityFilter }) => {
    try {
      const findings = (await api("GET", `/api/reviews/${reviewId}/findings`)) as Array<Record<string, unknown>>;
      const filtered = severityFilter === "all"
        ? findings
        : findings.filter(f => String(f.severity).toLowerCase() === severityFilter);
      return ok(JSON.stringify(filtered, null, 2));
    } catch (e) { return err(e); }
  }
);

// ── Tool: get_findings_summary ─────────────────────────────────────────────────
server.registerTool(
  "get_findings_summary",
  {
    description:
      "Get a severity summary for a ThirdEye review. Returns total count and counts broken down by critical, high, medium, and low severity.",
    inputSchema: z.object({
      reviewId: z.number().int().positive().describe("Review ID"),
    }),
  },
  async ({ reviewId }) => {
    try {
      return ok(JSON.stringify(await api("GET", `/api/reviews/${reviewId}/findings/summary`), null, 2));
    } catch (e) { return err(e); }
  }
);

// ── Tool: generate_tests ───────────────────────────────────────────────────────
server.registerTool(
  "generate_tests",
  {
    description:
      "Generate mock test stubs from the findings of a COMPLETED ThirdEye review. Returns a list of test skeletons with name, file, status (PENDING), and generated test code. NOTE: tests are MOCK stubs — they are not executed.",
    inputSchema: z.object({
      reviewId: z.number().int().positive().describe("Review ID (must be COMPLETED)"),
    }),
  },
  async ({ reviewId }) => {
    try {
      return ok(JSON.stringify(await api("POST", `/api/reviews/${reviewId}/tests/generate`), null, 2));
    } catch (e) { return err(e); }
  }
);

// ── Tool: get_review_diff ──────────────────────────────────────────────────────
server.registerTool(
  "get_review_diff",
  {
    description:
      "Get the raw unified diff (git diff output) submitted with a ThirdEye review. Returns the diff as plain text. Returns an empty string if no diff was stored.",
    inputSchema: z.object({
      reviewId: z.number().int().positive().describe("Review ID"),
    }),
  },
  async ({ reviewId }) => {
    try {
      const url = `${BASE_URL}/api/reviews/${reviewId}/diff`;
      const res = await fetch(url, {
        headers: { Accept: "text/plain, */*" },
      });
      const text = await res.text();
      if (!res.ok) throw new Error(`ThirdEye API GET /api/reviews/${reviewId}/diff → ${res.status}: ${text}`);
      return ok(text);
    } catch (e) { return err(e); }
  }
);

// ── Tool: get_tests ────────────────────────────────────────────────────────────
server.registerTool(
  "get_tests",
  {
    description: "Get previously generated test stubs for a ThirdEye review.",
    inputSchema: z.object({
      reviewId: z.number().int().positive().describe("Review ID"),
    }),
  },
  async ({ reviewId }) => {
    try {
      return ok(JSON.stringify(await api("GET", `/api/reviews/${reviewId}/tests`), null, 2));
    } catch (e) { return err(e); }
  }
);

// ── Main ───────────────────────────────────────────────────────────────────────

async function main(): Promise<void> {
  const transport = new StdioServerTransport();
  await server.connect(transport);
  console.error(`ThirdEye MCP server running — connected to ${BASE_URL}`);
}

main().catch((e) => {
  console.error("Fatal error in ThirdEye MCP server:", e);
  process.exit(1);
});
