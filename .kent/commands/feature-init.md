---
description: Initialize feature workspace in .todo/ for design, spec and plan data
---

# Feature Init

Creates a minimal feature workspace directory in `.todo/`.

## Usage
```
/prompt:feature-init video-details
/prompt:feature-init "Video Details Screen"
/prompt:feature-init playerScreen
```

## Parameters
- name: feature name in any format (required) — will be normalized automatically

## What it does

1. **Ensure `.todo/` exists** — create if missing: `mkdir -p .todo`
2. Normalize the name to lowercase-kebab-case:
   - "Video Details Screen" → `video-details-screen`
   - "playerScreen" → `player-screen`
   - "Mark as Favorite flow" → `mark-as-favorite-flow`
   - Already kebab-case → keep as-is
3. Creates `.todo/<normalized-name>/` — only the root directory, nothing inside
4. Creates `.todo/<normalized-name>/meta.json`:
   ```json
   {
     "name": "<normalized-name>",
     "createdAt": "<YYYY-MM-DD>",
     "currentStep": 0
   }
   ```
5. Reports the explicit workspace path: `.todo/<normalized-name>`.
6. Suggests next step: `/prompt:feature-design .todo/<normalized-name> <figma-url>`.

## Reusing features
- Running `/prompt:feature-init` with an existing name reuses `.todo/<name>/` and `meta.json` unless the user explicitly
  asks to reset it.
- To resume an old feature, pass the feature name or `.todo/<feature>` path explicitly to the command you are running.

## Important
- There is no global active feature pointer.
- Do not create or update `.todo/.current`.
- All metadata lives in `.todo/<feature>/meta.json`.
- Creates ONLY the root directory — sub-folders are created by commands that populate them
- If `.todo/<name>/` already exists, ask user whether to reset or keep existing data
