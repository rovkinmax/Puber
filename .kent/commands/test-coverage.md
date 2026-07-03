---
description: Analyze and improve test coverage for a package or feature
---

# Test Coverage

Analyzes test coverage gaps, creates a plan to add tests,
and implements them step by step.

## Usage
```
/prompt:test-coverage ui/feature/favorites
/prompt:test-coverage domain/interactor
/prompt:test-coverage data/api
```

## Parameters
- path: package path within `app/src/main/java/com/kino/puber/` to analyze (required)

## What it does

### Phase 1: Bootstrap
- Create workspace: `.todo/test-coverage-<package-name>/`
- Write `meta.json`:
  ```json
  {
    "type": "test-coverage",
    "module": "<path>",
    "status": "planning",
    "createdAt": "<date>"
  }
  ```

### Phase 2: Analysis

Use `kent run --agent=project-researcher --workspace "$PWD" "<bounded testability analysis prompt>"` to analyze the
target path:

1. **Find all source files:**
   - VMs, Interactors, Mappers, Validators
   - Skip: Screen.kt files (DI wrappers with buildModule)
2. **Find existing tests:**
   - Map source file → test file
   - Count tested vs untested classes
3. **Prioritize by risk:**
   - VMs with complex state logic → high priority
   - Interactors with business logic → high priority
   - UI Mappers → medium priority
   - Simple data classes → low priority (skip)
4. **For screen components**, also check:
   - [ ] `PreviewParameterProvider` exists for ViewState
   - [ ] All ViewState variants covered (Loading, Content, Error, Empty — whichever the sealed class defines)
   - If missing — add to improvement plan as a step
5. **Return:** prioritized list with source paths,
   existing test paths, test gap description

### Phase 3: Plan

Generate `.todo/test-coverage-<name>/plan.md`:

```markdown
# Test Coverage: <Package>

> Generated: <date>
> Source files: N
> Already tested: M (K%)
> To add: P test files

## Coverage Map

| Source File | Test File | Status |
|-------------|-----------|--------|
| MyVM.kt | MyVMTest.kt | exists |
| MyInteractor.kt | — | MISSING |
| MyUIMapper.kt | — | MISSING |

## Implementation Steps

### [ ] Step 1: MyInteractor tests  `priority: high`
- **Source:** .../domain/interactor/MyInteractor.kt
- **Create:** .../domain/interactor/MyInteractorTest.kt
- **Cases:**
  - getItems: happy path, empty, error
  - Pagination: page calculation

### [ ] Step 2: MyVM tests  `priority: high`
- **Source:** .../ui/feature/<name>/vm/MyVM.kt
- **Create:** .../ui/feature/<name>/vm/MyVMTest.kt
- **Cases:**
  - init: loads data → Content
  - init: error → Error state
  - RetryClicked: reloads
  - ItemSelected: navigates
  - Refresh: keeps content, reloads

### [ ] Step 3: MyUIMapper tests  `priority: medium`
- **Source:** .../ui/feature/<name>/model/MyUIMapper.kt
- **Create:** .../ui/feature/<name>/model/MyUIMapperTest.kt
- **Cases:**
  - mapToContent: all fields mapped
  - mapToContent: null fields handled
```

### Phase 4: Execute

For each step:
1. Load the `unit-testing.md` recipe
2. Read the source file being tested
3. Write the test file following project patterns
4. **Quick verify** (skip if in worktree — path contains `.kent/worktrees/`):
   - Call `.kent/adapters/mcp/mcp-call.sh jetbrains.get_file_problems` on the created test file
   - If errors → fix them before running tests
   - If MCP unavailable → skip, proceed to Gradle
5. **Reformat** (skip if in worktree): after explicit user approval,
   `.kent/adapters/mcp/mcp-call.sh jetbrains.reformat_file path="<test-file>" --allow-mutate` on the created test file
6. Run the tests:
   ```bash
   if pwd | grep -q '/.kent/worktrees/'; then
     ./tools/agentw :app:testDevDebugUnitTest --tests "<TestClassName>"
   else
     ./gradlew :app:testDevDebugUnitTest --tests "<TestClassName>"
   fi 2>&1 | grep -E "PASSED|FAILED|tests"
   ```
7. Fix any failing tests
8. Mark step `[x]` in plan.md
9. Update `meta.json`

### Phase 5: Report

```
Test coverage improvement: <package>
Before: M tested / N source files (K%)
After: M+P tested / N source files (K2%)
New test files: P
All tests passing: yes/no
```

## Test quality rules

### Regression tests
- A regression test MUST fail on the old (buggy) code. If your test data would pass both the old and new implementation, the test is useless — redesign the test data to specifically trigger the bug.

### Locale-independent assertions
- NEVER assert on formatted dates, currencies, or numbers directly (e.g., `assertEquals("21 Nov 2024", ...)`). These depend on `Locale.getDefault()` and break on non-EN CI.
- Instead, assert on structural properties: count, uniqueness, ordering, presence of items, field values.

### Explicit expected values
- Prefer explicit expected values over count-only checks. `assertEquals(listOf("movie", "serial"), result.map { it.type })` is far more useful than `assertEquals(2, items.size)` — when the test fails, the developer immediately sees what went wrong.

### Mapper test edge cases (mandatory checklist)
- [ ] Empty input → returns empty / handles gracefully
- [ ] Single item → no off-by-one errors
- [ ] Null/missing fields → defaults applied correctly
- [ ] Invalid format input → no crash, fallback used
- [ ] Verify item IDs/content, not just count
- [ ] Structure/order verification (for section-based lists: use non-contiguous grouping keys to catch bugs)

## Important
- Follow `unit-testing.md` recipe for patterns
- Use real project patterns (MockK, runTest)
- Test happy path + error path + edge cases
- Don't test trivial code (data classes, constants)
- Don't test Screen.kt files (DI wrappers with buildModule)
- Run tests after each file creation — fix before moving on
- Do NOT auto-commit — let user decide
