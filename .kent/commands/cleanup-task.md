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

Default behavior is report-only. Do not remove Kent-managed task worktrees or local branches unless the user explicitly
approves destructive cleanup and the project contract enables it.

Reason: deleting managed task worktrees can leave old Kent sessions bound to stale worktree metadata. Until Kent rebind
fully clears stale managed worktree bindings, cleanup must be conservative by default.

## What It Does

1. Determine the primary checkout:
   ```bash
   git worktree list --porcelain
   ```
2. Inspect worktrees under recognized agent roots only:
   - `.kent/worktrees/`
   - `~/.kent/worktrees/` (report-only; never remove from this Puber command)
3. For each candidate, report:
   - worktree path;
   - branch name;
   - whether the worktree is clean;
   - whether `HEAD` is recoverable from remote refs or a merged PR;
   - whether it is safe to remove manually.
4. If explicitly approved for destructive cleanup, remove only worktrees that are clean, under the project-local
   `.kent/worktrees/` root, and have recoverable commits. External/global worktree roots are always report-only.
5. Emit `cleanup_report` with removed, skipped, and blocked items.

## Output

Return a short human-readable `cleanup_report`:

```markdown
Cleanup report:
- Removed: none
- Safe to remove manually: .kent/worktrees/task-abc123 (clean, branch pushed)
- Skipped: .kent/worktrees/task-def456 (dirty worktree)
- Note: managed task worktree deletion is disabled by default in this project.
```
