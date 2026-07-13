---
name: puber-android-workflow
description: Puber Android TV feature/refactor workflow. Use for feature planning, Compose TV implementation, Koin/Voyager architecture, Gradle diagnostics, MCP source ingestion, smoke tests, and release tasks.
---

Use this skill for Puber Android work. Keep startup context compact: read only the reference files required by the active
command, phase, or plan step.

## State

- Feature artifacts: `.todo/<feature>/meta.json`, `design.md`, `layouts.md`, `spec.md`, `plan.md`.
- Kent task/node state is the lifecycle authority. `meta.json` stores identity and source/artifact metadata only;
  `plan.md` checkboxes track implementation-step progress.
- Feature target is explicit: command arguments, Kent workflow task context, or a `.todo/<feature>` path/name.
- There is no implicit global feature pointer.
- MCP raw artifacts: `.todo/<feature-or-task>/mcp/`
- Generic MCP raw fallback: `.todo/_mcp-raw/`
- MCP call log: `.todo/_mcp-log/mcporter-calls.jsonl`

## Public Commands

Kent commands live under `.kent/commands/` and are invoked as `/prompt:<name>`.

Legacy `.claude/` files may remain as historical reference, but active Kent sessions should use `.kent/commands/` and
this skill.

## Project Basics

- Single Android project with modules `:app` and `:baselineprofile`; feature/runtime code belongs in `:app`.
- Package root: `com.kino.puber`.
- Product flavors: `dev` and `prod`.
- Main compile check: `./gradlew :app:compileDevDebugKotlin` in the main checkout.
- In project-local and Kent-managed task worktrees, use `./tools/agentw :app:compileDevDebugKotlin`.
- Versions live in `gradle/libs.versions.toml`; do not hardcode dependency versions.
- Static analysis: Detekt config under `config/detekt/`.

## Architecture Pointers

- DI: Koin DSL, global modules in `PuberApp.kt`, screen-scoped modules in `buildModule(scopeId, parentScope)`.
- Navigation: Voyager screens implementing `PuberScreen`; app navigation through `AppRouter`.
- ViewModels: extend `PuberVM<ViewState>` or `PagingVM<T, VS>`.
- UI: Jetpack Compose for Android TV, TV Material3 components, pure content composables with `state` and
  `onAction: (UIAction) -> Unit`.
- API: Ktor/OkHttp in `KinoPubApiClient`; API models are used directly in domain/UI mapping.
- Strings: user-visible strings go in `res/values/strings.xml`.

Use `AGENTS.md` as the broader project source of truth.

## Recipe Loading

Load recipes lazily from:

```text
.kent/skills/puber-android-workflow/references/recipes/
```

Load rules lazily from:

```text
.kent/skills/puber-android-workflow/references/rules/
```

Use:

- `rules/workflow.md` for Kent-specific workflow, worktree, and Gradle behavior.
- `rules/feature-target-resolution.md` whenever a feature command needs to find or create a `.todo/<feature>`
  workspace.
- `rules/mcp.md` for MCP bridge usage.
- `rules/serena.md` for semantic Kotlin navigation when available.
- `rules/web-access-policy.md` for web search/fetch boundaries.
- `rules/natural-language-routing.md` for intent detection from normal user messages.

## MCP Bridge

Kent may not expose MCP as first-class native tools. Use project-local wrappers:

```bash
.kent/adapters/mcp/mcp-list.sh <server> --schema
.kent/adapters/mcp/mcp-call.sh <server.tool> [arguments]
```

Workflow commands should pass `--raw-dir <dir>` when collecting external source data. Mutating calls require
`--allow-mutate` and explicit user approval.

## Subagents

Use configured Kent roles:

```bash
kent run --agent project-researcher --workspace "$PWD" "<prompt>"
kent run --agent build-doctor --workspace "$PWD" "<prompt>"
kent run --agent compose-reviewer --workspace "$PWD" "<prompt>"
kent run --agent domain-model-reviewer --workspace "$PWD" "<prompt>"
```

Use subagents for broad search, noisy diagnostics, implementation slices, and read-only review.

## Safety

- Do not create or update global feature pointer files such as `.todo/.current`.
- Do not modify `.claude/` during Kent migration follow-up work unless explicitly requested.
- Do not commit or push unless explicitly requested.
- Keep local paths, tokens, MCP configs, raw MCP outputs, and call logs out of git.
- Mark plan steps complete only after verification succeeds.
