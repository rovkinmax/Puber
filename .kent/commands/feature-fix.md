---
description: Fix issues found by feature-review (maker-checker cycle)
effort: high
---

# Feature Fix

Reads the last review report, applies fixes interactively,
then re-runs review. Up to 3 iterations (boomerang pattern).

## Usage
```
/prompt:feature-fix              # fix all issues from last review
/prompt:feature-fix <screen>     # fix issues for specific screen
/prompt:feature-fix .todo/<feature> <screen>
```

## Parameters
- screen-name: fix issues for a specific screen only (optional)
- feature target: feature name, `.todo/<feature>` path, or workflow-provided workspace path

## What it does

### Step 1: Load review report
- Load `.kent/skills/puber-android-workflow/references/rules/feature-target-resolution.md`.
- Resolve the feature target from arguments or Kent workflow task context.
- Read `.todo/<feature>/review-report.md`
- If no report exists → suggest running `/prompt:feature-review .todo/<feature>` first
- Parse issues: each issue has screen name, check type,
  description, file:line reference

### Step 2: Filter issues
- If screen-name given → only issues for that screen
- Skip already-resolved issues (marked `[x]` in report)
- Count remaining issues → report: "N issues to fix"
- If zero issues → "All clean! No issues to fix."

### Step 3: Fix loop (interactive)

For each unresolved issue:
1. Show the issue:
   ```
   Screen: VideoDetails
   Issue: Missing empty state for optional fields
   File: DetailsScreenContent.kt:45
   Suggestion: Add check for empty fields list
   ```
2. Ask user via ask_question:
   "Fix this issue? (yes / skip / stop)"
   - **yes** → apply the fix following project patterns
   - **skip** → mark as `[skipped]`, move to next
   - **stop** → end the fix loop, keep remaining as unfixed
3. After applying fix:
   - **Quick verify** (not in worktree): call `.kent/adapters/mcp/mcp-call.sh jetbrains.get_file_problems` on modified file. If clean — skip Gradle. If MCP unavailable — use Gradle
   - **Gradle fallback** (always use `./tools/agentw` in worktrees; use `./gradlew` in the main checkout):
     ```bash
     ./tools/agentw :app:compileDevDebugKotlin 2>&1 | grep -E "e: |error:|FAILURE|What went wrong" -A3
     ```
   - Mark issue as `[x]` in review-report.md
   - Brief note of what was changed

### Step 4: Return to review
- After all issues processed (or user stopped):
  - Report: "Fixed M of N issues. K skipped."
  - Save the updated report to `.todo/<feature>/review-report.md`.
  - In a Kent workflow task, do not invoke a nested review command. Finish the fix node so the workflow can transition
    back to its audit/review node.
  - In manual command use, report `/prompt:feature-review .todo/<feature>` as the next command without invoking it.

### Step 5: Iteration tracking
- Track iteration count in the `> Iteration:` header and history inside `review-report.md`, not in `meta.json`.
- **Maximum 3 iterations**. After 3rd review:
  - Report remaining issues as "Deferred"
  - Save final report
  - Suggest user review manually
- Each iteration:
  ```
  Iteration 1: review found 8 issues → fixed 6, skipped 2
  Iteration 2: review found 3 issues → fixed 3
  Iteration 3: review found 0 issues → clean!
  ```

## Review report format

The report in `.todo/<feature>/review-report.md`:

```markdown
# Review Report: <Feature Name>

> Iteration: 1
> Date: 2026-03-23
> Issues: 8 found, 6 fixed, 2 skipped

## VideoDetails

### [x] Layout: Missing padding bottom
- File: DetailsScreenContent.kt:45
- Fix: Added 16.dp bottom padding
- Fixed in iteration 1

### [skipped] Spacing: 12dp instead of 16dp
- File: DetailsScreenContent.kt:78
- Reason: Matches design intent

## Favorites

### [ ] States: No saving indicator
- File: FavoritesScreenContent.kt:120
- Suggestion: Add loading indicator when toggling favorite
```

## Important
- Do NOT auto-fix without showing the issue first
- Compile-check after each fix (not at the end)
- If a fix introduces new compile errors → revert and skip
- Preserve the user's skip decisions across iterations
- Each iteration reviews ALL code, not just fixed parts
- After 3 iterations, stop regardless of remaining issues
- In workflow tasks, let the workflow transition trigger the next review pass.
