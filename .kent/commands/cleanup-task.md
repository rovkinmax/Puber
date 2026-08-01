---
description: Conservative Kent task cleanup report
---

# Cleanup Task

Produces a conservative cleanup report for completed Kent workflow tasks.

## Usage

```
/prompt:cleanup-task
/prompt:cleanup-task <task-short-id>
```

## Policy

The Cleanup agent is report-first and never removes its own Kent-managed
worktree. The generated Task Janitor runs after this session exits and owns
deterministic deletion.

## What It Does

1. Determine the primary checkout:
   ```bash
   git worktree list --porcelain
   ```
2. Inspect only the current task workspace and branch.
3. Report clean/dirty state, authoritative merged/no-PR proof, remote branch
   state, and any unique content.
4. Do not invoke `git worktree remove` or `kent worktree delete` for the managed
   task worktree.
5. Emit the complete `run_janitor` contract required by the workflow prompt.

## Output

Return a short human-readable `cleanup_report`:

```markdown
Cleanup report:
- Preflight: exact task worktree and merged PR verified
- Preserved: none
- Handoff: Task Janitor may remove the clean managed worktree and task branch
```
