---
description: Plan and execute a refactoring with regression checks
effort: high
---

# Refactor Start

Analyzes code, creates a step-by-step refactoring plan,
and executes it with compilation checks after each step.

## Usage
```
/prompt:refactor-start <description>
/prompt:refactor-start "extract VideoItemUIMapper to shared utility"
/prompt:refactor-start "split FavoritesVM into list + details VMs"
```

## Parameters
- description: what to refactor and why (free text)

## What it does

### Phase 1: Bootstrap
- Create workspace: `.todo/refactor-<kebab-name>/`
- Write `meta.json`:
  ```json
  {
    "type": "refactor",
    "description": "<user description>",
    "createdAt": "<date>"
  }
  ```

### Phase 2: Analysis

Use `kent run --agent=project-researcher --workspace "$PWD" "<bounded analysis prompt>"` to analyze the code:
- Identify all files affected by the refactoring
- Map dependencies: who uses the code being refactored
- Find all call sites, imports, DI bindings (Koin modules)
- Check for tests that cover the affected code
- Return: affected files, dependency graph, test coverage

- **Check shared knowledge**: Read `.todo/_shared/patterns-learned.md` and `.todo/_shared/api-quirks.md` for context relevant to this refactoring — known patterns may inform the plan.

**Prompt the agent with specific paths and class names**
from the user's description — don't let it explore broadly.

### Phase 3: Plan

Generate `.todo/refactor-<name>/plan.md`:

```markdown
# Refactor: <Description>

> Generated: <date>
> Affected files: N
> Risk: low/medium/high

## Motivation
<Why this refactoring, from user description>

## Steps

### [ ] Step 1: <Preparation>  `complexity: low`
- **Files:** list of files
- **What:** Create new file / interface / etc.
- **Regression check:** compile

### [ ] Step 2: <Core change>  `complexity: medium`
- **Files:** list of files
- **What:** Move / rename / extract
- **Regression check:** compile + verify callers

### [ ] Step 3: <Update callers>  `complexity: low`
- **Files:** list of caller files
- **What:** Update imports, references
- **Regression check:** compile

### [ ] Step 4: <Cleanup>  `complexity: low`
- **Files:** old files to remove
- **What:** Remove old code, unused imports
- **Regression check:** compile
```

**Step rules:**
- Each step = one atomic change that compiles
- Never break compilation between steps
- Update all callers in the same step as the API change
  (or immediately after)
- Final step: run existing tests to verify no regressions

### Phase 3b: Self-review (MANDATORY before presenting)

Before showing the plan to the user, review it as if you were an agent with NO conversation context:

- [ ] Exact file paths specified for every step (not "the VM" but `app/src/main/java/com/kino/puber/.../vm/MyVM.kt`)
- [ ] All fields/methods to add or modify are named explicitly
- [ ] DI dependencies checked — does the constructor need new params?
- [ ] All side-effects listed — files to delete, imports to update, Koin modules to modify
- [ ] No ambiguous language — "clean", "simple", "analogous", "etc." replaced with concrete descriptions
- [ ] Architecture rules respected — no VM references in composables, proper action dispatch pattern

### Phase 3c: Confirm plan with user

Before executing, present the plan to the user and wait for confirmation:
- Show the plan summary (steps, affected files count, risk level)
- Use `ask_question` if there are open design decisions
- Do NOT start execution until the user confirms
- For trivial refactors (1-2 files, low risk), a brief summary with "Proceeding..." is enough
- For non-trivial refactors (3+ files), always ask explicitly

### Phase 4: Execute

**Code comment rule:** When replacing a pattern with a non-obvious alternative (e.g., `Set` instead of previous tracking approach), add a short comment (1-2 lines) explaining WHY the new approach is used. Do not add comments for self-evident changes.

**JetBrains MCP helpers** (skip if in worktree — path contains `.kent/worktrees/`):
- **Renames**: after explicit user approval, use `.kent/adapters/mcp/mcp-call.sh jetbrains.rename_refactoring ... --allow-mutate` instead of manual find-and-replace — IDE updates all references (imports, type hints, configs) in one call
- **Quick verify**: after each change, call `.kent/adapters/mcp/mcp-call.sh jetbrains.get_file_problems` on modified files before running Gradle
- **Reformat**: after explicit user approval, call `.kent/adapters/mcp/mcp-call.sh jetbrains.reformat_file path="<file>" --allow-mutate` on created/modified files
- If JetBrains MCP unavailable → use manual edit + Gradle as before

For each step:
1. Apply the change (prefer `rename_refactoring` for renames when MCP available)
2. Quick verify via `get_file_problems` (or Gradle if in worktree — path contains `.kent/worktrees/`):
   ```bash
   if pwd | grep -q '/.kent/worktrees/'; then
     ./tools/agentw :app:compileDevDebugKotlin
   else
     ./gradlew :app:compileDevDebugKotlin
   fi 2>&1 | grep -E "e: |error:|FAILURE|What went wrong" -A3
   ```
3. If compilation fails → fix immediately
4. Mark step `[x]` in plan.md
5. Do not mirror step or lifecycle progress into `meta.json`

### Phase 5: Verify

After all steps:
1. Run full compile:
   ```bash
   if pwd | grep -q '/.kent/worktrees/'; then
     ./tools/agentw :app:compileDevDebugKotlin
   else
     ./gradlew :app:compileDevDebugKotlin
   fi 2>&1 | grep -E "e: |error:|FAILURE|What went wrong" -A3
   ```
2. Run tests if they exist:
   ```bash
   if pwd | grep -q '/.kent/worktrees/'; then
     ./tools/agentw :app:testDevDebugUnitTest
   else
     ./gradlew :app:testDevDebugUnitTest
   fi 2>&1 | grep -E "FAILED|PASSED|tests"
   ```
3. Report results:
   ```
   Refactor complete: <description>
   Files changed: N
   Tests: M passed, K failed (or "no tests yet")
   ```

## Important
- Never break compilation between steps
- If a step is too complex, split it before executing
- Verify all callers updated (grep for old class/method names)
- Do NOT auto-commit — let user decide
- If tests fail, report which and suggest fixes
- For large refactors (10+ files), suggest user review
  the plan before execution
