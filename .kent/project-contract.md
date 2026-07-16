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
- Waiting PR produces `blocker_reason` while the PR is open,
  `merge_report` after GitHub reports `state=MERGED`, or `pr_report` when PR
  review/conflict/post-CI feedback must be fixed.
- Release preparation produces `release_version`, `release_type`, `release_branch`, `release_tag`, `workspace_path`,
  `version_bump_commit`, and `verification_summary`.
- Release publication produces `target_commit`, `tag_push_status`, and `release_report`.
- Every recoverable wait transition must provide `blocker_reason`.
- Explicit task cancellation produces `closure_reason` or `cleanup_reason`.
- Cleanup produces `cleanup_report`.

## Lifecycle State Authority

- The Kent task's current node, transition history, approvals, and comments are the workflow lifecycle source of truth.
- `plan.md` checkboxes are the source of implementation-step progress within a planning artifact.
- `meta.json` stores stable identity plus source/artifact metadata such as task IDs, Figma sources, screens, and spec
  origin. Commands must not write lifecycle mirrors such as `status`, `currentStep`, `totalSteps`, or `stepHistory`.
- Existing lifecycle fields in old `.todo` workspaces are compatibility-only and must not drive new decisions.

## Command Contract

- `feature_start_command`: `.kent/commands/feature-start.md`
- `feature_implement_command`: `.kent/commands/feature-implement.md`
- `feature_audit_command`: `.kent/commands/feature-audit.md`
- `feature_fix_command`: `.kent/commands/feature-fix.md`
- `refactor_start_command`: `.kent/commands/refactor-start.md`
- `migration_start_command`: `.kent/commands/migration-start.md`
- `smoke_command`: `.kent/commands/smoke-test.md`
- `ship_pr_command`: `.kent/commands/ship-pr.md`
- `release_command`: `.kent/commands/release.md`
- `release_prepare_command`: `.kent/commands/release-branch.md`
- `release_tag_command`: `.kent/commands/release-tag.md`
- `cleanup_command`: `.kent/commands/cleanup-task.md`

Commands that operate on feature artifacts accept an explicit feature name, `.todo/<feature>` path, or workflow-provided
workspace path.

The Feature Delivery `plan` node owns bootstrap, optional design ingestion, spec creation, and implementation planning in
one Kent session. `feature-start.md` may load `feature-design.md`, `feature-spec.md`, and `feature-plan.md` as procedure
modules, but must not invoke nested `/prompt:*` flows or start child sessions for those phases.
The generated `Puber Engineering Delivery v5` Plan node follows the same
single-session procedure through `.kent/workflow-profile.toml`. Its
post-verification Gate follows `.kent/commands/smoke-policy.md` and records an
explicit `smoke_required` or `delivery_ready` decision.
`Puber Engineering Canary v2` uses generic Plan/Implement/Fix prompts and
disables Smoke and PR/CI delivery stages.
`Puber Engineering Smoke Lab` keeps the conditional Smoke Gate and runtime
procedure but routes both successful branches to conservative cleanup without a
PR/CI tail.

## Agent Contract

Workflow nodes should generally use the `default` assignee for Kent Desktop validation portability. Prompts may delegate
to project-local capability roles:

- `.kent/config.toml` enables `[workflow] subagents = true`.
- Canonical delegated roles are explicitly marked `agent_callable = true` and `workflow_subagent = true`; do not rely on
  Kent defaults for workflow delegation eligibility.

- `project-researcher`: Puber codebase research, alias for `android-codebase-analyst`.
- `implementation-worker`: bounded feature step implementation.
- `quality-reviewer`: read-only quality audit.
- `build-doctor`: Gradle diagnostics, alias for `gradle-build-doctor`.
- `compose-reviewer`: Compose-specific review.
- `domain-model-reviewer`: data/domain/UI mapper review.

## Build And Test Policy

- Main compile check: `./gradlew :app:compileDevDebugKotlin`.
- Kent worktree compile check: `./tools/agentw :app:compileDevDebugKotlin`.
- Main checkout may use direct Gradle.
- Project-local worktrees under `.kent/worktrees/` and Kent-managed task worktrees under
  `~/.kent/worktrees/workspace-.../<TASK-ID>` must use `./tools/agentw` to isolate Gradle state.
- `.kent/worktrees/setup.sh` attempts early SDK setup, and `tools/agentw` repeats it as a build-time fallback through
  `tools/configure-worktree-sdk`. They may seed `local.properties` with `sdk.dir` only and must not copy API secrets;
  tasks needing secrets use environment variables or explicit user-approved provisioning.
- Device smoke tests must acquire a shared mobile resource lock before touching an emulator/device.
- If multiple `adb` emulators are already running, smoke agents should acquire any free emulator-specific lock and use
  that serial with `adb -s`.
- Starting another emulator is allowed only when the task/user explicitly permits parallel device usage and the agent
  acquires a distinct lock for it.
- Physical devices, including a real TV, are forbidden unless the task/user explicitly provides permission and an
  explicit serial for that physical device. Smoke agents must never rely on adb's default target selection.
- Device smoke tests must always install the freshly built dev APK before launch, even if the user says the app is already
  running.
- Generated conditional Smoke decisions must provide `smoke_rationale`.
  `smoke_required` also provides `smoke_scope`; unavailable runtime resources
  route through `needs_user_action` and never justify `delivery_ready`.
- Smoke agents must build with `:app:assembleDevDebug` and install with explicit
  `adb -s "$DEVICE_SERIAL" install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk`; Gradle `install*` tasks are
  forbidden for smoke tests because they may target a physical device.

