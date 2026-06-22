---
description: Implement the next (or specific) step from the feature plan
effort: high
---

# Feature Implement

Implements a step from the feature plan. Loads relevant context, recipes, and writes code.

## Usage
```
/prompt:feature-implement            # implement next unchecked step
/prompt:feature-implement 3          # implement step 3 specifically
```

## Parameters
- step-number: specific step to implement (optional — defaults to next unchecked `[ ]` step)

## What it does

### Step 1: Load plan
- Read `.todo/.current` (plain text) → get feature name, then read `.todo/<name>/meta.json` for `currentStep`
- Read `.todo/<feature>/plan.md`
- Find the target step:
  - If step number given → use that step
  - If not → find the first `[ ]` (unchecked) step
- If all steps are `[x]` → report "All steps completed!"

### Step 1b: Check for parallel group
- If the target step has a `parallel-group: <letter>` tag:
  1. Find ALL unchecked `[ ]` steps with the same `parallel-group` letter
  2. Verify all their dependencies are satisfied (all `Depends on:` steps are `[x]`)
  3. If 2+ steps are ready → enter **parallel execution mode** (see "Parallel Execution" section below)
  4. If only 1 step is ready (others blocked by dependencies) → proceed with normal sequential execution
- If no parallel-group tag → proceed with normal sequential execution

### Step 2: Load recipes for this step
Based on the step's **type** tag, load the matching recipe from `.kent/skills/puber-android-workflow/references/recipes/`:

| Step type | Recipe file |
|-----------|-------------|
| `screen` | `compose-screen.md` + `ui-components.md` + `compose-previews.md` + `compose-performance.md` |
| `screen` (with list/pagination) | `compose-screen.md` + `ui-components.md` + `paging-list.md` + `compose-previews.md` + `compose-performance.md` |
| `screen` (with filters) | `compose-screen.md` + `ui-components.md` + `filtering.md` + `compose-previews.md` + `compose-performance.md` |
| `screen` (with search) | `compose-screen.md` + `ui-components.md` + `viewmodel.md` + `paging-list.md` + `compose-previews.md` + `compose-performance.md` |
| `viewmodel` | `viewmodel.md` + `error-handling.md` |
| `viewmodel` (with pagination) | `viewmodel.md` + `paging-list.md` + `error-handling.md` |
| `data` / `api` | `api-endpoint.md` |
| `navigation` | `navigation.md` |
| `di` | `di-setup.md` |
| `test` | `unit-testing.md` |

If step has no explicit type, infer from the step title/description:
- "Screen", "UI" → screen + ui-components
- "List", "Paginated", "Paging" → screen + ui-components + paging-list
- "Filter", "Picker" → filtering (add to current set)
- "Search" → screen (with search)
- "ViewModel", "VM" → viewmodel
- "Repository", "Interactor", "API" → api-endpoint
- "Navigation", "Flow", "Router" → navigation
- "DI", "Module" → di-setup

Load ONLY the relevant recipes — not all of them.

**Advanced recipes** — load additionally when the step description mentions specific patterns:

| Trigger in step description | Advanced recipe |
|-----------------------------|----------------|
| "search", "SearchComponent", "SearchBar" | `compose-screen-advanced.md` + `viewmodel-advanced.md` |
| "collapsing", "CollapsingTitle", "nestedScroll" | `compose-screen-advanced.md` |
| "tabs", "segmented", "AnimatedContent" | `compose-screen-advanced.md` |
| "accordion", "collapsible section" | `compose-screen-advanced.md` |
| "form", "draft", "create/edit", "auto-save" | `viewmodel-advanced.md` |
| "picker", "result handling" | `viewmodel-advanced.md` |
| "label-value", "details fields" | `viewmodel-advanced.md` |
| "grouped list", "sections by date" | `viewmodel-advanced.md` |
| "caching", "cache", "TypedTtlCache" | `api-endpoint-advanced.md` |
| "flow listener", "real-time", "eventBus" | `api-endpoint-advanced.md` |

If no triggers match → skip advanced recipes (core is sufficient for most steps).

