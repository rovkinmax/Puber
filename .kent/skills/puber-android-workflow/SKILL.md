---
name: puber-android-workflow
description: Puber Android TV feature/refactor workflow. Use for feature planning, Compose TV implementation, Koin/Voyager architecture, Gradle diagnostics, MCP source ingestion, smoke tests, and release tasks.
---

# Puber Android TV Workflow

Read the active `.kent/context/<node>.md` manifest first. This skill is the
project workflow and recipe index; repository-wide Android TV gotchas stay in
`AGENTS.md` and shared lifecycle stays in the Kent Engineering Kit.

## Artifacts And Commands

- Feature artifacts:
  `.todo/<feature>/{meta.json,design.md,layouts.md,spec.md,plan.md}`.
- The feature target is explicit through command/task context or a
  `.todo/<feature>` path; there is no global current-feature pointer.
- Public prompt procedures live in `.kent/commands/`.
- Known-safe MCP artifacts may use `.todo/<feature-or-task>/mcp/`; call metadata
  uses `.todo/_mcp-log/mcporter-calls.jsonl`.

## Lazy Loading

Recipe root:

```text
.kent/skills/puber-android-workflow/references/recipes/
```

Rule root:

```text
.kent/skills/puber-android-workflow/references/rules/
```

Load only what the active step triggers:

- workflow/worktree/Gradle behavior: `rules/workflow.md`;
- `.todo` feature resolution: `rules/feature-target-resolution.md`;
- MCP bridge: `rules/mcp.md`;
- semantic Kotlin navigation: `rules/serena.md`;
- web sources: `rules/web-access-policy.md`;
- natural-language command routing: `rules/natural-language-routing.md`.

Use `AGENTS.md` for architecture and build gotchas. Load the matching
navigation, DI, ViewModel, Compose TV, filtering, paging, API, or test recipe
only for the current plan slice or finding.

## External Tools

- MCP uses `~/.kent/bin/kent-mcp-list` and
  `~/.kent/bin/kent-mcp-call`.
- Runtime Mobile behavior belongs to `.kent/commands/smoke-test.md`.
- Persist only scoped known-safe output; credentials and broad authenticated
  responses stay outside Git and evidence.

Use configured research/build roles for bounded read-only search or noisy
diagnosis. Implement/Fix remains the single writer selected by the workflow.
