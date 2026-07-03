---
description: Parse Figma design via MCP and cache screen layouts locally
---

# Feature Design

Parses Figma design via MCP, extracts screen layouts, and saves them as markdown files to `.todo/<feature>/`.

## Usage
```
/prompt:feature-design https://www.figma.com/design/XXXXX/Puber?node-id=1234-5678
/prompt:feature-design <url1> <url2> <url3>
/prompt:feature-design .todo/<feature> <url1> <url2>
/prompt:feature-design                       # asks for URLs interactively
/prompt:feature-design .todo/<feature> --refresh details
```

## Parameters
- url: one or more Figma URLs with node-id (ask user via ask_question if not provided)
- --refresh <screen-name>: only update the specified screen (optional)
- feature target: feature name, `.todo/<feature>` path, or workflow-provided workspace path

## What it does

### Step 1: Determine feature
- Load `.kent/skills/puber-android-workflow/references/rules/feature-target-resolution.md`.
- Resolve the feature target from arguments or Kent workflow task context, then read `.todo/<name>/meta.json` for
  metadata.
- If no target is available, ask user for a feature name, normalize it to kebab-case, and auto-create
  `.todo/<name>/` + `meta.json` (like `/prompt:feature-init`).
- Do not create or update `.todo/.current`.
- This makes `/prompt:feature-init` optional — design can bootstrap the explicit workspace itself.

### Step 2: Collect Figma URLs
- If URLs provided as arguments → use them
- If no URLs provided → ask_question asking for Figma links (one or multiple)
- User may provide URLs inline, in a list, or mixed with comments — extract all valid Figma URLs
- **Check for URL annotations**: read `.todo/<feature>/url-annotations.md` if it exists (saved by `/prompt:feature-start`). This file contains the user's original comments and URL grouping — use it for screen classification context in Step 7

### Step 3: Parse and deduplicate node IDs
- Extract nodeId from each URL: `?node-id=X-Y` → `X:Y`
- Extract fileKey from URL path: `/design/:fileKey/`
- **Deduplicate** by nodeId — remove repeated nodes
- Log: "Found N unique nodes from M URLs"

### Step 4: Check Figma access and fetch screenshots

**First — check if `FIGMA_ACCESS_TOKEN` is set:**
```bash
FIGMA_TOKEN="$(printenv FIGMA_ACCESS_TOKEN | tr -d '\n\r ')"
[ -z "$FIGMA_TOKEN" ] && echo "NO_TOKEN" || echo "TOKEN_OK"
```

**If `NO_TOKEN`** → skip REST API entirely, go to Step 5 (MCP-only mode). Warn user: "FIGMA_ACCESS_TOKEN not set — using MCP for visual analysis only, no local screenshots will be saved."

**If `TOKEN_OK`** → use Figma REST API to download screenshots:

**Token validation** — quick sanity check:
```bash
curl -s -w "\nHTTP:%{http_code}" -H "X-Figma-Token: $FIGMA_TOKEN" \
  "https://api.figma.com/v1/files/<fileKey>?depth=1" | tail -1
```
If HTTP code is not 200 → warn user, switch to MCP-only mode (same as NO_TOKEN).

**CRITICAL — URL-encode node IDs**: The Figma REST API requires colons and commas to be percent-encoded in the `ids` query parameter. Raw `2010:23668` → `2010%3A23668`. Comma between IDs → `%2C`.

1. Batch-request export URLs (one API call for all nodes):
   ```bash
   IDS="2010%3A23668%2C2042%3A62613%2C..."
   curl -s -H "X-Figma-Token: $FIGMA_TOKEN" \
     "https://api.figma.com/v1/images/<fileKey>?ids=${IDS}&format=png&scale=2"
   ```
2. Parse JSON response → extract CDN URLs from `images` object. If `err` is not null → log error and retry with smaller batch.
3. Create `SCREENSHOT_DIR=".todo/<feature>/screenshots"` and run `mkdir -p "$SCREENSHOT_DIR"`.
4. Download all PNGs in parallel:
   ```bash
   curl -sL -o "$SCREENSHOT_DIR/<name>.png" "<cdn-url>" &
   # repeat for all image URLs, then:
   wait
   ```
5. **Name files by screen-state pattern** from the start: `list-content.png`, `details-loading.png`, `favorites-empty.png`. If screen classification isn't done yet, use the node ID as suffix: `screen-2042-62613.png` — then rename in Step 8 after classification.
6. Verify downloads: `ls "$SCREENSHOT_DIR"/*.png | wc -l` should match expected count

### Step 5: Visual analysis

**If REST API succeeded** (local PNGs exist): use the `Read` tool to view them — faster and avoids MCP overhead.

