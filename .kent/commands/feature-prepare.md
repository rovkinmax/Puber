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
```

## Parameters
- figma-url: one or more Figma URLs with node-id (optional — will ask if not provided)
- spec-source: path or URL to specification (optional — if omitted, enters interview mode after design)

## What it does

### Phase 1: Bootstrap (silent)
- Read `.todo/.current` — if no active feature, ask user for feature name (free-form, normalize to kebab-case)
- Create workspace if needed (like `/prompt:feature-init`)

### Phase 2: Design (semi-interactive)
- If Figma URL(s) provided → **use the Kent prompt command to invoke `/prompt:feature-design`** with all URLs as arguments
- If no Figma URL → ask user via ask_question (user can also skip design)
  - If URLs provided → **use the Kent prompt command to invoke `/prompt:feature-design`** with them
- **CRITICAL**: Do NOT extract design data inline. ALWAYS delegate to `/prompt:feature-design` via Kent prompt command.
- **Verification checkpoint**: confirm `.todo/<feature>/screenshots/`, `design.md`, `layouts.md`, `nodes.json` exist

### Phase 3: Spec (interactive if no source)

**If spec source provided:**
- **Use the Kent prompt command to invoke `/prompt:feature-spec`** with the source path/URL as argument

**If no spec source:**
- **Use the Kent prompt command to invoke `/prompt:feature-spec`** (no arguments → interview mode)
- **CRITICAL**: Do NOT run the spec interview inline. ALWAYS delegate to `/prompt:feature-spec` via Kent prompt command.
- **Verification checkpoint**: confirm `.todo/<feature>/spec.md` exists

### Phase 4: Summary
- Report what was collected:
  ```
  Feature: <name>
  Design: N screens saved
  Spec: saved (source: file/interview)
  Next: run /prompt:feature-plan to generate implementation plan
  ```

## Important
- **ALWAYS use Kent prompt command for sub-commands** — never implement design/spec phases inline
- Only pause for user input when necessary (spec interview, ambiguous questions)
- Design phase runs silently — no confirmation between screens
- **Verification between phases** — check that expected output files exist after each phase
- If any phase fails, stop and report — don't continue with incomplete data
