---
description: Generate implementation plan from cached design and spec data
effort: high
---

# Feature Plan

Generates a step-by-step implementation plan based on design and spec data collected by `/prompt:feature-design` and `/prompt:feature-spec`.

## Usage
```
/prompt:feature-plan
```

## What it does

### Step 1: Load context
- Read `.todo/.current` (plain text) → get feature name, then read `.todo/<name>/meta.json` for `screens`, `figmaUrl`
- Read `.todo/<feature>/design.md` (screen map) + `.todo/<feature>/layouts.md` (detailed layouts, tokens, components)
- Read `.todo/<feature>/navigation-flow.md` if it exists — use transition types and triggers for the Navigation section of the plan (navigateTo vs showOver)
- Read `.todo/<feature>/spec.md`
- If design or spec is missing, warn and suggest running the appropriate command first:
  - No design? → suggest running `/prompt:feature-design <url>` first
  - No spec? → suggest running `/prompt:feature-spec` first

### Step 1b: Load shared knowledge
- Read `.todo/_shared/patterns-learned.md` — accumulated patterns from past features
- Read `.todo/_shared/api-quirks.md` — known API issues and workarounds
- Read `.todo/_shared/ui-decisions.md` — component mapping decisions
- If any of these files don't exist yet — skip (they'll be populated as features complete)
- Use this knowledge to inform the plan (avoid known pitfalls, reuse proven patterns)

### Step 2: Analyze existing codebase
**Recommended**: Use the **android-codebase-analyst** agent (Kent subagent, `agent: "android-codebase-analyst"`) for codebase analysis — it can explore multiple files efficiently without polluting the main context. For simple file searches (checking if a file exists, finding imports), use a **Haiku subagent** (`model: "haiku"`) instead — faster and cheaper.

**JetBrains MCP shortcuts** (use directly, without subagent, for quick lookups):
- `.kent/adapters/mcp/mcp-call.sh jetbrains.get_symbol_info` — inspect an existing class/method signature (e.g., check current interactor interface before planning new methods)
- `.kent/adapters/mcp/mcp-call.sh jetbrains.search_in_files_by_text` — quick text search across the project (faster than Grep for known string patterns)

**Pass specific paths to the subagent** (from AGENTS.md) to avoid open-ended exploration:
- Package path (e.g., `app/src/main/java/com/kino/puber/ui/feature/<name>/`) — for existing code
- AGENTS.md path — for project conventions
- List of screens from spec.md (so it knows what to look for)
- List of API endpoints from spec.md (to check existing coverage in `KinoPubApiClient`)
- Specific file names to check (e.g., existing VM/Screen files mentioned in spec stubs)

The subagent should:
- Read AGENTS.md for project patterns and conventions
- Check the relevant feature package for existing code (VMs, screens, models, interactors) — use specific file paths, not broad glob searches
- Check API client: do needed endpoints already exist in `KinoPubApiClient`?
- Identify reusable components — give specific component names to search for (from design layouts.md)
- Check navigation patterns (how screens are registered, router usage)
- **Find all files constructing modified models**: for each API model being modified, search for files that construct it (previews, mappers, UI mappers, ViewState factories). These MUST be included in the plan's Files list for the relevant step
- Return a structured summary: existing files, missing pieces, API coverage, reusable components

**Optimization tips for the subagent prompt** (reduces tool calls from ~60 to ~30):
- List specific model/screen names to search for — don't let it discover them
- Give exact file paths for key files instead of "search for stubs"
- For API coverage, search in `KinoPubApiClient.kt`
- For reusable components, give the exact names from layouts.md (e.g., "VideoItem", "VideoGrid", "VideoItemGridDetails") — one grep per name
- Set `max_turns` on the subagent to limit runaway exploration (30 is usually enough)

### Step 3: Generate plan

Create a detailed implementation plan with:

1. **Dependency order** — what must be built first
2. **Files to create/modify** — exact paths
3. **Per-step details:**
   - What to implement
   - Which design screen it relates to (+ screenshot filenames for UI steps)
   - API endpoints to integrate
   - Patterns to follow (from AGENTS.md)
   - What already exists vs what's new

