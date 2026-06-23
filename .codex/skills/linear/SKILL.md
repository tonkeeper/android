---
name: linear
description: Retrieve Linear task context via Linear MCP for implementation work. Use when the developer needs requirements, scope, or acceptance criteria from a ticket.
metadata:
  short-description: Linear MCP task context for coding
---

# Linear

## Overview

This skill uses the Linear MCP server to gather issue context for developers implementing tasks. It is context-oriented and read-only by default.

## Prerequisites
- Linear MCP server must be connected and accessible via OAuth
- Confirm access to the relevant Linear workspace and teams

## Required Workflow

**Follow these steps in order. Do not skip steps.**

### Step 0: Set up Linear MCP (if not already configured)

If any MCP call fails because Linear MCP is not connected, pause and set it up:

1. Add the Linear MCP:
   - `codex mcp add linear --url https://mcp.linear.app/mcp`
2. Enable remote MCP client:
   - Set `[features] rmcp_client = true` in `config.toml` or run `codex --enable rmcp_client`
3. Log in with OAuth:
   - `codex mcp login linear`

After successful login, the user must restart Codex. Finish the answer and instruct them to restart and retry.

**Windows/WSL note:** If connection errors appear on Windows, configure Linear MCP via WSL:
```json
{"mcpServers":{"linear":{"command":"wsl","args":["npx","-y","mcp-remote","https://mcp.linear.app/sse","--transport","sse-only"]}}}
```

### Step 1
Clarify the implementation goal and scope for the task. Confirm relevant identifiers (issue ID, team key, project if needed).

### Step 2
Use read-focused Linear MCP tools to collect context:
- `get_issue`, `list_issues`, `list_my_issues`
- `list_issue_statuses`, `list_issue_labels`
- `get_project`, `list_projects`
- `get_team`, `list_teams`, `list_users`
- `list_documents`, `get_document`, `search_documentation`
- `list_comments`, `list_cycles`

### Step 3: Build implementation context
From retrieved data, extract and present:
- Problem statement
- Scope and out-of-scope boundaries
- Acceptance criteria / definition of done
- UX, API, and technical constraints
- Dependencies, risks, and blockers
- Open questions and assumptions that must be clarified before coding

### Step 4
Summarize findings for the developer and map them to an implementation plan and test plan.

## Guardrails

- Do not create or update Linear entities unless the user explicitly asks.
- Do not run project-management workflows (triage, planning, redistribution) by default.
- Prefer read calls first; if write actions are requested later, confirm intent and scope before proceeding.
