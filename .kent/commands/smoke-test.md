---
description: Run smoke test for a feature via MCP mobile
---

# Smoke Test

Runs smoke test for a feature via MCP mobile.

## Usage
```
/prompt:smoke-test favorites
/prompt:smoke-test details
```

## Parameters
- feature: feature name (required)

## What it does

1. Reads the MCP Mobile Testing section in `AGENTS.md` to understand the process
2. **Acquire a mobile resource lock before touching any emulator/device**
   - Prefer already-running healthy emulators. Discover them with:
     ```bash
     mapfile -t EMULATORS < <(.kent/adapters/mobile/emulator-resource-lock.sh adb-emulators)
     ```
   - If one or more emulators are already running, acquire any free emulator-specific lock:
     ```bash
     LOCK_OUTPUT="$(.kent/adapters/mobile/emulator-resource-lock.sh acquire-any "${EMULATORS[@]}" -- 900 7200)"
     LOCK_RESOURCE="$(printf '%s\n' "$LOCK_OUTPUT" | sed -n 's/^resource=//p')"
     LOCK_TOKEN="$(printf '%s\n' "$LOCK_OUTPUT" | sed -n 's/^token=//p')"
     DEVICE_SERIAL="$LOCK_RESOURCE"
     trap '.kent/adapters/mobile/emulator-resource-lock.sh release "$LOCK_RESOURCE" "$LOCK_TOKEN"' EXIT
     ```
   - If no emulator is running, acquire the default machine-level lock before starting or using the default emulator:
     ```bash
     LOCK_RESOURCE="default-emulator"
     LOCK_TOKEN="$(.kent/adapters/mobile/emulator-resource-lock.sh acquire "$LOCK_RESOURCE" 900 7200)"
     DEVICE_SERIAL=""
     trap '.kent/adapters/mobile/emulator-resource-lock.sh release "$LOCK_RESOURCE" "$LOCK_TOKEN"' EXIT
     ```
   - Keep the token until smoke testing is fully reported. The `trap` releases it on normal exit or failure; explicit
     release is also fine after the report:
     ```bash
     .kent/adapters/mobile/emulator-resource-lock.sh release "$LOCK_RESOURCE" "$LOCK_TOKEN"
     ```
   - When running `adb`, pass `-s "$DEVICE_SERIAL"` if `DEVICE_SERIAL` is set.
   - If all running emulators are busy, inspect lock owners with:
     ```bash
     .kent/adapters/mobile/emulator-resource-lock.sh status <emulator-serial>
     ```
   - Start a second emulator only when the task/user explicitly allows parallel device usage and a suitable AVD/host
     capacity is available. If a second emulator is used, acquire a distinct lock name such as
     `emulator-5556` or `avd-<name>-<port>` before starting or using it.
   - If no device can be safely acquired, complete the workflow with `blocked` and explain who/what holds the resource.
3. **Always builds and installs a fresh `devDebug` APK immediately before device testing**
   - Do this even if the user says the app is already running; stale APKs can hide or misattribute regressions.
   - In a Kent worktree, use `./tools/agentw installDevDebug`; in the main checkout, use `./gradlew installDevDebug`.
   - Then restart the app with:
     ```bash
     adb ${DEVICE_SERIAL:+-s "$DEVICE_SERIAL"} shell am force-stop com.kino.puber.stage
     adb ${DEVICE_SERIAL:+-s "$DEVICE_SERIAL"} shell am start -n com.kino.puber.stage/com.kino.puber.MainActivity
     ```
4. Connects to device via MCP mobile
5. **Launches app via adb**:
   ```bash
   adb ${DEVICE_SERIAL:+-s "$DEVICE_SERIAL"} shell am start -n com.kino.puber.stage/com.kino.puber.MainActivity
   ```
   Note: `com.kino.puber.stage` is the dev flavor package. For prod builds use `com.kino.puber`.
6. Navigates to feature
7. Goes through main screens
8. Outputs report
9. Releases the mobile resource lock

## Testing Strategy

### Use `get_ui` as the primary tool for screen inspection:
- **`get_ui`** — main tool for reading screen state, verifying content, and finding focus/tap targets
- **`get_ui(showAll: true)`** — when you need to see non-interactive elements too
- **`assert_visible`** for quick "is element on screen?" checks
- **`screenshot`** only for visual bug evidence or when user explicitly asks
- **NEVER use `screenshot` to read screen state** — always use `get_ui` instead

### Speed optimizations:
- **`tap(hints: true)`** — get state change info without extra get_ui
- **`wait_for_element`** instead of `wait(ms)` for loading/animations
- **`find_and_tap`** for fuzzy element matching when exact text is unknown
- **`get_logs(package: "com.kino.puber.stage", level: "E")`** for error-only logs

### Screen verification checklist:
- Loading → Content transition (use `wait_for_element`)
- No "null", placeholder, or empty texts (use `get_ui`)
- Expected elements present (use `assert_visible`)
- No crash/exception in logs (use `get_logs` with error filter)
- TV remote navigation works (D-pad focus movement)

### TV-specific checks:
- Focus is visible on interactive elements
- D-pad navigation moves focus correctly between items
- Select/Enter activates the focused item
- Back button navigates back properly

## On Issues
- Asks if unclear where to navigate
- Takes screenshot for visual evidence (only for bugs)
- Saves artifacts to build/test-artifacts/ on errors
- Checks `get_logs(package: "com.kino.puber.stage", level: "E")` for crashes

## Example Report

### Smoke Test: Favorites ✅

**Checked screens:**
- [x] Favorites grid (Content state)
- [x] Video details
- [x] Empty state
- [x] D-pad navigation

**Issues:** none found

---

### Smoke Test: Details ⚠️

**Checked screens:**
- [x] Details screen
- [x] Season/episode list

**Issues:**
1. Loading stuck >3sec on details screen
2. Warning in logs: "DetailsVM: cache miss"

**Artifacts:** build/test-artifacts/details_20260323_1430/