**Step granularity rules:**
- One step = one logical unit that can be built and verified independently
- **Combine** VM + Screen into one step only if the screen is trivial (< 100 lines, no complex state)
- **Split** VM and Screen into separate steps if the screen has complex layout, multiple states, or reusable components
- Data layer (interactor + API client methods) is usually one step unless multiple independent endpoints
- Navigation + DI are usually the final step(s)
- Each step should touch at most 3-5 files — if more, consider splitting
- **Incremental nav/DI**: if navigation and DI registrations are added incrementally during each screen step, do NOT create a separate final Navigation/DI step — mark it as `type: verification` or merge with the last implementation step
- **Wiring steps must list ALL affected files**: when a step wires a new feature into an existing VM, list ALL files that need changes — not just the VM, but also its model (ViewState), interactor, UI mapper, etc.
- **Field changes must be explicit**: if the spec says "skip field X" but the field exists in the current model — explicitly state "Remove field X" in the plan step

**Parallel execution groups:**

Steps that are independent (no mutual dependencies, no shared files) can be executed in parallel by the `/prompt:feature-implement` command. Mark them with `parallel-group: <letter>` in the step header.

**Rules for parallel grouping:**
- Steps in the same group MUST NOT depend on each other
- Steps in the same group MUST NOT modify the same files
- Steps in the same group SHOULD have `complexity: low` or `complexity: medium` — do NOT parallelize `complexity: high` steps (they need full context attention)
- Typical parallel candidates:
  - Multiple independent screen steps (different screens, different VMs)
  - Multiple independent data layer steps (different endpoints)
  - Multiple independent VM steps (different features, no shared state)
  - String resources + previews + DI bindings (low-complexity support tasks)
- Steps that modify shared files (e.g., a navigation registry, a shared DI module) are NOT parallel candidates
- Maximum group size: 4 steps (more than 4 parallel agents cause diminishing returns and increase conflict risk)
- **Cross-group ordering**: groups are executed sequentially (all of group A before group B), steps within a group are parallel

**Format in plan.md:**
```markdown
### [ ] Step 3: Details Screen  `type: screen`  `complexity: medium`  `parallel-group: A`
...
### [ ] Step 4: Player Screen  `type: screen`  `complexity: medium`  `parallel-group: A`
...
### [ ] Step 5: Wiring + Navigation  `type: navigation`  `complexity: low`
...
```

In this example, Steps 3 and 4 run in parallel (group A), then Step 5 runs after both complete.

### Step 4: Save plan

Save to `.todo/<feature>/plan.md`:

```markdown
# <Feature Name> — Implementation Plan

> Generated: <YYYY-MM-DD>
> Screens: <N screens from design.md>
> Spec: <source from spec.md>

## Summary

<Brief overview: what we're building, how many screens, main complexity>

## Prerequisites

Check each and mark as met or add as a blocker:
- [ ] API endpoints: do needed endpoints exist in KinoPubApiClient? If not → add step to add them
- [ ] Base components: list which are needed and confirm they exist
- [ ] String resources: any new strings needed? (usually yes for new screens)

## Implementation Steps

### [ ] Step 1: <Data Layer — Interactor & API>  `type: api`  `complexity: low`
- **Files:**
  - `app/src/main/java/com/kino/puber/data/api/KinoPubApiClient.kt` (modify)
  - `app/src/main/java/com/kino/puber/domain/interactor/<name>/<Name>Interactor.kt` (create)
- **Design ref:** —
- **Spec ref:** API Endpoints section
- **What:** Add API client methods and interactor for the feature
- **Depends on:** —

### [ ] Step 2: <ViewModel>  `type: viewmodel`  `complexity: medium`  `parallel-group: A`
- **Files:** `app/src/main/java/com/kino/puber/ui/feature/<name>/vm/<Name>VM.kt` (create)
- **Design ref:** `layouts.md` → <Name> section
- **Spec ref:** Screen Behavior → <Name>
- **What:** VM with states (Loading, Content, Error), actions
- **Depends on:** Step 1

### [ ] Step 3: <UI Screen>  `type: screen`  `complexity: medium`
- **Files:**
  - `app/src/main/java/com/kino/puber/ui/feature/<name>/component/<Name>Screen.kt` (create)
  - `app/src/main/java/com/kino/puber/ui/feature/<name>/component/<Name>ScreenContent.kt` (create)
- **Design ref:** `layouts.md` → <Name> section
- **Screenshots:** `screenshots/<name>-content.png`
- **What:** Compose screen matching the design layout
- **Depends on:** Step 2

### ...

## API Model Usage Map

(Include when feature modifies existing API models or introduces new ones)

| Model | Used in | Must update |
|-------|---------|-------------|
| Item | VideoItemUIMapper.kt | yes (new fields) |
| Item | preview/VideoPreviewProvider.kt | yes (add new fields to fixtures) |

## Navigation

- How screens connect: `ListScreen → DetailsScreen(id) → PlayerScreen(id)`
- Router configuration needed (AppRouter commands)
- Screen factory methods in `Screens` / `ScreensImpl`

## DI Modules

- New Koin modules or existing ones to modify
- Global modules (`interactorModule`) vs screen-scoped (`buildModule`)

## Testing Considerations

- Key scenarios to verify
- Edge cases from spec to test

## Notes

- <Risks, open questions, things to clarify during implementation>
```

