---
description: Prepare a feature in one session — init, design, spec and plan
---

# Feature Start

Runs the entire feature preparation flow in the current Kent session: init → design → spec → plan.

## Usage
```
/prompt:feature-start <figma-url>
/prompt:feature-start <figma-url1> <figma-url2> ...
/prompt:feature-start <figma-url> /path/to/spec.md
/prompt:feature-start <description without URLs>
/prompt:feature-start .todo/<feature> <figma-url-or-description>
```

## Parameters
- figma-url: one or more Figma URLs with node-id (optional — will ask if not provided)
- spec-source: path or URL to specification (optional — if omitted, enters interview mode)
- feature target: optional feature name, `.todo/<feature>` path, or workflow-provided workspace path

## What it does

### Phase 1: Bootstrap (silent)
- Load `.kent/skills/puber-android-workflow/references/rules/feature-target-resolution.md`.
- Resolve the feature target from arguments or Kent workflow task context. If no target is available, derive a
  kebab-case name from the task title/body or ask the user.
- Ensure `.todo/` directory exists (create if missing)
- **Check .gitignore** — if `.todo/` is not in `.gitignore`, add it (feature workspace is local-only, should not be committed):
  ```bash
  grep -q '\.todo/' .gitignore 2>/dev/null || echo '.todo/' >> .gitignore
  ```
- Create workspace if needed (`.todo/<name>/` + `meta.json`)
- Do not create or update `.todo/.current`.

### Phase 1b: Detect task type

Determine if this is a **visual feature** (has UI design) or a **code-only change** (refactoring, migration, architectural change):

- **Visual feature indicators:** Figma URL provided, user mentions "screen", "design", "UI"
- **Code-only indicators:** no Figma URL, user mentions "refactor", "migrate", "replace", "extract", "split", "universal component", "consolidate"

**If code-only:**
- If this is a refactor or migration, stop and report that the task should use the refactor/migration workflow instead
  of invoking another `/prompt:*` flow from this Plan session.
  - In a Kent workflow task, complete with `needs_user_action` and provide `blocker_reason` stating that the task is
    misclassified, naming the expected workflow, and giving the exact user action required to recreate or move it.
  - In manual command use, report the appropriate refactor or migration command without invoking it.
- If this is a non-visual feature, skip Phase 2 (Design), still run Phase 3 (Spec) to create `.todo/<feature>/spec.md`
  from the task description or interview, then proceed to Phase 4 (Plan).
- The plan phase handles its own codebase analysis via `project-researcher`.
- If unsure, ask the user via `ask_question`: "Is there a Figma design for this, or is it a code-only change?"

### Phase 2: Design (semi-interactive)

**Pre-processing URLs** (before passing to design):
- User may provide URLs mixed with comments (lines starting with `//` or `#`, or inline comments after URLs)
- Extract all valid Figma URLs (match `https://www.figma.com/design/...?node-id=...`)
- **Deduplicate** by `node-id` parameter — users often paste the same URL multiple times
- Log: "Found N unique URLs from M provided (K duplicates removed)" if duplicates found
- **Save user's annotated URL groups** to `.todo/<feature>/url-annotations.md` — preserves the user's comments and grouping context for design/spec phases

**Running design:**
- Load `.kent/commands/feature-design.md` as the design procedure for this phase.
- If Figma URL(s) are provided, execute that procedure in the current session with the explicit workspace path and
  deduplicated URLs as inputs.
- If no Figma URL(s) → ask user via ask_question for Figma links
  - User can provide one or many URLs, possibly with comments — extract all valid Figma URLs
  - User can answer "no design" → skip design phase entirely
  - If URLs are provided, deduplicate them and execute the loaded design procedure with the explicit workspace path.
- Do not invoke `/prompt:feature-design`, start a nested prompt flow, or delegate ownership of the phase to another
  session. Follow the loaded procedure directly so all extraction and artifact rules remain intact.
- **Verification checkpoint** after the design procedure completes — check artifacts:
  - `.todo/<feature>/design.md` — **required** (stop if missing)
  - `.todo/<feature>/layouts.md` — **required** (stop if missing)
  - `.todo/<feature>/nodes.json` — **required** (stop if missing)
  - `.todo/<feature>/screenshots/` directory with PNG files — **optional** (warn if missing, but continue)

### Phase 3: Spec (interactive if no source)

**IMPORTANT**: Spec phase runs AFTER design is complete. This is intentional — design answers many questions that would otherwise need to be asked in the spec interview.

**If spec source provided:**
- Load `.kent/commands/feature-spec.md` as the spec procedure and execute it in the current session with the explicit
  workspace path and source path/URL.
- Pause only if ambiguous questions arise

**If no spec source:**
- Execute the loaded spec procedure with the explicit workspace path and no source, using its interview mode.
- The procedure must:
  - Load design context when design artifacts exist
  - Only ask questions about things NOT answerable from available sources
  - Save spec.md when enough info collected
- Do not invoke `/prompt:feature-spec` or start another prompt flow. The interview remains part of this Plan session.
- **Verification checkpoint** after the spec procedure completes:
  - `.todo/<feature>/spec.md` exists
  - If missing → report error and stop

### Phase 4: Plan (silent)
- **Validation checkpoint**: verify `spec.md` exists before proceeding.
- For visual features, also verify `design.md` and `layouts.md` exist before proceeding.
- If a required artifact is missing → stop and report what's missing.
- Load `.kent/commands/feature-plan.md` as the planning procedure and execute it in the current session with the
  explicit workspace path.
- Follow its codebase-analysis and plan-generation rules directly; do not invoke `/prompt:feature-plan` or start another
  prompt flow.
- **Verification checkpoint** after the planning procedure completes:
  - `.todo/<feature>/plan.md` exists
  - If missing → report error and stop

### Phase 5: Final summary
- Report everything collected:
  ```
  Feature: <name>
  Design: N screens (M states), K screenshots saved
  Spec: saved (source: file/url/interview)
  Plan: P implementation steps

  Ready to implement: .todo/<feature>/plan.md
  ```
- Present plan steps overview for user review
- Ask if anything needs adjustment

## Important
- Keep bootstrap, design, spec, and planning in this one Kent session. The phase command files are procedure modules:
  read and follow them directly, but do not invoke nested `/prompt:*` flows.
- **Figma URLs are optional** — ask interactively if not provided, allow skipping design
- **Spec interview comes AFTER design** — design provides visual context that reduces spec questions
- **Verification between phases** — check that expected output files exist after each phase before proceeding
- If any phase fails, stop and report — don't continue with incomplete data
- The plan is presented at the end for review — user can ask for adjustments before implementation
