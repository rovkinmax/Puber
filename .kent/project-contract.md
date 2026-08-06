# Puber Kent Project Contract

This file contains Puber-specific deltas for the shared Kent Engineering Kit
workflow. Generic lifecycle, fan-out, approval, recovery, evidence, PR, and
cleanup semantics come from the installed kit contracts.

## Sources And Context

- Repository index: `AGENTS.md`.
- Profile: `.kent/workflow-profile.toml`.
- Node reading budgets: `.kent/context/*.md`.
- Android TV workflow skill:
  `.kent/skills/puber-android-workflow/SKILL.md`.
- Mobile lease/evidence adapters:
  `.kent/adapters/mobile/emulator-resource-lock.sh` and
  `.kent/adapters/mobile/mobile-evidence-audit.sh`.
- MCP policy: `.kent/adapters/mcp/policy`.

The node manifest is the read allowlist. Load rules and recipes only when its
trigger matches the task.

## Lifecycle And Durable State

- Kent owns task lifecycle.
- `plan.md` owns writer-step progress.
- `meta.json` stores identity/source metadata only.
- `workspace_path` is always the repository or managed-worktree root.
- Fix and Smoke use ignored atomic checkpoints under
  `.kent/runtime/<TASK-ID>/`.
- Every agent node appends evidence/context metrics through
  `.kent/scripts/workflow-evidence-ledger`. Never rewrite prior slice evidence.

## Planning And Writers

- Plan selects exactly one profile `work_kind` and uses its mapped Plan
  procedure in one session without nested prompt workflows.
- Writer checkboxes exclude Smoke, review, and delivery stages.
- Implement/Fix are the only production writers and complete one bounded slice
  per fresh session.
- A `continue_fix` checkpoint action is consumed when the next Fix session is
  created. Non-empty incoming findings are the new assignment: select and
  complete one concrete slice instead of repeating a transition-only loop.
- Standards, Specification, Gate, Smoke, and Compliance are workflow-owned and
  are not duplicated by writers.

## Branch Identity

- Kent keeps `PUB-*` for lifecycle/checkpoint identity.
- A same-repository GitHub issue resolves the new branch to
  `issue-<number>` after Plan and before Implement.
- Source URL wins, followed by same-repository task-body URLs.
- Cross-repository issues, missing identity, or non-issue tasks keep the Kent
  branch. Collisions block without ref reuse.
- A PR that fully resolves the same-repository issue uses `Fixes #<number>`;
  partial/cross-repository relationships are non-closing links.

## Build And Verification

- Main compile: `:app:compileDevDebugKotlin`.
- Main checkout may use `./gradlew`; every worktree uses `./tools/agentw`.
- Worktree SDK setup writes only `sdk.dir`; task secrets are never copied.
- Standards findings require a new/worsened differential against the pinned
  task baseline. Current `origin/master` is separate integration state.
- Deterministic workflow verification uses the project command selected by the
  profile.

## Android TV Smoke

- Conditional policy is decided by `.kent/commands/smoke-policy.md`.
- Execution follows `.kent/commands/smoke-test.md`.
- Use a TV emulator selected by exact serial; physical TV/device use requires
  explicit authorization.
- Acquire the shared lease before build/install/launch/input/logs/MCP.
- On resume, reconcile the checkpoint. If its token still owns the emulator,
  use `emulator-resource-lock.sh resume <serial> <token>` rather than
  self-blocking on a new acquisition.
- Install a fresh dev APK. Runtime evidence covers visible behavior,
  focus/navigation, integration, restoration, and liveness. Deterministic tests
  may cover non-observable state logic.
- Playback-progress, account, subscription, or server-visible mutations require
  explicit task/comment authorization.

## MCP

- Use `~/.kent/bin/kent-mcp-call` and
  `~/.kent/bin/kent-mcp-list`.
- Puber has no Jira adapter; generated prompts must not assume Jira.
- Keep credentials, broad/raw UI/log/network state, and private MCP config out
  of Git and evidence.

## Pull Requests, Release, And Cleanup

- Normal Delivery may commit/push only the task branch and create/update its
  PR. It never merges.
- Merge strategy is resolved from repository rules; method-specific feasibility
  is required.
- CI monitoring waits on exact GitHub state and retries only proven
  infrastructure-cancelled jobs, at most twice.
- Puber release remains a separate workflow: next minor by default, explicit
  patch/major overrides, version PR merged before tag approval, Russian
  user-facing release notes, terminal automation monitoring, then GitHub
  Release note verification.
- Cleanup is report-first. Task Janitor removes only exact clean recoverable
  task resources and may delete a merged remote branch only when its OID still
  equals the merged PR head.
