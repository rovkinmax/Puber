---
description: Plan and execute a codebase migration step by step
effort: high
---

# Migration Start

Analyzes dependencies, creates an ordered migration plan,
and executes it file by file with build verification.

## Usage
```
/prompt:migration-start "migrate from Ktor 2 to Ktor 3"
/prompt:migration-start "migrate from Voyager to Compose Navigation"
/prompt:migration-start "migrate to Room for local caching"
```

## Parameters
- description: what to migrate and scope (free text)

## What it does

### Phase 1: Bootstrap
- Create workspace: `.todo/migration-<kebab-name>/`
- Write `meta.json`:
  ```json
  {
    "type": "migration",
    "description": "<user description>",
    "createdAt": "<date>"
  }
  ```

### Phase 2: Analysis

Use `kent run --agent=project-researcher --workspace "$PWD" "<bounded analysis prompt>"` to analyze migration scope:

1. **Identify all files in scope** (single `:app` module)
2. **Build dependency graph:**
   - Which files depend on what
   - Which packages import the migrating code
   - Order by dependency (leaf → root)
3. **Check compatibility:**
   - New library versions compatible?
   - API changes between old and new?
   - Breaking changes list
4. **Return:** ordered file list, dependency graph,
   breaking changes, estimated complexity

### Phase 3: Plan

Generate `.todo/migration-<name>/plan.md`:

```markdown
# Migration: <Description>

> Generated: <date>
> Scope: N files
> Dependency order: leaf-first

## Pre-migration checklist
- [ ] New dependencies added to version catalog
- [ ] Build compiles with both old and new present

## Steps (ordered by dependency)

### [ ] Step 1: <Leaf package/file>  `complexity: low`
- **Files:** ...
- **What:** Migrate X to Y
- **Breaking changes:** none (leaf)
- **Verify:** compile

### [ ] Step 2: <Dependent file>  `complexity: medium`
- **Files:** ...
- **What:** Update imports from old to new
- **Depends on:** Step 1
- **Verify:** compile

### [ ] Step N: <Cleanup>  `complexity: low`
- **Files:** build.gradle.kts, libs.versions.toml
- **What:** Remove old dependencies
- **Verify:** full project compile
```

**Ordering rules:**
- Leaf files first (no dependents)
- Work up the dependency tree
- Never break compilation between steps
- Cleanup (remove old deps) is always last

### Phase 3b: Self-review (MANDATORY before presenting)

Before showing the plan to the user, review it as if you were an agent with NO conversation context:

- [ ] Exact file paths specified for every step (not "the file" but `app/src/main/java/.../MyFile.kt`)
- [ ] All fields/methods to add or modify are named explicitly
- [ ] DI dependencies checked — does the constructor need new params?
- [ ] All side-effects listed — files to delete, imports to update
- [ ] No ambiguous language — "clean", "simple", "analogous" replaced with concrete descriptions
- [ ] Architecture rules respected — no VM references in composables, proper action dispatch pattern

### Phase 3c: Confirm plan with user

Before executing, present the plan to the user and wait for confirmation:
- Show the plan summary (steps, affected files, risk level)
- Use `ask_question` if there are open design decisions
- Do NOT start execution until the user confirms
- For large migrations (10+ files), always ask explicitly

### Phase 4: Execute

**Sequential execution** (order matters for migrations):

**JetBrains MCP helpers** (skip if in worktree — path contains `.kent/worktrees/`):
- **Renames**: after explicit user approval, use `.kent/adapters/mcp/mcp-call.sh jetbrains.rename_refactoring ... --allow-mutate` for class/method renames — IDE updates all references automatically
- **Quick verify**: `.kent/adapters/mcp/mcp-call.sh jetbrains.get_file_problems` on modified files before Gradle
- **Reformat**: after explicit user approval, `.kent/adapters/mcp/mcp-call.sh jetbrains.reformat_file path="<file>" --allow-mutate` on created/modified files
- If JetBrains MCP unavailable → use manual edit + Gradle

For each step:
1. Apply the migration change (prefer `rename_refactoring` for renames when MCP available)
2. Quick verify via `get_file_problems`, then compile:
   ```bash
   if pwd | grep -q '/.kent/worktrees/'; then
     ./tools/agentw :app:compileDevDebugKotlin
   else
     ./gradlew :app:compileDevDebugKotlin
   fi 2>&1 | grep -E "e: |error:|FAILURE|What went wrong" -A3
   ```
3. If fails → fix (migration-specific issues are expected)
4. Mark `[x]` in plan.md
5. Do not mirror step or lifecycle progress into `meta.json`

**After all steps:**
1. Full project compile:
   ```bash
   if pwd | grep -q '/.kent/worktrees/'; then
     ./tools/agentw assembleDevDebug
   else
     ./gradlew assembleDevDebug
   fi 2>&1 | grep -E "e: |error:|FAILURE|What went wrong" -A3
   ```
2. Run tests for affected code (if tests exist)
3. Report results

### Phase 5: Report

```
Migration complete: <description>
Files changed: M
Build: passing/failing
Tests: P passed, K failed (or "no tests yet")
Old dependencies removed: yes/no
```

## Migration-specific patterns

### Library replacement
- Add new dependency alongside old
- Migrate callers one by one
- Remove old dependency last
- Watch for transitive dependency conflicts

## Important
- **Order matters** — always follow dependency order
- Never break compilation between steps
- Compile-check after each step
- Final step always = full project compile
- Large migrations: suggest user review plan first
- Do NOT auto-commit — let user decide per step
