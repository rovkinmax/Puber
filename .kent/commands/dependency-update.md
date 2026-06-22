---
description: Check and update project dependencies systematically
---

# Dependency Update

Checks all dependencies for updates, creates a prioritized
update plan, and applies updates with build verification.

## Usage
```
/prompt:dependency-update                  # check all dependencies
/prompt:dependency-update compose          # only compose-related
/prompt:dependency-update --security-only  # only security patches
```

## Parameters
- filter: keyword to filter dependencies (optional)
- --security-only: only update patch versions (optional)

## What it does

### Phase 1: Discover outdated dependencies

1. Read `gradle/libs.versions.toml` for current versions
2. Use `.kent/adapters/mcp/mcp-call.sh package-version-check.get_latest_package_versions`
   to check for updates (batch by group)
3. If MCP unavailable, use web search as fallback
4. Build update table:

```markdown
| Dependency | Current | Latest | Type |
|------------|---------|--------|------|
| kotlin | 2.2.0 | 2.2.1 | patch |
| compose-bom | 2025.07.00 | 2025.08.00 | minor |
| agp | 8.12.0 | 8.12.1 | patch |
```

### Phase 2: Prioritize

Sort updates by risk:
1. **Patch** (X.Y.Z → X.Y.Z+1): safe, apply all
2. **Minor** (X.Y → X.Y+1): usually safe, review changelogs
3. **Major** (X → X+1): breaking changes likely, plan carefully

For --security-only: only include patch updates.

### Phase 3: Plan

Create `.todo/dependency-update-<date>/plan.md`:

```markdown
# Dependency Update

> Date: <date>
> Updates found: N (P patch, M minor, K major)

## Steps

### [ ] Step 1: Patch updates (safe)  `complexity: low`
- kotlin: 2.2.0 → 2.2.1
- agp: 8.12.0 → 8.12.1
- coroutines: 1.10.1 → 1.10.2
- **Verify:** full compile

### [ ] Step 2: Compose BOM update  `complexity: medium`
- compose-bom: 2025.07.00 → 2025.08.00
- **Verify:** compile
- **Note:** may need UI adjustments

### [ ] Step 3: Major updates  `complexity: high`
- <library>: X.0 → Y.0
- **Breaking changes:** <list from changelog>
- **Verify:** compile + manual check
```

**Grouping rules:**
- Patch updates: batch together (one step)
- BOM updates: separate step (coordinates multiple libs)
- Major updates: one per step
- Kotlin + KSP: always update together

### Phase 4: Execute

For each step:
1. Update versions in `libs.versions.toml`
2. Sync Gradle
3. Compile:
   ```bash
   ./gradlew :app:compileDevDebugKotlin 2>&1 |
     grep -E "e: |error:|FAILURE|What went wrong" -A3
   ```
4. If compilation fails → check deprecation replacements,
   API changes, fix
5. Mark `[x]` in plan.md
6. Update `meta.json`

### Phase 5: Report

```
Dependency update complete:
Updated: N dependencies
  - P patch, M minor, K major
Build: passing
Skipped: L (major, need manual review)
```

## Important
- Always update Kotlin + KSP versions together
- Compose BOM updates may change visuals — check UI
- AGP updates: check for deprecated API usage
- For major updates: check changelog/migration guide first
- Do NOT auto-commit — let user decide
- Suggest separate commits: patches, minor, major