**If MCP-only mode** (no token or REST failed): use `.kent/adapters/mcp/mcp-call.sh figma.get_screenshot nodeId="<node-id>" --raw-dir ".todo/<feature>/mcp"` for each node. Note: these are visual-only (bytes not extractable), sufficient for analysis but no local files saved.

Read images in parallel batches (up to 4 at a time).

**Handle section/composite nodes:**
- If a screenshot shows a composite frame with multiple phone screens arranged side by side → this is a section/overview node
- **Transition map detection**: If the composite also shows **arrows/lines connecting screens** and/or **text labels above screens** → this is a **transition map** (navigation flow diagram). See "Extracting navigation from transition maps" below.
- The overview screenshot is still useful (save it as `overview-<name>.png` or `flow-<name>.png` for transition maps)
- **Smart detection**: Before extracting child nodes, check if the user already provided individual node URLs that cover the composite's children. If all children are already covered → **skip child detection** for this composite node.
- Only if the composite contains screens NOT covered by existing nodes:
  1. **Ask user first** via ask_question: "Composite [node] contains N screens not in individual nodes — extract them separately?"
  2. If user confirms → **prefer Figma REST API** to get children (faster):
     ```bash
     FIGMA_TOKEN="$(printenv FIGMA_ACCESS_TOKEN | tr -d '\n\r ')"
     curl -s -H "X-Figma-Token: $FIGMA_TOKEN" \
       "https://api.figma.com/v1/files/<fileKey>/nodes?ids=<nodeId>" | python3 -c "
     import json,sys; data=json.load(sys.stdin)
     node=list(data['nodes'].values())[0]['document']
     for c in node.get('children',[]):
       if c.get('type') in ('FRAME','COMPONENT','INSTANCE'):
         print(f\"{c['id']} {c.get('name','')}\")"
     ```
  3. **Fallback**: If REST API fails → use `.kent/adapters/mcp/mcp-call.sh figma.get_design_context nodeId="<node-id>" --raw-dir ".todo/<feature>/mcp"` for the section node
  4. Parse child node IDs from the response
  5. Add only **new** child node IDs to the processing queue (skip IDs already in the list)
  6. Re-run Step 4 for the new nodes if needed

### Step 6: Try get_design_context (optional, max 2 calls)
**Limit**: Max 2 calls total, but **1 call is usually sufficient** — pick the most complex screen and extract tokens from it. Apply extracted tokens to all screens since they share the same design system.

This tool wastes significant context on generated React/Tailwind code (~80% of response is irrelevant). Only use for screens where spacing/tokens are NOT obvious from screenshots.

**When to use:**
- The most complex screen (forms, multi-section layouts) — do this one FIRST
- A second call only if another screen uses visually different tokens/spacing
- **Skip** for simple screens (lists, empty states, dialogs) — screenshots are sufficient

For selected nodes, attempt `.kent/adapters/mcp/mcp-call.sh figma.get_design_context nodeId="<node-id>" clientLanguages=kotlin clientFrameworks=compose artifactType=WEB_PAGE_OR_APP_SCREEN --raw-dir ".todo/<feature>/mcp"`:
- `clientLanguages: "kotlin"`, `clientFrameworks: "compose"`, `artifactType: "WEB_PAGE_OR_APP_SCREEN"`
- **If response mentions Code Connect** → this is normal, not an error. Ignore Code Connect mappings and continue extracting layout data
- **From each response, extract ONLY:**
  - Component tree structure (hierarchy of elements)
  - Spacing, padding, margin values
  - Color tokens and typography styles
  - Component names and text content
- **IGNORE generated code** — the tool generates React/Tailwind/web code which is irrelevant for Android/Compose
- **Post-processing** — after receiving get_design_context response, immediately extract ONLY these into a compact summary and discard the rest:
  1. **Design tokens**: colors, font sizes, spacing values
  2. **Component names**: extract from attributes
  3. **Text content**: labels, placeholders, button text
  4. **Hierarchy**: parent-child nesting of named components
- If screens are similar, analyze only one — apply findings to others

### Step 7: Classify screens vs states
Analyze all screenshots and group into **screens** and **states**:

**Rules for classification:**
- If two screenshots share the same toolbar/title/overall structure but differ in content → **same screen, different states**
- If the only difference is expanded/collapsed sections → **same screen, different states**
- If showing a dialog/overlay on top of another screen → **same screen + overlay state**
- If showing a snackbar/toast on a screen → **same screen + transient feedback**
- If the toolbar, navigation, and overall layout differ → **different screens**

**IMPORTANT — Screen presentation type for composite-only screens:**
When a screen is visible ONLY in a composite/overview frame (not as a dedicated individual node), you CANNOT reliably determine its presentation type from the composite screenshot alone.
- **Always ask the user** via ask_question: "Screen '{name}' is only visible in the composite overview. What presentation type is it?" with options: "Full screen (push)", "Modal (full screen)", "Bottom sheet", "Dialog/overlay"