### Step 3: Load feature context for this step
- **Inspect existing APIs**: if the step extends or modifies an existing class (e.g., adds methods to an interactor), use `.kent/adapters/mcp/mcp-call.sh jetbrains.get_symbol_info` to get its current method signatures — faster than reading the full file
- **Skip design/screenshots for non-UI steps**: if step type is `api`, `data`, or `di` → skip Design ref and Screenshots loading entirely (no visual reference needed for data layer)
- For `screen`/`viewmodel` steps: Read the step's **Design ref** → load screen layout from `design/screens/` or `layouts.md`
- **Load screenshots** (only for `screen` type) → read PNG files from `screenshots/` using Read tool (check layouts.md screenshots index for filenames). This gives visual reference while coding UI.
- For `navigation` steps: Read `.todo/<feature>/navigation-flow.md` if it exists — use transition types (navigateTo, showOver) and triggers from the design flow map
- Read the step's **Spec ref** → load relevant section from `spec.md`
- Read the step's **Files** → load existing files that will be modified

### Step 4: Implement
- Write code following:
  1. The plan step description (WHAT to do)
  2. The loaded recipe (HOW to do it — patterns, structure, checklist)
  3. The design file (WHAT it should look like)
  4. The spec (business logic and behavior)
- Run through the recipe's checklist before moving on

### Step 5: Verify

**Quick check via JetBrains MCP** (skip if in worktree — path contains `.kent/worktrees/`):
1. For each created/modified file, call `.kent/adapters/mcp/mcp-call.sh jetbrains.get_file_problems` with `errorsOnly: true`
2. If errors found → fix them immediately
3. If no errors → skip Gradle build (IDE index is sufficient for most cases)
4. If JetBrains MCP is unavailable (connection refused) → fall through to Gradle

**Gradle fallback** (always use `./tools/agentw` in worktrees; use `./gradlew` in the main checkout):
```bash
./tools/agentw :app:compileDevDebugKotlin 2>&1 | grep -E "e: |error:|FAILURE|What went wrong" -A3
```
- Fix any compilation errors

If running in the main checkout and MCP was unavailable, direct `./gradlew` is acceptable.

**After fixing errors**, call `.kent/adapters/mcp/mcp-call.sh jetbrains.reformat_file` on each created/modified file (skip in worktree).

### Step 5b: Add/update previews (for `screen` type steps)
- If step type is `screen`:
  1. Check if a `PreviewParameterProvider` exists for this screen's ViewState
  2. If not — create one in `preview/` subdirectory following `compose-previews.md` recipe
  3. Add `@Preview` with `@PreviewParameter` to the component file
  4. Cover all ViewState variants (Loading, Content, Error, Empty — whatever the sealed class has)

### Step 5c: Quality gate (post-implementation check)

Run through the quality checklist for the step type. These are **verification checks** — review the code you just wrote:

**For `screen` steps:**
- [ ] All states relevant to this screen are covered (not all screens need Loading/Error/Empty — use the spec and design to determine which states apply)
- [ ] Preview added for each state variant
- [ ] All user-visible strings in `strings.xml` (no hardcoded text)
- [ ] `@Immutable` on ViewState sealed class and data class variants
- [ ] `key = { it.id }` in LazyColumn/LazyRow `items()` calls
- [ ] `remember(vm) { vm::onAction }` for action lambda
- [ ] Touch targets adequate for interactive elements (TV remote focus areas)
- [ ] **No duplicate screens** — check if a similar screen already exists before creating a new one
- [ ] **UIKit components reused** — VideoItem, VideoGrid, VideoItemGridDetails, FullScreenProgressIndicator
- [ ] **ViewState doesn't duplicate API model** — Content references model fields via mapper, does not re-declare them as separate properties

**For `viewmodel` steps:**
- [ ] All Actions handled in `when` block (exhaustive)
- [ ] `errorHandler` used via `launch {}` (not manual try-catch unless needed for inline errors)
- [ ] `dispatchError()` handles Loading → Error state, Content → toast
- [ ] No blocking calls on Main thread
- [ ] **No `@InjectConstructor`** — Koin uses pure DSL, constructor params are resolved automatically
- [ ] **Network actions show progress** — any user-triggered network action shows loading before and success message after

**For `api` steps:**
- [ ] Interactor has interface (for global singletons) or plain class (for scoped-only)
- [ ] No separate domain entity layer — uses API models (`data.api.models.*`) directly
- [ ] DI binding added in appropriate Koin module (`interactorModule` for global, `buildModule` for scoped)
- [ ] `KinoPubApiClient` method uses `apiCall { }` returning `Result<T>`

