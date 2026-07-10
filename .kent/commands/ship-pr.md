---
description: Commit, push, create or update a pull request, and optionally monitor CI
---

# Ship PR

Use this command only from a Kent workflow `ship_pr` node after implementation review and Compliance Review passed.

## Purpose

Turn a completed task worktree into a pull request. This is the normal successful outcome for code-producing Kent
Desktop workflows.

## Preconditions

- Compliance Review passed for the work product.
- The task worktree contains the final reviewed changes.
- There are no unresolved blockers or unreviewed compliance findings.
- Release workflows may use this command for the release version-bump PR. Tag publication still uses dedicated release
  workflow gates after the PR is merged.

## Steps

1. Read `AGENTS.md`, `.kent/project-contract.md`, and the workflow input summary.
2. Inspect repository state:
   ```bash
   git status --short
   git branch --show-current
   git remote -v
   ```
3. If there are no repository changes and the task was report-only or smoke-only, report PR as not applicable and use the
   workflow's `no_pr` transition. Do not create empty commits or empty PRs. `no_pr` is approval-gated because it allows
   cleanup without a merged PR.
4. If there are changes:
   - Verify the current branch is a task/worktree branch, not `master`/`main`.
   - Stage only task-related files.
   - Commit with a concise task-scoped message.
   - Push the branch to `origin`.
   - Create or update a GitHub PR with `gh pr create` / `gh pr view` / `gh pr edit`.
5. The PR body must include:
   - Task summary.
   - Compliance Review result.
   - Verification commands and results.
   - Known skipped checks or blockers.
6. Never merge the PR.
7. Never push directly to `master`/`main`.
8. If recoverable repository, branch, or PR state prevents PR creation/update, complete `needs_changes` with
   `workspace_path` and `blocker_reason`. Do not force-push unless the latest user comment explicitly permits force-push
   for this exact PR/branch.
9. If credentials, project policy, missing user input, or an unsafe repository state prevents progress, complete
   `needs_user_action` with a precise `blocker_reason`.

## Completion Contract

Complete with:

- `monitor_ci` when a PR exists and CI should be monitored. Provide `pr_url`, `branch_name`, and `workspace_path`.
- `no_pr` only when PR is intentionally not applicable because there are no repository changes. Provide `pr_report`; the
  workflow must stop for user approval before cleanup.
- `needs_changes` when task-scoped PR/branch issues can be fixed safely. Provide `workspace_path` and
  `blocker_reason`.
- `needs_user_action` when PR creation/update cannot be completed safely without user input, credentials, or a policy
  decision. Provide `blocker_reason`.
