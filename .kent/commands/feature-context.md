---
description: Load cached design, spec and plan context for an explicit feature workspace
---

# Feature Context

Loads cached feature data (design screens, spec, plan) into the current session to provide context for coding work.

## Usage
```
/prompt:feature-context                  # Load full context summary
/prompt:feature-context <screen-name>    # Load specific screen details only
/prompt:feature-context plan             # Load plan with progress status
/prompt:feature-context .todo/<feature> plan
```

## Parameters
- screen-name: load only the specified screen's design file (optional)
- "plan": load only the plan with current progress (optional)
- feature target: feature name, `.todo/<feature>` path, or workflow-provided workspace path

## What it does

### Step 1: Determine feature
- Load `.kent/skills/puber-android-workflow/references/rules/feature-target-resolution.md`.
- Resolve the feature target from arguments or Kent workflow task context.
- Read `.todo/<name>/meta.json` → quick-access metadata (`screens`, `currentStep`, `totalSteps`)
- If no target is available, list available feature dirs in `.todo/` and ask which one to use. Do not create or update
  `.todo/.current`.

### Step 2: Load and summarize

**Full context (no arguments):**
1. List design files — check both structures:
   - New style: `.todo/<feature>/design.md` + `.todo/<feature>/layouts.md`
   - Old style: `.todo/<feature>/design/screens/*.md`
2. Read design overview — extract screen list and key layout info (skip verbose ASCII art)
3. Read `.todo/<feature>/navigation-flow.md` if it exists — include navigation summary (N screens, M transitions)
4. Read `.todo/<feature>/spec.md` — extract `## Overview`, `## User Flows`, `## Screen Behavior` headings (not full content)
5. Read `.todo/<feature>/plan.md` — extract `## Summary` and step list with completion status
6. Output a compact summary:
   ```
   Feature: <name>
   Screens: <list with one-line descriptions>
   Spec: <overview + flow names>
   Plan: <N/M steps done>
   Screenshots: K files in screenshots/
   ```

**Specific screen (`/prompt:feature-context details`):**
1. Read design file for this screen (from layouts.md or design/screens/<name>.md) in full
2. **Load screenshots** — read matching PNG files from `screenshots/` using the Read tool (images are displayed visually). Check layouts.md screenshots index to find the right files.
3. Read the matching section from `spec.md` (Screen Behavior → <screen-name>)
4. Read the matching step(s) from `plan.md`
5. Output everything — full detail for this screen with visual reference

**Plan only (`/prompt:feature-context plan`):**
1. Read `plan.md` in full
2. Highlight completed `[x]` vs pending `[ ]` steps
3. Suggest the next step to work on

## Important
- Minimize token usage — the whole point is to avoid re-reading everything from Figma
- For full context: extract key info only, not full file contents
- For specific screen: show full details — user needs them for implementation
- If a file is missing, skip it silently (not all steps may have been run yet)