**Output format:**
```
Screen A: "Favorites Grid"
  - State: content (node 1234:5678) → favorites-content.png
  - State: empty (node 1234:5679) → favorites-empty.png

Screen B: "Video Details"
  - State: content (node 2345:6789) → details-content.png
  - State: loading (node 2345:6790) → details-loading.png
```

**Extracting navigation from transition maps:**

If a composite frame contains **arrows/lines** connecting screens and/or
**labels above screens** — this is a transition map. Extract the navigation graph:

1. **Identify screens by labels**: Text labels above/below each frame indicate screen names
2. **Trace arrows/connections**: Record source, target, and trigger for each arrow
3. **Determine presentation type from the map**:
   - Screens connected by horizontal arrows → typically **push navigation** (navigateTo)
   - Screens overlapping or floating above another → **overlay/bottom sheet** (showOver)
   - Share sheets, system UI → **system intent** (not in-app navigation)
   - Screens with same layout but different status labels → **states of one screen**, not separate screens
4. **Save navigation flow** to `.todo/<feature>/navigation-flow.md`

### Step 8: Generate design files

#### design.md — screen map
Create `.todo/<feature>/design.md`:
- Table mapping all Figma nodes to screens and states
- Duplicate analysis (which nodes show the same screen)
- List of unique screens to implement
- If transition map was found → add link: "Navigation flow: see `navigation-flow.md`"

#### layouts.md — detailed layouts
Create `.todo/<feature>/layouts.md` with for each screen:

1. **Screenshots index** — table linking filenames to screens/states/nodes
2. **ASCII layout** for each screen state
3. **Design tokens** — colors, typography, spacing extracted from design context or screenshots
4. **Components used** — list of UI components per screen
5. **Component mapping** — map visual elements to project UI Kit components:
   ```
   ## Component Mapping
   - Video card → VideoItem + VideoItemUIState
   - Video grid → VideoGrid + VideoGridUIState
   - Details panel → VideoItemGridDetails + VideoDetailsUIState
   - Loading spinner → FullScreenProgressIndicator
   - Rating badge → RatingUIState (IMDB/KP/PUB)
   - Card container → tv.material3.Card
   - Surface → tv.material3.Surface
   - Text → tv.material3.Text
   ```
   Reference `.kent/skills/puber-android-workflow/references/recipes/ui-components.md` for the full component catalog and `.todo/_shared/ui-decisions.md` for past mapping decisions.

Rename screenshot files to meaningful names based on classification:
```bash
mv ".todo/<feature>/screenshots/screen-01.png" ".todo/<feature>/screenshots/favorites-content.png"
```

### Step 9: Save nodes mapping
Save `.todo/<feature>/nodes.json` for `--refresh` support:
```json
{
  "fileKey": "<figma-file-key>",
  "figmaUrls": ["<url1>", "<url2>"],
  "screens": {
    "favorites-grid": {
      "states": {
        "content": "2085:24914",
        "empty": "2081:25431"
      }
    },
    "video-details": {
      "states": {
        "content": "2042:62613",
        "loading": "2042:64474"
      }
    }
  }
}
```

### Step 10: Update meta.json and report
- Update `.todo/<feature>/meta.json`: set `figmaUrls`, `screens` array
- If navigation flow was extracted → set `hasNavigationFlow: true` in meta.json
- Report: "Design: N screens (M states total), K screenshots saved"
- If transition map found → also report: "Navigation flow: P transitions between Q screens (see navigation-flow.md)"
- List screens with state counts

## --refresh behavior
1. Read `nodes.json` to find nodeId(s) for the given screen name
2. If screen not found in nodes.json, re-fetch metadata to find it
3. Re-download screenshots via REST API
4. Update that screen's section in layouts.md
5. Report what was updated

## Important
- **Screenshots**: Check `FIGMA_ACCESS_TOKEN` first. If set → use Figma REST API to save PNGs locally. If not set or REST fails → switch to MCP-only mode (visual analysis via `get_screenshot`, no local files). Decision is made once at Step 4, no mixing of paths
- **get_design_context**: Max 2 calls (1 is usually enough). Composite node detection calls don't count. If response contains Code Connect references — ignore them, extract layout data normally
- **Screen vs State**: Group aggressively — only truly different layouts are separate screens
- **Composite frames**: Recognize overview/flow frames, don't count as individual screens. **Skip child detection** if individual nodes already cover all children
- **Transition maps**: Composites with arrows/lines between screens → extract navigation graph to `navigation-flow.md`. States shown in the map are states of one screen, not separate screens
- Always use `clientLanguages: "kotlin"`, `clientFrameworks: "compose"` for MCP calls
- Normalize screen names to lowercase-kebab-case for filenames
- Keep descriptions concise but complete — this is the primary reference for implementation