### Step 4b: Self-review (MANDATORY before presenting)

Before showing the plan to the user, review it as if you were an agent with NO conversation context:

**Per-step checklist:**
- [ ] Exact file paths specified (not "the VM" but `app/src/main/java/com/kino/puber/ui/feature/<name>/vm/<Name>VM.kt`)
- [ ] All fields/methods to add or modify are named explicitly
- [ ] DI dependencies checked — does the VM constructor need new params? (e.g., `ResourceProvider`)
- [ ] All side-effects listed — files to delete, imports to update, Koin modules to modify
- [ ] No ambiguous language — words like "clean", "simple", "analogous", "etc." replaced with concrete descriptions

**Clarity rule:** Every step must be understandable by an agent that has NEVER seen this conversation. If a step says "replace X with Y" — both X and Y must be fully described with code examples or exact field names.

### Step 5: Present to user
- If `plan.md` already exists → warn user: "Plan already exists. Overwrite?" (use ask_question)
- Show the plan summary (number of steps, key decisions)
- Ask if anything needs adjustment before implementation
- Update `meta.json`: set `totalSteps` to the number of plan steps, `currentStep` to 0, `status` to `plan-complete`
- The plan is a REFERENCE document — it guides implementation but can be updated

## Progress tracking

Each step in plan.md uses checkbox format for progress tracking:

```markdown
### Step 1: <Title>  ← [ ] pending / [x] done
```

When a step is completed during implementation:
1. Update the step's checkbox from `[ ]` to `[x]` in plan.md
2. Optionally add a completion note: `[x] Done — see commit abc1234`

The `/prompt:feature-context plan` command shows progress summary: "3/7 steps completed, next: Step 4".

## Important
- Always reference `design.md` / `layouts.md` and spec sections in each step
- Follow project patterns from AGENTS.md strictly
- Order steps by dependency: data layer → VM → UI → navigation → DI
- Check what ALREADY exists — don't plan to recreate existing code
- Each step should be doable in one focused coding session
- Do NOT start implementing — only create the plan
- Use `[ ]` / `[x]` checkboxes on step headers for progress tracking
- Add `complexity: low/medium/high` to each step header — helps estimate effort
- For Android codebase analysis: use `agent: "android-codebase-analyst"`
- Use Haiku subagents for simple file searches (existence checks, import lookups)
- For UI steps, always include **Screenshots** field with relevant PNG filenames
- Load `.todo/_shared/` knowledge to avoid repeating past mistakes
- **Parallel groups**: After generating all steps, scan for parallel candidates — steps with no mutual file conflicts and no mutual dependencies. Assign `parallel-group: <letter>` tags. This is NOT optional — always look for parallelization opportunities. Even 2 steps in parallel saves significant time.
