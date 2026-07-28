# Puber Kent Project Contract

This file is the stable adapter between portable Kent Desktop workflow patterns and Puber-specific commands, agents,
builds, source adapters, and cleanup policy. Workflow prompts should reference this contract instead of hardcoding
project internals from another repository.

## Stable Workflow Outputs

Puber workflow commands must use explicit task artifacts. Do not infer a feature from `.todo/.current`.

- Planning produces `workspace_path`, `plan_path`, and `work_kind`. `workspace_path` is
  always the repository or managed-worktree root; `.todo/<feature>` is an
  artifact directory and belongs in `plan_path` or another explicit artifact
  field.
- Implementation preserves and re-emits `workspace_path`, `plan_path`, and `work_kind` until all steps are complete.
- Audit/review produces `audit_report` or `review_report`.
- Generated Delivery workflows use `standards_status`
  and `standards_report` for early Standards Review.
- Verification produces `verification_report` or a concise verification summary.
- Final Compliance Review produces `compliance_report`.
- PR creation produces `pr_url`, `branch_name`, `workspace_path`, and the
  resolved `merge_strategy`; no-diff/report-only PR skips produce `pr_report`.
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
- `bugfix_start_command`: `.kent/commands/bugfix-start.md`
- `bugfix_implement_command`: `.kent/commands/bugfix-implement.md`
- `smoke_command`: `.kent/commands/smoke-test.md`
- `mobile_resource_lock_adapter`:
  `.kent/adapters/mobile/emulator-resource-lock.sh`
- `mobile_evidence_audit_adapter`:
  `.kent/adapters/mobile/mobile-evidence-audit.sh`
- `ship_pr_command`: `.kent/commands/ship-pr.md`
- `release_command`: `.kent/commands/release.md`
- `release_prepare_command`: `.kent/commands/release-branch.md`
- `release_tag_command`: `.kent/commands/release-tag.md`
- `cleanup_command`: `.kent/commands/cleanup-task.md`

Commands that operate on feature artifacts accept an explicit feature name, `.todo/<feature>` path, or workflow-provided
workspace path.

The generated Engineering Delivery Plan node selects one profile-owned
`work_kind` and performs planning in one Kent session. Feature planning may
load `feature-design.md`, `feature-spec.md`, and `feature-plan.md` as procedure
modules, but must not invoke nested `/prompt:*` flows or start child sessions.
Its
post-verification Gate follows `.kent/commands/smoke-policy.md` and records an
explicit `smoke_required` or `delivery_ready` decision.

## Agent Contract

Generated Delivery nodes use direct profile roles:

- Plan uses `default`; Gate uses global `workflow-gate`.
- Implement and Fix use project-local `implementation-worker`.
- Smoke uses global `runtime-smoke-tester` with the Puber Android TV procedure.
- PR preparation and Cleanup use global `delivery-operator`.
- CI and Waiting PR use global `ci-monitor`.

- `.kent/config.toml` enables `[workflow] subagents = true`.
- That setting controls nested delegation only; direct workflow-node roles do
  not depend on it.
- Nested research and build-diagnosis roles remain explicitly marked
  `agent_callable = true` and `workflow_subagent = true`.
- Role prompts define behavior only. Model, reasoning, verbosity, tools, and delegation eligibility are owned by Kent
  configuration; role-prompt frontmatter must not declare `model` or `tools`.
- Generated Delivery Standards, Specification, and Compliance nodes own final review. Implementation and Fix procedures
  must not launch nested final reviewers that duplicate those graph stages.

- `project-researcher`: Puber codebase research, alias for `android-codebase-analyst`.
- `implementation-worker`: bounded feature step implementation.
- `quality-reviewer`: read-only quality audit.
- `build-doctor`: Gradle diagnostics, alias for `gradle-build-doctor`.
- `compose-reviewer`: Compose-specific review.
- `domain-model-reviewer`: data/domain/UI mapper review.

## Build And Test Policy

- Main compile check: `./gradlew :app:compileDevDebugKotlin`.
- Kent worktree compile check: `./tools/agentw :app:compileDevDebugKotlin`.
- Detekt findings are task-scoped only when comparison with the pinned
  `origin/master` baseline proves a new or worsened violation. A failing full
  repository Detekt run and a touched path are not sufficient evidence.
  For metric rules, worsening means the same rule, path, and declaration has a
  larger measured value. For non-metric rules, it means a new normalized
  declaration signature or increased occurrence count. Line shifts do not
  count, and a lower total finding count does not waive an individual
  regression.
  Pre-existing findings are reported as baseline debt, not assigned to the
  feature writer. If an explicit absolute-clean rule conflicts with the
  baseline repository state, report `blocked` and request a policy decision;
  do not start broad cleanup.
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
- Mobile MCP must discover the acquired serial and receive
  `platform=android` plus the same explicit `deviceId` on every target-specific
  UI/input/system call. Do not use process-local target selection.
