---
description: Collect feature specification from document or via interview mode
---

# Feature Spec

Processes a specification document or enters interview mode to collect feature requirements.

## Usage
```
/prompt:feature-spec                          # Interview mode — ask clarifying questions
/prompt:feature-spec /path/to/spec.md        # From local file
/prompt:feature-spec https://notion.so/...   # From Notion page (via MCP)
/prompt:feature-spec https://example.com/... # From any URL
```

## Parameters
- source: path to spec file or URL (optional — if omitted, enters interview mode)

## What it does

### Determine feature
- Read `.todo/.current` (plain text) → get feature name, then read `.todo/<name>/meta.json`
- If no current feature — ask user or suggest running `/prompt:feature-init <name>` first
- After saving spec, update `meta.json`: set `specSource` to "file", "url", or "interview"

### Load design context
- Read `.todo/<feature>/design.md` (screen map) + `.todo/<feature>/layouts.md` (detailed ASCII layouts, design tokens, components)
- Read `.todo/<feature>/navigation-flow.md` if it exists — contains the navigation graph extracted from Figma transition maps (screen transitions, presentation types, triggers). Use it to pre-fill navigation-related spec questions.
- **Do NOT re-read screenshot PNGs** — layouts.md already contains all visual information as ASCII art and component lists. Reading PNGs wastes context without adding value beyond what layouts.md provides.
- This gives context about what screens exist, their layout, states, and design tokens
- Reference these when asking questions or analyzing spec

### Check existing API
- Search `KinoPubApiClient.kt` for relevant endpoints
- When spec mentions API endpoints, check existing methods in the client to extract:
  - Request/response models (field names, types)
  - Existing data models in `data.api.models`
- Include extracted API models in the "API Endpoints" section of spec.md

---

### Mode A: From file/URL

1. Read the spec from source:
   - Local file → Read tool
   - Notion URL → `.kent/adapters/mcp/mcp-call.sh notion.notion-fetch`
   - Other URL → WebFetch
2. Extract and structure key information into sections (see format below)
3. Cross-reference with design screens: which screens are covered? which are missing?
4. Save structured spec to `.todo/<feature>/spec.md`
5. If anything is ambiguous or missing → ask clarifying questions via ask_question
6. Update spec.md with answers

### Mode B: Interview mode (no source)

Act as a senior mobile engineer and conduct a structured interview.

**Fast path — skip interview if design + API cover everything:**
Before starting the interview, check if ALL questions are already answered by:
1. Design (layouts.md) — UI structure, fields, states, components
2. API (KinoPubApiClient + data models) — data models, endpoints

If both sources together answer all non-obvious questions (no ambiguity remaining), **skip the interview entirely**:
- Compile spec.md directly from design + API findings
- Set `specSource` to "design+api" in meta.json
- Report: "Spec compiled without interview — design and API covered all questions"

When fast path is used (spec generated without interview), add a section to spec.md:

```markdown
## Auto-resolved (from design + API)

| Question | Answer | Source |
|----------|--------|--------|
| Which fields are shown in list? | title, poster, year, type | Figma list screen |
| What pagination size? | 20 items per page | API default |
```

This allows reviewers to verify that auto-resolved answers are correct.

**EXCEPTION — always ask about these even in fast path:**
- **UX timing parameters** (debounce, animation duration, delays)
- **Screen presentation type** — if a screen was only visible in a composite/overview Figma frame and its presentation type (modal, bottom sheet, push) wasn't explicitly confirmed during design phase, ask the user.
- **Ambiguous UI interaction patterns** — if the design shows a search field, filter, or picker but it's unclear which component/pattern to use, always ask the user.

**Phase 1: Review designs and filter questions**
- Read design.md + layouts.md (already loaded in "Load design context" step above — do NOT re-read)
- Summarize what you see: "I see N screens: <list>."
- **Before asking anything**, check what the design already answers:
  - Layout structure → answered by layouts.md
  - Component types (buttons, toggles, fields) → answered by screenshots
  - Form fields and their types → answered by screenshots
  - Empty states and their content → answered by screenshots
  - Visual states (loading, content, empty, error) → partially answered if multiple states in design
- Mark these as "answered by design" — do NOT ask about them

**Phase 2: Ask ONLY non-obvious questions (3-4 at a time)**
Only ask about things NOT visible in the design:

| Area | When to ask | When to skip |
|------|-------------|--------------|
| User flows | Complex multi-screen flows | Single obvious screen transitions |
| Data source | Multiple possible endpoints | Design clearly shows API-backed list |
| Edge cases | Offline/permissions behavior | Empty states already in design |
| Navigation | Ambiguous back/forward flows | Standard back navigation |
| States | Error recovery logic | States visible in design screenshots |
| Validation | Complex business rules | Simple required field validation |
| Interactions | Non-standard gestures | Standard D-pad/remote navigation |
| Offline | App requires offline mode | Standard online-only feature |

**When formulating questions**, include text descriptions of relevant design details since the user may not see screenshots in ask_question UI:
- BAD: "What happens when user taps this button?"
- GOOD: "The details screen has a 'Add to favorites' button in the toolbar. On press: (a) immediately adds and shows confirmation, or (b) shows a confirmation dialog first?"

**Key rule**: Always describe the UI element and its behavior in the question text. Never reference "the design" or assume the user remembers specific screenshot details. Provide enough visual context that the user can answer without looking at the Figma.

**Phase 3: Compile and save**
- After collecting enough info, compile into `.todo/<feature>/spec.md`
- Show summary to user for review

---

## Spec file format

```markdown
# <Feature Name> — Specification

> Source: <file path / URL / interview>
> Date: <YYYY-MM-DD>
> Feature: <feature name from .current>

## Overview

<1-3 sentences describing the feature>

## User Flows

### Flow 1: <Name>
1. User opens screen X
2. Sees list of items
3. Selects item → navigates to details
4. ...

### Flow 2: <Name>
...

## Screen Behavior

### <Screen Name>
- **Design ref:** `layouts.md` → <Screen Name> section
- **States:** loading, content, empty, error
- **Data source:** KinoPubApiClient method
- **Actions:**
  - Select item → open details
  - Back → previous screen
- **Navigation:**
  - Back → previous screen
  - Item select → DetailsScreen(id)

### <Another Screen>
...

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | /v1/items | List with pagination |
| GET | /v1/items/{id} | Item details |
| ... | ... | ... |

## Business Rules

1. Only authenticated users can access content
2. ...

## Edge Cases

1. Empty list → show illustration + "No items" message
2. Network error → show retry button
3. ...

## Open Questions

- [ ] Question that needs clarification
- [ ] ...
```

## Important
- Always read design files BEFORE asking questions — gives you context
- Batch questions 3-4 at a time (ask_question supports up to 4)
- Cross-reference spec with design screens — note mismatches
- If spec mentions screens not in design/, add to "Open Questions"
- Do NOT start implementing — only collect and document requirements