## Source Adapters

- MCP access goes through `.kent/adapters/mcp/mcp-call.sh`.
- Figma, JetBrains, Serena, Firebase, and mobile MCP are optional and must degrade gracefully when unavailable.
- Puber currently has no project-local Jira adapter. Workflow prompts must not assume Jira availability.

## Cleanup Policy

Default cleanup is conservative because deleting Kent-managed task worktrees can leave old sessions bound to stale
worktree metadata until Kent rebind behavior is fixed.

- `cleanup_managed_task_worktrees`: `false` by default.
- Code-producing workflow cleanup must happen after `waiting_pr` confirms the PR is merged through GitHub state, or after
  the user approves an explicit no-diff/report-only `pr_report` through the `no_pr` transition.
- Release cleanup must happen after tag publication is monitored, or after explicit user cancellation.
- Cleanup after a PR path must verify `gh pr view --json state,mergedAt,mergeCommit,headRefName,baseRefName,url` when
  GitHub CLI is available. Do not rely only on git ancestry because squash merges are allowed.
- Cleanup nodes should report safe-to-remove worktrees and branches unless explicit project/user policy enables removal.
- Destructive cleanup requires proof that worktrees are clean and branch commits are recoverable from remote refs or a
  merged PR.
- Cleanup always emits `cleanup_report`; skipped cleanup is a valid result and must be visible.

## Recoverable Blocking Policy

Recoverable blockers must not use a terminal node. The workflow keeps the task in its current stage:

- `needs_user_action`: the current stage cannot safely continue until the user or an external system resolves a blocker.
  The transition is approval-gated and must provide `blocker_reason`. It
  normally loops back to the same node; after a joined verification gate it may
  return to verification dispatch so every read-only branch reruns.
- `needs_changes`: audit/review/compliance/CI/PR feedback needs task-scoped fixes. Internal fix loops should not require
  approval; `ship_pr -> needs_changes` stays approval-gated because branch recovery can involve rebase or force-push
  policy.
- `no_pr`: the task has no repository changes or is explicitly report-only. This transition is approval-gated because it
  allows cleanup/done without a merged PR.

Terminal `wont_do` is only for explicit user cancellation or "not planned"
decisions, requires approval, and emits `closure_reason`. It is not a
recoverable blocker.

## PR Waiting Policy

`done` is reserved for delivered work, not "agent finished." For PR-producing workflows:

- `ci_monitor` routes successful or intentionally skipped checks to `waiting_pr`.
- `waiting_pr` checks the pull request through GitHub. It must not merge, push, tag, or clean up.
- If the PR is still open, `waiting_pr` writes a task comment with the current PR status and takes the approval-gated
  `needs_user_action` self-loop.
- If the PR has review comments, conflicts, or post-CI regressions that fit the task scope, `waiting_pr` takes
  `needs_changes` back to `fix` or `prepare`.
- If GitHub reports `state=MERGED`, `waiting_pr` advances to cleanup for normal workflows.
- Release workflows route `waiting_pr -> pr_merged -> publish` with human approval before tag publication.
- `close_without_merge` is approval-gated and valid only when the latest user comment explicitly says to close, cancel, or
  skip the PR.
- `no_pr` is approval-gated and valid only when the PR step produced a clear `pr_report` explaining why no PR is
  applicable.

## Release Policy

Use `Puber Release` for human-facing release tasks.

- Default release type is next minor from `origin/master`.
- Patch and major releases require explicit task wording.
- The workflow prepares the version bump, runs Compliance Review, creates/updates a PR, monitors CI, waits in
  `waiting_pr`, then publishes the tag only after explicit approval and after verifying the release PR is merged into
  `origin/master`.
- Never create or push a release tag before the version bump is present on `origin/master`.
- Legacy split workflows (`Puber Release Preparation`, `Puber Release Publication`) are not intended for new tasks.

## Naming Policy

Use generic workflow graph keys and project-prefixed live workflow names:

- Current generated non-default workflows: `Puber Engineering Delivery v5`
  and `Puber Engineering Canary v2`, plus unversioned
  `Puber Engineering Smoke Lab`. These are experimental labels; v4/v1 remain
  comparison history.
- Live workflow names: `Puber Feature Delivery`, `Puber Refactor With Audit`, `Puber Bugfix Investigation`,
  `Puber Dependency Update`, `Puber Test Coverage`, `Puber Smoke Test`, `Puber Release`.
- Node keys include `plan`, `implement`, `verification_dispatch`,
  `deterministic_verify`, `standards_review`, `spec_review`,
  `verification_join`, `verification_gate`, `fix`, `smoke`, `prepare_pr`,
  `ci_monitor`, `waiting_pr`, `cleanup`, `done`, and `wont_do`.
- Transition IDs include `implement`, `continue_implementation`, `verify`,
  `fanout_verify`, `reported`, `evaluate`, `needs_changes`,
  `needs_user_action`, `smoke_required`, `delivery_ready`, `monitor_ci`,
  `waiting_pr`, `pr_merged`, `close_without_merge`, `no_pr`, `done`, and
  `wont_do`.
- Portable params: `workspace_path`, `plan_path`, `audit_report`, `review_report`, `verification_report`, `pr_url`,
  `branch_name`, `pr_report`, `ci_report`, `compliance_report`, `merge_report`, `cleanup_reason`,
  `closure_reason`, `smoke_rationale`, `smoke_scope`, `release_version`,
  `release_type`, `release_branch`, `release_tag`, `version_bump_commit`,
  `target_commit`, `tag_push_status`, `release_report`, `blocker_reason`, `cleanup_report`.