- If the inventory does not contain the locked serial, or a required operation
  cannot address it explicitly, route through `needs_user_action` or use the
  exact `adb -s` operation when the smoke procedure defines one.
- Device-side timestamp and log-boundary syntax is not portable. Validate the
  exact command before using it as an evidence gate. Command or parsing failure
  is a Smoke blocker until a verified alternative is used, never an empty
  passing signal result.
- Runtime evidence must be scoped and sanitized. Full device logs, network
  payloads, auth headers, and unexpected authenticated UI dumps are forbidden.
  `.kent/adapters/mobile/mobile-evidence-audit.sh` must pass before completion.

## Source Adapters

- MCP access goes through `~/.kent/bin/kent-mcp-call` and
  `~/.kent/bin/kent-mcp-list`; do not call raw `mcporter`.
- `.kent/adapters/mcp/policy` classifies Puber-specific tools. Unknown tools
  inherit the global fail-closed mutation policy.
- Mobile MCP is stateless. Discover devices with `mobile.device action=list`,
  then pass `platform=android` and the exact locked `deviceId` to every
  target-specific call. Do not use process-local target selection.
- Known-safe persisted responses must redirect command stdout and be consumed
  through the exact successful `rawOutputPath` from
  `.todo/_mcp-log/mcporter-calls.jsonl`.
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

- `.kent/workflow-profile.toml` uses `pr_merge_strategy = "auto"`. Resolve it
  from GitHub repository-enabled methods, `master` branch protection/rulesets,
  and merge-queue policy. Continue only when exactly one method remains; do not
  guess from the PR UI.
- Supported explicit policies are `merge`, `squash`, and `rebase`. The selected
  method must remain enabled and compatible with the target branch.
- Generic `mergeable=MERGEABLE` or `mergeStateStatus=CLEAN` proves only that
  the final tree can merge. For rebase delivery, GitHub GraphQL
  `canBeRebased=true` is separately required. A clean merge-tree or proof that
  `master` is already an ancestor does not replace the replay check.
- Diagnose conflicting rebase signals only in an isolated temporary clone or
  branch with a forced replay onto fresh `origin/master`; do not mutate the
  task branch while investigating.
- `ci_monitor` routes successful or intentionally skipped checks to `waiting_pr`.
- `waiting_pr` checks the pull request through GitHub. It must not merge, push, tag, or clean up.
- If the PR is still open, `waiting_pr` writes a task comment with the current PR status and takes the approval-gated
  `needs_user_action` self-loop.
- If the PR has review comments, conflicts, or post-CI regressions that fit the task scope, `waiting_pr` takes
  `needs_changes` back to `fix` or `prepare`.
- History rewriting or force-pushing requires exact user authorization naming
  the task branch and repair. Preserve the old remote head in a local backup,
  verify the authorized final-tree invariant, and use force-with-lease pinned
  to the expected remote head. Any mismatch returns `needs_user_action`.
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

## Naming Policy

Use generic workflow graph keys and project-prefixed live workflow names:

- Generated default: `Puber Engineering Delivery v9`.
- Temporary Canary and Smoke Lab names are reserved for bounded future
  experiments and should be removed after their evidence is incorporated.
- Live workflow names: `Puber Engineering Delivery v9` and `Puber Release`.
- Node keys include `plan`, `implement`, `verification_dispatch`,
  `deterministic_verify`, `standards_review`, `spec_review`,
  `verification_join`, `verification_gate`, `fix`, `smoke`, `compliance`,
  `prepare_pr`, `ci_monitor`, `waiting_pr`, `cleanup`, `done`, and `wont_do`.
- Transition IDs include `implement`, `continue_implementation`, `verify`,
  `fanout_verify`, `reported`, `evaluate`, `needs_changes`,
  `needs_user_action`, `smoke_required`, `delivery_ready`, `ship_pr`,
  `monitor_ci`, `waiting_pr`, `pr_merged`, `close_without_merge`, `no_pr`,
  `done`, and `wont_do`.
- Portable params: `workspace_path`, `plan_path`, `work_kind`, `review_context`,
  `fix_context`, `verification_status`, `verification_report`,
  `standards_status`, `standards_report`, `spec_status`, `review_report`,
  `compliance_report`, `smoke_rationale`, `smoke_scope`, `blocker_reason`,
  `pr_url`, `branch_name`, `pr_report`, `ci_report`, `merge_report`,
  `closure_reason`, and `cleanup_report`. Release uses its dedicated
  parameters.
