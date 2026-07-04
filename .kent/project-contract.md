# Puber Kent Project Contract

This file is the stable adapter between portable Kent Desktop workflow patterns and Puber-specific commands, agents,
builds, source adapters, and cleanup policy. Workflow prompts should reference this contract instead of hardcoding
project internals from another repository.

## Stable Workflow Outputs

Puber workflow commands must use explicit task artifacts. Do not infer a feature from `.todo/.current`.

- Planning produces `workspace_path` and `plan_path`.
- Implementation preserves and re-emits `workspace_path` and `plan_path` until all steps are complete.
- Audit/review produces `audit_report` or `review_report`.
- Verification produces `verification_report` or a concise verification summary.
- Compliance Review produces `compliance_report`.
- PR creation produces `pr_url`, `branch_name`, and `workspace_path`; no-diff/report-only PR skips produce `pr_report`.
- PR/CI monitoring produces `ci_report`.
- Every blocked transition must provide `blocker_reason`.
- Cleanup produces `cleanup_report`.

## Command Contract

- `feature_start_command`: `.kent/commands/feature-start.md`
- `feature_implement_command`: `.kent/commands/feature-implement.md`
- `feature_audit_command`: `.kent/commands/feature-audit.md`
- `feature_fix_command`: `.kent/commands/feature-fix.md`
- `refactor_start_command`: `.kent/commands/refactor-start.md`
- `migration_start_command`: `.kent/commands/migration-start.md`
- `smoke_command`: `.kent/commands/smoke-test.md`
- `ship_pr_command`: `.kent/commands/ship-pr.md`
- `release_prepare_command`: `.kent/commands/release-branch.md`
- `release_tag_command`: `.kent/commands/release-tag.md`
- `cleanup_command`: `.kent/commands/cleanup-task.md`

Commands that operate on feature artifacts accept an explicit feature name, `.todo/<feature>` path, or workflow-provided
workspace path.

## Agent Contract

Workflow nodes should generally use the `default` assignee for Kent Desktop validation portability. Prompts may delegate
to project-local capability roles:

- `project-researcher`: Puber codebase research, alias for `android-codebase-analyst`.
- `implementation-worker`: bounded feature step implementation, alias for `feature-step-worker`.
- `feature-orchestrator`: parallel feature step orchestration, alias for `feature-parallel-orchestrator`.
- `quality-reviewer`: read-only quality audit.
- `build-doctor`: Gradle diagnostics, alias for `gradle-build-doctor`.
- `compose-reviewer`: Compose-specific review.
- `domain-model-reviewer`: data/domain/UI mapper review.

## Build And Test Policy

- Main compile check: `./gradlew :app:compileDevDebugKotlin`.
- Kent worktree compile check: `./tools/agentw :app:compileDevDebugKotlin`.
- Main checkout may use direct Gradle; Kent worktrees must use `./tools/agentw` to isolate Gradle state.
- Device smoke tests must acquire a shared mobile resource lock before touching an emulator/device.
- If multiple `adb` emulators are already running, smoke agents should acquire any free emulator-specific lock and use
  that serial with `adb -s`.
- Starting another emulator is allowed only when the task/user explicitly permits parallel device usage and the agent
  acquires a distinct lock for it.
- Device smoke tests must always install the freshly built dev APK before launch, even if the user says the app is already
  running.

## Source Adapters

- MCP access goes through `.kent/adapters/mcp/mcp-call.sh`.
- Figma, JetBrains, Serena, Firebase, and mobile MCP are optional and must degrade gracefully when unavailable.
- Puber currently has no project-local Jira adapter. Workflow prompts must not assume Jira availability.

## Cleanup Policy

Default cleanup is conservative because deleting Kent-managed task worktrees can leave old sessions bound to stale
worktree metadata until Kent rebind behavior is fixed.

- `cleanup_managed_task_worktrees`: `false` by default.
- Code-producing workflow cleanup must happen after PR creation/update and CI monitoring, or after an explicit
  no-diff/report-only `pr_report`.
- Cleanup nodes should report safe-to-remove worktrees and branches unless explicit project/user policy enables removal.
- Destructive cleanup requires proof that worktrees are clean and branch commits are recoverable from remote refs or a
  merged PR.
- Cleanup always emits `cleanup_report`; skipped cleanup is a valid result and must be visible.

## Naming Policy

Use generic workflow graph keys and project-prefixed live workflow names:

- Live workflow names: `Puber Feature Delivery`, `Puber Refactor With Audit`, `Puber Bugfix Investigation`,
  `Puber Dependency Update`, `Puber Test Coverage`, `Puber Smoke Test`, `Puber Release Preparation`,
  `Puber Release Publication`.
- Node keys: `plan`, `implement`, `audit`, `fix`, `smoke`, `ship_pr`, `ci_monitor`, `cleanup`, `done`, `blocked`.
- Transition IDs: `implement`, `continue_implementation`, `audit`, `needs_changes`, `smoke`, `ship_pr`, `monitor_ci`,
  `done`, `blocked`.
- Portable params: `workspace_path`, `plan_path`, `audit_report`, `review_report`, `verification_report`, `pr_url`,
  `branch_name`, `pr_report`, `ci_report`, `compliance_report`, `blocker_reason`, `cleanup_report`.
