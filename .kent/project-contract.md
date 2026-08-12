# Puber Kent Project Contract

This file contains only Puber-specific deltas for the shared Kent Engineering
Kit. Global lifecycle, authority, writer, review, recovery, evidence, PR,
waiting, and cleanup semantics come from the installed kit roles and generated
workflow.

## Context Sources

- Repository rules and gotchas: `AGENTS.md`.
- Profile and node budgets:
  `.kent/workflow-profile.toml` and `.kent/context/*.md`.
- Android TV workflow index:
  `.kent/skills/puber-android-workflow/SKILL.md`.
- Mobile lease/evidence adapters: `.kent/adapters/mobile/`.
- MCP policy: `.kent/adapters/mcp/policy`.

The active manifest is the read allowlist. Load a rule or recipe only when its
trigger matches the current node and task.

## Planning And Identity

- Feature artifacts live under `.todo/<feature>/`; `plan.md` owns writer-step
  progress and `meta.json` stores identity/source metadata only.
- Kent keeps `PUB-*` as task identity.
- After Plan, a same-repository GitHub issue resolves the branch to
  `issue-<number>`. Source URL wins, followed by same-repository task-body
  issue URLs.
- Cross-repository issues, missing identity, or non-issue tasks keep the Kent
  branch. Existing local or remote collisions block without ref reuse.
- A PR that fully resolves the same-repository issue uses `Fixes #<number>`;
  partial and cross-repository relationships are non-closing links.

## Build

- Main compile task: `:app:compileDevDebugKotlin`.
- The primary checkout may use `./gradlew`; every worktree uses
  `./tools/agentw`.
- Worktree SDK setup writes only `sdk.dir`; KinoPub/TMDB credentials are never
  copied.
- Deterministic workflow verification uses the command selected by the profile.

## Android TV Smoke

- Conditional routing follows `.kent/commands/smoke-policy.md`; execution
  follows `.kent/commands/smoke-test.md`.
- Use a TV emulator selected by exact serial. Physical TV/device use requires
  explicit authorization.
- Acquire the shared lease before build, install, launch, input, logs, or MCP.
  On resume, reuse a still-owned checkpoint token through the adapter's
  `resume` operation.
- Build a fresh task APK, but preserve app data by installing only through
  `.kent/adapters/mobile/android-apk-install-preserve`. Unknown signer,
  downgrade, signer mismatch, or install failure blocks the replacement.
  Never uninstall, clear package data, allow downgrade, or replace a signer
  without separate explicit authority.
- A resumed Smoke run may reuse a checkpointed install only when the APK
  SHA-256 and required authenticated state still match. Runtime proof covers
  visible behavior, focus/navigation, integration, restoration, and liveness.
- Playback-progress, account, subscription, or server-visible mutations require
  explicit task or comment authority.

## MCP And Release

- Puber has no Jira adapter; generated tasks must not assume Jira.
- MCP calls use `~/.kent/bin/kent-mcp-call` and
  `~/.kent/bin/kent-mcp-list`.
- Credentials, broad/raw UI, logs, network state, and private MCP config stay
  outside Git and evidence.
- Merge strategy is resolved from repository rules.
- Release remains a separate workflow: version PR, merge, tag approval,
  Russian user-facing notes, automation monitoring, and GitHub Release
  verification.
