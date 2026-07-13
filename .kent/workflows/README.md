# Puber Kent Workflows

Kent workflow graphs live in the Kent server database. JSON exports, when present, are audit snapshots, not the source of
truth. The current CLI can inspect and edit workflows, but does not import these snapshots as canonical definitions.

## Portable Pattern

Do not link Appsome workflow instances directly into Puber. Reuse the graph family and transition contract, then create a
Puber workflow instance whose prompts call `.kent/project-contract.md`.

The intended layering is:

```text
Kent Desktop workflow graph
  -> stable project contract params and command names
      -> Puber .kent/commands/*
          -> puber-android-workflow recipes, rules, adapters, and agents
```

## Puber Workflow Set

- `Puber Feature Delivery` (default): plan -> implement loop -> audit -> fix loop -> optional smoke -> compliance ->
  create/update PR -> monitor CI -> waiting PR -> cleanup -> done.
- `Puber Refactor With Audit`: plan/audit -> implement loop -> read-only review -> fix loop -> compliance ->
  create/update PR -> monitor CI -> waiting PR -> cleanup -> done.
- `Puber Bugfix Investigation`: reproduce/diagnose -> fix or report-only -> verify/fix loop -> compliance ->
  create/update PR when changes exist -> monitor CI -> waiting PR -> cleanup -> done.
- `Puber Dependency Update`: update Gradle versions/tooling -> fallout verification -> fixes -> compliance ->
  create/update PR -> monitor CI -> waiting PR -> cleanup -> done.
- `Puber Test Coverage`: coverage gap plan -> test implementation loop -> review/fix loop -> compliance ->
  create/update PR -> monitor CI -> waiting PR -> cleanup -> done.
- `Puber Smoke Test`: focused device smoke test -> optional fix -> rerun smoke -> compliance ->
  create/update PR when changes exist -> monitor CI -> waiting PR -> cleanup -> done.
- `Puber Release`: default next minor release from `origin/master` -> version bump branch/PR -> CI -> approved tag
  publication after the PR is merged -> optional automation monitor -> cleanup -> done. Patch/major releases require
  explicit task wording.
- `Puber Compile Verify Canary`: non-default script-node canary for deterministic `compileDevDebugKotlin` verification.
  Keep it isolated from product workflows until its backlog canary task passes from a committed revision.

Only `Puber Feature Delivery` should be the project default. The other workflows are linked to the project for explicit
task creation when the work type is known.

Legacy split release workflows (`Puber Release Preparation` and `Puber Release Publication`) are superseded by
`Puber Release` and must not be linked to the project for new tasks.

## Authoring Rules

- Use `default` as node assignee unless Kent workflow validation can see project-local roles.
- Delegate to project roles inside prompts, for example `kent run --agent implementation-worker ...`.
- Project role aliases are configured in `.kent/config.toml`; for example `project-researcher` maps to
  `subagents/android-codebase-analyst.md`, even though there is no `subagents/project-researcher.md` file.
- After adding or changing subagent roles in `.kent/config.toml`, restart Kent service/GUI before expecting execution
  validation or new workflow tasks to see the role.
- Do not model recoverable blockers as terminal states. Use `needs_user_action` back to the same node with
  `blocker_reason` and human approval.
- Every successful work-product path must pass through `compliance` before `cleanup`. Compliance Review is not a
  replacement for audit/review/verify/smoke; it only checks adherence to AGENTS.md, project contracts, specs, plans,
  human-approved design decisions, and workflow transition contracts.
- Code-producing workflows must create or update a PR after compliance passes. `ship_pr` may skip PR only for explicit
  no-diff/report-only/smoke-only cases and must explain that through `pr_report`. That `no_pr` path must require user
  approval before cleanup because it finishes without a merged PR.
- `ci_monitor` never merges PRs and never pushes new commits. CI failures go back to fix/review/compliance before another
  PR/CI pass.
- `done` is terminal and must mean delivered: PR merged and cleanup completed, release published and cleanup completed,
  user-approved no-diff/report-only cleanup completed, or explicit `wont_do`.
- `waiting_pr` is the normal post-CI state for PR workflows. It may only advance to cleanup after GitHub reports
  `state=MERGED`, or to release publication after the release PR is merged and the publish transition is approved.
- `wont_do` is terminal and approval-gated. Use it only for explicit user cancellation or "not planned"; do not use it as
  a recoverable blocker.
- Recoverable external waits should use `needs_user_action` back to the same node with human approval. Recoverable
  PR/branch/CI fallout should use `needs_changes` back to `fix` or `prepare` without a manual approval, except
  `ship_pr -> needs_changes`, which remains approval-gated because it can involve rebase/force-push policy.
- Smoke workflows must acquire a shared mobile resource lock through
  `.kent/adapters/mobile/emulator-resource-lock.sh` before installing, launching, or controlling an emulator/device. When
  multiple `adb` emulators are already running, agents should acquire any free emulator-specific lock and pass that serial
  to `adb -s`. Starting another emulator is allowed only when the task/user explicitly permits parallel device usage and
  the agent acquires a distinct lock for that emulator. Physical devices must not be used unless the task/user explicitly
  provides permission and an explicit serial for that physical device; agents must never rely on adb's default target
  selection. Smoke workflows must build APKs and install with explicit `adb -s "$DEVICE_SERIAL"`; Gradle `install*` tasks
  are forbidden for smoke tests.
- Every successful terminal path should pass through `cleanup`, but cleanup is conservative by default. Cleanup after a PR
  path must verify the PR through GitHub state rather than git ancestry alone because squash merges are allowed.
- Pass explicit `workspace_path` and `plan_path`; never rely on `.todo/.current`.
- Keep prompts project-neutral where possible: "run the project feature planning command" rather than naming another
  repository's skill path, Jira project, module graph, or release process.

## Portability Next Step

Snapshots are not enough for reuse across repositories because Kent workflow graphs live in the Kent DB. The next
practical extraction should be a global Kent workflow template/generator that creates project-local graph instances from a
stable contract:

- Global: graph families, transition parameter contract, naming rules, and safe cleanup/release gates.
- Project-local: `.kent/project-contract.md`, command files, adapters, worktree setup, and subagent alias mapping.

That keeps reusable orchestration global while preserving project-specific build commands, release policy, MCP adapters,
and architecture rules.

## Workflow Smoke Test Checklist

Before making a workflow default for the project:

- Create a dummy task that reaches planning and emits `workspace_path`/`plan_path`.
- Exercise a `needs_user_action` self-loop and verify `blocker_reason` is visible.
- Exercise a `waiting_pr` path and verify an unmerged PR waits instead of reaching `done`.
- Exercise an implementation continuation path and verify params are re-emitted.
- Exercise cleanup in conservative mode and verify `cleanup_report`.
- Validate with `kent workflow validate "<workflow>" --mode execution`.