**Package structure (all steps):**
- [ ] Action/ViewState in `ui/feature/<name>/model/` (NOT in `ui/feature/<name>/vm/`)
- [ ] Screen in `ui/feature/<name>/component/`
- [ ] VM in `ui/feature/<name>/vm/`

If any check fails — fix it before moving on.

### Step 5d: Visual comparison with Figma (for `screen` type steps)

After previews are ready, compare implementation with the Figma design screenshots:

1. **Load both images**: Read the Figma screenshot from `.todo/<feature>/screenshots/`
2. **Compare visually** — check for:
   - Layout structure (section order, spacing)
   - Typography (sizes, weights, colors)
   - Button styles
   - Card shapes and padding
3. **Fix any discrepancies** before proceeding

### Step 6: Update progress
- Update `plan.md`: change `[ ] Step N` → `[x] Step N`
- Update `meta.json`:
  - Set `currentStep` to the completed step number
  - Set `lastUpdated` to current date
  - Append to `stepHistory` array: `{ "step": N, "completedAt": "<date>" }`
- Report what was implemented and which files were created/modified
- **Open key files** (skip if in worktree): call `.kent/adapters/mcp/mcp-call.sh jetbrains.open_file_in_editor` for each newly created file so user can immediately see them in the IDE

### Step 6b: Smoke-test prompt (for `screen` + `navigation` steps only)
- If the implemented screen is now reachable (navigation wired):
  1. First check if a device/emulator is connected: call `.kent/adapters/mcp/mcp-call.sh mobile.list_devices`
  2. If no devices found → skip smoke-test silently (don't ask the user)
  3. If device available → ask user: "Screen is reachable. Run smoke-test on device? (y/n)"
  4. If yes → **use Kent prompt command to invoke `/prompt:smoke-test`** with the screen name
  5. Save results to `.todo/<feature>/smoke-results/step-<N>.md`
- If navigation is not wired yet → skip (mention it will be testable after nav step)

### Step 6c: Update shared knowledge (if applicable)
- If a non-obvious pattern, API quirk, or UI decision was discovered during this step:
  - Append to `.todo/_shared/patterns-learned.md` if it's a reusable coding pattern
  - Append to `.todo/_shared/api-quirks.md` if it's an API behavior gotcha
  - Append to `.todo/_shared/ui-decisions.md` if it's a component/layout choice
- Format: `### <Title>` + `**Problem:**` + `**Solution:**` + `**Files:**` + `**Date:**`
- Skip if nothing new was learned

## Parallel Execution

When Step 1b detects a parallel group with 2+ ready steps, delegate to the **feature-parallel-orchestrator** agent.

### How to delegate:

Launch a single Task with `agent: "feature-parallel-orchestrator"` and provide:

```
Feature: <name>
Workspace: .todo/<feature>/
Parallel group: <letter>
Steps to execute: <N, M, K>
Compile command:
- Main checkout: ./gradlew :app:compileDevDebugKotlin 2>&1 | grep -E "e: |error:|FAILURE|What went wrong" -A3
- Kent worktree: ./tools/agentw :app:compileDevDebugKotlin 2>&1 | grep -E "e: |error:|FAILURE|What went wrong" -A3

## Plan
<paste full plan.md content>
```

The orchestrator will:
1. Read all context (recipes, design, spec, existing files) for each step
2. Compose self-contained prompts for `feature-step-worker` agents
3. Launch workers in parallel
4. Compile after all workers finish
5. Fix errors, run quality gates
6. Update plan.md and meta.json progress
7. Report results

**max_turns**: 80 (orchestrator needs room to prepare prompts + compile + fix)

After the orchestrator completes, read its report and proceed to the next step or group.

## Important
- Load ONLY the context and recipes needed for the current step — not everything
- Follow the plan's dependency order — if step depends on a previous unchecked step, warn the user
- Do NOT skip ahead or combine steps unless user explicitly asks
- After implementation, suggest the next step
- Do NOT commit automatically — let user decide when to commit
- **Ambiguous UI patterns — always ask**: if the design shows a search field, list, or interactive element but it's unclear which specific component or interaction pattern to use, ask the user via ask_question before implementing. Do NOT guess — wrong pattern choice leads to significant rework
- **Parallel execution**: when a parallel group is detected, ALWAYS use parallel mode — do NOT fall back to sequential without a good reason (e.g., all steps share a file). Report "Running N steps in parallel (group <letter>)" before launching workers
