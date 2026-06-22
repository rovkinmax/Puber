---
description: Review implementation against cached design and spec
effort: high
---

# Feature Review

Compares the current implementation with the cached design screens and spec. Reports discrepancies.

## Usage
```
/prompt:feature-review                  # review all implemented steps
/prompt:feature-review <screen-name>    # review specific screen only
```

## Parameters
- screen-name: review only the specified screen (optional)

## What it does

### Step 1: Load references
- Read `.todo/.current` (plain text) → get feature name
- Read design files: try `design/screens/*.md` first, then `design.md` + `layouts.md`
- Read `.todo/<feature>/navigation-flow.md` if it exists — verify navigation transitions match the design flow map
- **Load screenshots** from `screenshots/` using Read tool — visual reference for comparison
- Read `spec.md`
- Read `plan.md` to know which steps are done `[x]`

### Step 2: Load implementation
- For each completed step, read the actual source files referenced in the plan
- If screen-name given, only load files related to that screen

**IDE static analysis** (skip if in worktree — path contains `.kent/worktrees/`):
- Call `.kent/adapters/mcp/mcp-call.sh jetbrains.get_file_problems` on each loaded source file (with `errorsOnly: false`)
- Collect results — include errors/warnings in the review report as a separate "IDE issues" section
- If MCP unavailable → skip, note "IDE analysis skipped" in report

### Step 2b: Parallel review agents (optional, for large features)
If the feature has both screen and data-layer steps completed (3+ files to review), launch review agents in parallel:
- **compose-reviewer** (Task, `agent: "compose-reviewer"`) — review all screen/component files
- **domain-model-reviewer** (Task, `agent: "domain-model-reviewer"`) — audit interactor/mapper quality

These agents are independent — launch them simultaneously. Merge their findings into the final report.

For small features (1-2 screens), review inline without subagents — the overhead isn't worth it.

### Step 3: Compare

Check each screen against its design file:

| Check | What to verify |
|-------|---------------|
| Layout | Does the Compose layout match the structure from design? |
| Components | Are the right TV Material3 / UIKit components used? |
| States | Are all states implemented (loading, content, empty, error)? |
| Spacing | Do paddings/margins match the design? |
| Actions | Are all user actions from spec handled? |
| Navigation | Does navigation match spec flows? |
| Edge cases | Are edge cases from spec covered? |
| API | Are correct KinoPubApiClient methods used per spec? |

**Code quality checks:**
- [ ] No data mapping in composables (all mapping in VM/Mapper via ResourceProvider)
- [ ] API model fields are not duplicated in ViewState (Content maps via UIMapper)
- [ ] Koin DI properly scoped (global singletons in `interactorModule`, screen-scoped in `buildModule`)
- [ ] PuberVM pattern followed (onStart, onAction, updateViewState)
- [ ] PuberScreen pattern followed (@Parcelize, buildModule, DIScope, koinViewModel)

### Step 4: Report

Output a structured report:

```
## Review: <Feature Name>

### <Screen Name> ✅ / ⚠️
- [x] Layout matches design
- [x] All states implemented
- [ ] Missing empty state illustration
- [ ] Padding 12dp instead of 16dp (design says 16dp)

### <Another Screen> ✅ / ⚠️
...

### IDE Issues (auto-detected)
- `MyScreen.kt`: warning: unused import `...`
- `MyVM.kt`: error: unresolved reference `...`

### Summary
- N screens reviewed
- M issues found
- K IDE issues (errors/warnings from static analysis)
```

## Important
- Compare against CACHED design files, not Figma directly
- Focus on structural/behavioral issues, not pixel-perfect matching
- Reference specific lines in design files and source code
- Suggest fixes for each issue found
- Do NOT auto-fix — present findings and let user decide
