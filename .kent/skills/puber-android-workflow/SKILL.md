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
- Known-safe persisted MCP artifacts: `.todo/<feature-or-task>/mcp/`
- MCP call log: `.todo/_mcp-log/mcporter-calls.jsonl`

## Public Commands

Kent commands live under `.kent/commands/` and are invoked as `/prompt:<name>`.

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

Kent does not expose MCP as first-class tools. Use the global Kent Engineering
Kit adapter:

```bash
~/.kent/bin/kent-mcp-list <server> --schema
~/.kent/bin/kent-mcp-call <server.tool> [arguments]
```

Important rules:

- Config resolution is worktree-aware. Credentials and project-specific server
  definitions stay outside the global adapter.
- The default `mobile` server is machine-global in
  `~/.mcporter/mcporter.json`, managed by the kit's `configure-mcporter`
  command. Do not duplicate it in project `.mcp.json`.
- `.kent/adapters/mcp/policy` classifies known Puber operations. Unknown tools
  inherit the global fail-closed mutation policy.
- The adapter does not create a separate raw artifact by default, but normal
  stdout remains in Kent's shell transcript.
- Use `--quiet`, `--digest-output`, assertions, or bounded hash/marker
  extraction for sensitive or large responses.
- Use `--raw-dir <dir>` only for a known-safe scoped response. Redirect stdout
  when the artifact, rather than transcript output, is the intended consumer.
- When consuming a persisted response, record the MCP call-log length before
  the call. Afterwards, inspect only newly appended records and require exactly
  one successful record for the expected server/tool:

  ```bash
  MCP_CALL_LOG=".todo/_mcp-log/mcporter-calls.jsonl"
  if [[ -f "$MCP_CALL_LOG" ]]; then
    MCP_CALL_LOG_START="$(wc -l <"$MCP_CALL_LOG")"
  else
    MCP_CALL_LOG_START=0
  fi

  # Run exactly one persisted kent-mcp-call here.

  RAW_OUTPUT_PATH="$(
    tail -n "+$((MCP_CALL_LOG_START + 1))" "$MCP_CALL_LOG" |
      jq -ser \
        --arg server "<expected-server>" \
        --arg tool "<expected-tool>" \
        'map(select(
          .server == $server and
          .tool == $tool and
          .exitCode == 0 and
          .rawOutputPath != null
        )) |
        if length == 1 then
          .[0].rawOutputPath
        else
          error("expected exactly one persisted MCP response")
        end'
  )"
  test -s "$RAW_OUTPUT_PATH"
  ```

  Read that exact `RAW_OUTPUT_PATH` before the next MCP call. Never guess a
  timestamped filename.
- Mutating calls require `--allow-mutate` and explicit user approval.
- Mobile MCP is stateless. Discover with `mobile.device action=list`, then pass
  `platform=android` and the exact locked `deviceId` on every target-specific
  call. Do not use `set`, `get_target`, `list_modules`, or `enable_module`.
- Every Mobile call other than device discovery requires a safe output mode.
  If a tool lacks explicit `deviceId`, use `adb -s` instead of implicit state.
- Do not use `jetbrains.build_project`; use Gradle for builds and diagnostics.
- Serena is a `~/.kent/bin/kent-mcp-call` target. Follow
  `references/rules/serena.md`.

## Subagents

Use configured Kent roles:

```bash
kent run --agent project-researcher --workspace "$PWD" "<prompt>"
kent run --agent build-doctor --workspace "$PWD" "<prompt>"
kent run --agent compose-reviewer --workspace "$PWD" "<prompt>"
kent run --agent domain-model-reviewer --workspace "$PWD" "<prompt>"
```

Use subagents for broad read-only search, noisy diagnostics, and review. The active implementation or fix session
remains the single writer. Do not run parallel implementation writers.

## Safety

- Do not create or update global feature pointer files such as `.todo/.current`.
- Do not commit or push unless explicitly requested.
- Keep local paths, tokens, MCP configs, raw MCP outputs, and call logs out of git.
- Mark plan steps complete only after verification succeeds.
