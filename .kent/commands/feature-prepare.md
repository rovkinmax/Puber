---
description: Composite flow — collect design and spec data for a feature
---

# Feature Prepare

Composite flow that chains `/prompt:feature-design` → `/prompt:feature-spec` to collect all data before planning.

## Usage
```
/prompt:feature-prepare <figma-url>
/prompt:feature-prepare <figma-url> /path/to/spec.md
/prompt:feature-prepare <figma-url> https://notion.so/...
/prompt:feature-prepare .todo/<feature> <figma-url> /path/to/spec.md
```

## Parameters
- figma-url: one or more Figma URLs with node-id (optional — will ask if not provided)
- spec-source: path or URL to specification (optional — if omitted, enters interview mode after design)
- feature target: optional feature name, `.todo/<feature>` path, or workflow-provided workspace path

## What it does

### Phase 1: Bootstrap (silent)
- Load `.kent/skills/puber-android-workflow/references/rules/feature-target-resolution.md`.
- Resolve the feature target from arguments or Kent workflow task context. If no target is available, ask for a feature
  name and normalize it to kebab-case.
- Create workspace if needed (like `/prompt:feature-init`).
- Do not create or update `.todo/.current`.

### Phase 2: Design (semi-interactive)
- If Figma URL(s) provided → **use the Kent prompt command to invoke `/prompt:feature-design`** with the explicit
  workspace path and all URLs as arguments
- If no Figma URL → ask user via ask_question (user can also skip design)
  - If URLs provided → **use the Kent prompt command to invoke `/prompt:feature-design`** with the explicit workspace
    path and URLs
- **CRITICAL**: Do NOT extract design data inline. ALWAYS delegate to `/prompt:feature-design` via Kent prompt command.
- **Verification checkpoint**: if design was not skipped, confirm `.todo/<feature>/screenshots/`, `design.md`,
  `layouts.md`, and `nodes.json` exist.

### Phase 3: Spec (interactive if no source)

**If spec source provided:**
- **Use the Kent prompt command to invoke `/prompt:feature-spec`** with the explicit workspace path and source path/URL
  as arguments

**If no spec source:**
- **Use the Kent prompt command to invoke `/prompt:feature-spec`** with the explicit workspace path (no source →
  interview mode)
- **CRITICAL**: Do NOT run the spec interview inline. ALWAYS delegate to `/prompt:feature-spec` via Kent prompt command.
- **Verification checkpoint**: confirm `.todo/<feature>/spec.md` exists

### Phase 4: Summary
- Report what was collected:
  ```
  Feature: <name>
  Design: N screens saved
  Spec: saved (source: file/interview)
  Next: run /prompt:feature-plan .todo/<feature> to generate implementation plan
  ```

## Important
- **ALWAYS use Kent prompt command for sub-commands** — never implement design/spec phases inline
- Only pause for user input when necessary (spec interview, ambiguous questions)
- Design phase runs silently — no confirmation between screens
- **Verification between phases** — check that expected output files exist after each phase
- If any phase fails, stop and report — don't continue with incomplete data
