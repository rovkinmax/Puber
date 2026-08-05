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

- `Puber Engineering Delivery v12` (default): common delivery for
  `feature`, `bugfix`, `refactor`, `migration`, `dependency`, and `test`.
  Plan selects one `work_kind`, Implement preserves it across bounded slices,
  and the common verification, Smoke, compliance, PR/CI, waiting, and cleanup
  tail handles delivery.
- `Puber Engineering Delivery v13` (staged, non-default): adds deterministic
  same-repository GitHub issue branches as `issue-<number>` before Plan and
  keeps `PUB-*` when no usable issue URL exists. The PR body uses `Fixes #N`
  only when the task fully resolves that Puber issue.
- `Puber Release`: next minor release from `origin/master` through
  version-bump PR, CI, approved tag publication, optional automation
  monitoring, and cleanup. Patch/major releases require explicit wording.

`Puber Release` assigns operational ownership directly: `release-manager` owns
version/tag lifecycle, `delivery-operator` owns PR/Cleanup, and `ci-monitor`
owns CI, merge-state, and release automation.

Covered specialized workflows and earlier Delivery versions are retired.
Completed and canceled task history is not a retention requirement.

Before retiring any workflow, read current Kent-owned state instead of relying
on task IDs or status captured in this repository:

```bash
kent workflow list --project /Users/rovkinmax/dev/android/Puber --json
kent task list --project /Users/rovkinmax/dev/android/Puber --json
```

Group tasks by the current workflow ID. Recreate every current Backlog task in
the replacement graph with its title, body, source URL, labels, and relevant
comments, then record the old-to-new short-ID mapping. Running,
approval-waiting, or otherwise active tasks must finish or be explicitly
canceled before deletion. Completed and canceled history may be discarded.
Do not move task records between incompatible graphs.

## Revision Preflight

Before starting a task from a selected revision, verify that the revision
contains a complete and valid project adapter:

```bash
~/.kent/bin/kent-preflight-revision \
  --project /Users/rovkinmax/dev/android/Puber \
  --ref origin/master
```

Use the exact selected task ref in place of `origin/master` when starting from
another branch or commit.

## Authoring Rules

- Assign operational nodes directly from `.kent/workflow-profile.toml`.
  `default` is reserved for Plan orchestration.
- `[workflow] subagents = true` controls nested delegation only. It does not
  route direct workflow-node roles.
- Keep workflow-level fan-out read-only. The feature verification workflow runs
  audit and deterministic compilation in parallel, joins both reports, and
  sends any required code changes through the existing single-writer Fix node.
- Each branch must provide one stable join contract. Kent `2.2.0` runtime rejects mutually exclusive output field sets
  as missing aggregate fields even when draft and execution validation pass.
- Every fan-out branch must transition directly to its join. Kent `2.2.0` drops parallel branch lineage on an
  intermediate node, leaving the task active with no placement after both branches complete.
- Project role aliases are configured in `.kent/config.toml`; for example `project-researcher` maps to
  `subagents/android-codebase-analyst.md`, even though there is no `subagents/project-researcher.md` file.
- After adding or changing subagent roles in `.kent/config.toml`, restart Kent service/GUI before expecting execution
  validation or new workflow tasks to see the role.
- Do not model recoverable blockers as terminal states. Use `needs_user_action` back to the same node with
  `blocker_reason` and human approval.
- Every successful code-producing PR delivery path must pass through
  `compliance` before PR preparation. Report-only Canary/Smoke Lab experiments
  are exempt. Compliance Review is not a replacement for
  audit/review/verify/smoke; it only checks adherence to AGENTS.md, project
  contracts, specs, plans, human-approved design decisions, and workflow
  transition contracts.
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
- Pass explicit `workspace_path`, `plan_path`, and `work_kind`; never rely on
  `.todo/.current`.
- Keep prompts project-neutral where possible: "run the project feature planning command" rather than naming another
  repository's skill path, Jira project, module graph, or release process.

## Shared Generator

Snapshots are not enough for reuse across repositories because Kent workflow
graphs live in the Kent DB. `kent-engineering-kit/scripts/generate-workflow`
now creates versioned project-local graph instances from a stable contract:

- Global: graph families, transition parameter contract, naming rules, and safe cleanup/release gates.
- Project-local: `.kent/project-contract.md`, command files, adapters, worktree setup, and subagent alias mapping.

That keeps reusable orchestration global while preserving project-specific
build commands, release policy, MCP adapters, and architecture rules.

## Workflow Smoke Test Checklist

Before making a workflow default for the project:

- Create a dummy task that reaches planning and emits `workspace_path`/`plan_path`.
- Exercise a `needs_user_action` self-loop and verify `blocker_reason` is visible.
- Exercise a `waiting_pr` path and verify an unmerged PR waits instead of reaching `done`.
- Exercise an implementation continuation path and verify params are re-emitted.
- Exercise cleanup in conservative mode and verify `cleanup_report`.
- Validate with `kent workflow validate "<workflow>" --mode execution`.
- Reapply the same taskless experimental graph while iterating. Once tasks
  reference it, preserve that graph and use another experimental label for
  semantic changes.
- Before deleting an obsolete workflow, preview deletion and classify attached
  tasks. Recreate Backlog tasks under the replacement graph; completed and
  canceled history may be discarded. Active or approval-waiting tasks must
  finish or be explicitly canceled first.
