---
description: Full pipeline — init, design, spec and plan in one go
---

# Feature Start

Full pipeline that runs the entire feature preparation flow: init → design → spec → plan.

## Usage
```
/prompt:feature-start <figma-url>
/prompt:feature-start <figma-url1> <figma-url2> ...
/prompt:feature-start <figma-url> /path/to/spec.md
/prompt:feature-start <description without URLs>
```

## Parameters
- figma-url: one or more Figma URLs with node-id (optional — will ask if not provided)
- spec-source: path or URL to specification (optional — if omitted, enters interview mode)

## What it does

### Phase 1: Bootstrap (silent)
- Read `.todo/.current` — if no active feature, ask user for feature name (free-form, normalize to kebab-case)
- Ensure `.todo/` directory exists (create if missing)
- **Check .gitignore** — if `.todo/` is not in `.gitignore`, add it (feature workspace is local-only, should not be committed):
  ```bash
  grep -q '\.todo/' .gitignore 2>/dev/null || echo '.todo/' >> .gitignore
  ```
- Create workspace if needed (`.todo/<name>/` + `meta.json` + `.current`)

### Phase 1b: Detect task type

Determine if this is a **visual feature** (has UI design) or a **code-only change** (refactoring, migration, architectural change):

- **Visual feature indicators:** Figma URL provided, user mentions "screen", "design", "UI"
- **Code-only indicators:** no Figma URL, user mentions "refactor", "migrate", "replace", "extract", "split", "universal component", "consolidate"

**If code-only:**
- Skip Phase 2 (Design) and Phase 3 (Spec) entirely
- Proceed directly to Phase 4 (Plan) — invoke `/prompt:feature-plan` which will do codebase analysis
- The plan phase handles its own codebase analysis via `android-codebase-analyst`
- If unsure, ask the user via `ask_question`: "Is there a Figma design for this, or is it a code-only change?"

### Phase 2: Design (semi-interactive)

**Pre-processing URLs** (before passing to design):
- User may provide URLs mixed with comments (lines starting with `//` or `#`, or inline comments after URLs)
- Extract all valid Figma URLs (match `https://www.figma.com/design/...?node-id=...`)
- **Deduplicate** by `node-id` parameter — users often paste the same URL multiple times
- Log: "Found N unique URLs from M provided (K duplicates removed)" if duplicates found
- **Save user's annotated URL groups** to `.todo/<feature>/url-annotations.md` — preserves the user's comments and grouping context for design/spec phases

**Invoking design:**
- If Figma URL(s) provided → **use the Kent prompt command to invoke `/prompt:feature-design`** with deduplicated URLs as arguments
- If no Figma URL(s) → ask user via ask_question for Figma links
  - User can provide one or many URLs, possibly with comments — extract all valid Figma URLs
  - User can answer "no design" → skip design phase entirely
  - If URLs provided → deduplicate, then **use the Kent prompt command to invoke `/prompt:feature-design`** with them
- **CRITICAL**: Do NOT extract design data inline. ALWAYS delegate to `/prompt:feature-design` via Kent prompt command. This ensures all steps (REST API screenshot saving, URL dedup, screen classification, nodes.json) are followed exactly.
- **Verification checkpoint** after `/prompt:feature-design` completes — check artifacts:
  - `.todo/<feature>/design.md` — **required** (stop if missing)
  - `.todo/<feature>/layouts.md` — **required** (stop if missing)
  - `.todo/<feature>/nodes.json` — **required** (stop if missing)
  - `.todo/<feature>/screenshots/` directory with PNG files — **optional** (warn if missing, but continue)

### Phase 3: Spec (interactive if no source)

**IMPORTANT**: Spec phase runs AFTER design is complete. This is intentional — design answers many questions that would otherwise need to be asked in the spec interview.

**If spec source provided:**
- **Use the Kent prompt command to invoke `/prompt:feature-spec`** with the source path/URL as argument
- Pause only if ambiguous questions arise

**If no spec source:**
- **Use the Kent prompt command to invoke `/prompt:feature-spec`** (no arguments → interview mode)
- The spec skill will automatically:
  - Load design context (design.md, layouts.md, screenshots)
  - Only ask questions about things NOT answerable from the design
  - Save spec.md when enough info collected
- **CRITICAL**: Do NOT run the spec interview inline. ALWAYS delegate to `/prompt:feature-spec` via Kent prompt command.
- **Verification checkpoint** after `/prompt:feature-spec` completes:
  - `.todo/<feature>/spec.md` exists
  - If missing → report error and stop

### Phase 4: Plan (silent)
- **Validation checkpoint**: verify design.md and spec.md both exist before proceeding
- If either is missing → stop and report what's missing
- **Use the Kent prompt command to invoke `/prompt:feature-plan`** (no arguments needed)
- The plan skill will automatically analyze codebase and generate steps
- **CRITICAL**: Do NOT generate the plan inline. ALWAYS delegate to `/prompt:feature-plan` via Kent prompt command.
- **Verification checkpoint** after `/prompt:feature-plan` completes:
  - `.todo/<feature>/plan.md` exists
  - If missing → report error and stop

### Phase 5: Final summary
- Report everything collected:
  ```
  Feature: <name>
  Design: N screens (M states), K screenshots saved
  Spec: saved (source: file/url/interview)
  Plan: P implementation steps

  Ready to implement. Run /prompt:feature-context to load context for coding.
  ```
- Present plan steps overview for user review
- Ask if anything needs adjustment

## Important
- **ALWAYS use Kent prompt command for sub-commands** — never implement design/spec/plan phases inline. Each sub-command has detailed step-by-step instructions that MUST be loaded via Kent prompt command.
- **Figma URLs are optional** — ask interactively if not provided, allow skipping design
- **Spec interview comes AFTER design** — design provides visual context that reduces spec questions
- **Verification between phases** — check that expected output files exist after each phase before proceeding
- If any phase fails, stop and report — don't continue with incomplete data
- The plan is presented at the end for review — user can ask for adjustments before implementation
