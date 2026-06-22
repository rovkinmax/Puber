---
description: Initialize feature workspace in .todo/ for design, spec and plan data
---

# Feature Init

Sets the active feature and creates a minimal workspace directory in `.todo/`.

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
5. Writes `.todo/.current` — plain text file with just the feature name (pointer only)
6. Reports readiness and suggests next step: `/prompt:feature-design <figma-url>`

## Switching features
- Running `/prompt:feature-init` with a different name updates `.current` pointer
- Old feature's `meta.json` and all data stay intact in `.todo/<old-name>/`
- To resume an old feature, run `/prompt:feature-init <old-name>` — it reuses existing folder and meta.json

## Important
- `.current` contains ONLY the feature name — a plain string, not JSON
- All metadata lives in `.todo/<feature>/meta.json` — survives feature switching
- Creates ONLY the root directory — sub-folders are created by commands that populate them
- If `.todo/<name>/` already exists, ask user whether to reset or keep existing data
