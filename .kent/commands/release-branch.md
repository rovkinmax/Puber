---
description: Create a new release branch with version bump
---

# Release Branch

Creates a new release branch and bumps the version in build.gradle.kts.
Uses git worktree if there are uncommitted changes to avoid switching branches.

## Usage
```
/prompt:release-branch
```

## Steps to execute

### Step 1: Choose base branch
1. Ask user via ask_question: "Which branch to create the release from?"
   - Options: "master (Recommended)", "Other release branch"
2. Store the chosen base branch name (e.g., `master` or `release/1.2.0`)

### Step 2: Fetch and determine version
1. `git fetch origin <base_branch>`
2. Read `app/build.gradle.kts` from the base branch (use `git show origin/<base_branch>:app/build.gradle.kts`), find the version line:
   ```
   val currentVersion = "X.Y.Z"
   ```
3. Parse current version (e.g., `1.2.0`): extract `major.minor`, increment minor, reset patch → `1.3.0`

### Step 3: Check for uncommitted changes
1. Run `git status --porcelain`
2. If there ARE uncommitted changes (dirty working tree):
   - Use `git worktree add .kent/worktrees/release-<version> <base_branch>` to create a worktree from the updated local base branch
   - All subsequent git operations happen inside the worktree directory
3. If the working tree is clean:
   - `git switch <base_branch>`
   - `git rebase origin/<base_branch>`

### Step 4: Create release branch
- In worktree mode: `cd <worktree_path> && git switch -c release/<version>`
- In normal mode: `git switch -c release/<version>`
- Verify `release/<version>` does not track the base branch:
  ```bash
  git branch -vv --list release/<version>
  ```

### Step 5: Update version in build.gradle.kts
1. Replace version string with the new version in `app/build.gradle.kts` (use the worktree path if in worktree mode)

### Step 6: Commit
1. Stage `app/build.gradle.kts`
2. Commit with message: `Bump version to <version>`

### Step 7: Ask about push
1. Ask user via ask_question: "Push `release/<version>` to remote?"
2. If yes → `git push -u origin release/<version>`
3. If no → done, branch stays local

### Step 8: Cleanup
- If worktree was used:
  - `git worktree remove <worktree_path>`
  - Report: the release branch exists as a remote/local branch, original working directory is untouched
- If normal mode:
  - Switch back to the original branch the user was on before

## Example output

```
Uncommitted changes detected — using worktree
Base branch: master (fetched from origin)
Current version: 1.2.0
New version: 1.3.0
Created branch: release/1.3.0
Updated app/build.gradle.kts: 1.2.0 → 1.3.0
Committed: Bump version to 1.3.0
Pushed: release/1.3.0
Worktree cleaned up, back on feature/favorites
```
