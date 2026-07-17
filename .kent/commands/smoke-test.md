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

## Evidence Safety

- Store only the minimum evidence required for the Smoke decision.
- Never persist full `adb logcat`, network payloads, authentication headers, or
  a raw UI dump/screenshot from an unexpected authenticated or sensitive state.
- Establish the expected non-sensitive screen with assertions before
  requesting a full UI tree.
- Run `.kent/adapters/mobile/mobile-evidence-audit.sh
  <evidence-dir> <package-name>` before reporting success or a blocker.

## What it does

1. Reads the MCP Mobile Testing section in `AGENTS.md` to understand the process
2. **Acquire an emulator resource lock before touching any emulator/device**
   - Physical devices, including a real TV, are forbidden unless the task/user explicitly provides permission and an
     explicit serial for that physical device. Never rely on adb's default target selection.
   - Prefer already-running healthy emulators. Discover them with:
     ```bash
     EMULATORS=($(.kent/adapters/mobile/emulator-resource-lock.sh adb-emulators))
     ```
   - If one or more emulators are already running, acquire any free emulator-specific lock:
     ```bash
     LOCK_OUTPUT="$(.kent/adapters/mobile/emulator-resource-lock.sh acquire-any "${EMULATORS[@]}" -- 900 7200)"
     LOCK_RESOURCE="$(printf '%s\n' "$LOCK_OUTPUT" | sed -n 's/^resource=//p')"
     LOCK_TOKEN="$(printf '%s\n' "$LOCK_OUTPUT" | sed -n 's/^token=//p')"
     DEVICE_SERIAL="$LOCK_RESOURCE"
     trap '.kent/adapters/mobile/emulator-resource-lock.sh release "$LOCK_RESOURCE" "$LOCK_TOKEN"' EXIT
     ```
   - If no emulator is running, do not run `adb` without `-s`. Either block, or start an emulator only when the task/user
     explicitly allows starting one. After the emulator appears in `adb devices`, acquire its emulator-specific lock and
     set `DEVICE_SERIAL` to that serial.
   - Use a physical device only with explicit user permission and an explicit serial:
     ```bash
     LOCK_RESOURCE="<explicit-physical-serial>"
     LOCK_TOKEN="$(.kent/adapters/mobile/emulator-resource-lock.sh acquire "$LOCK_RESOURCE" 900 7200)"
     DEVICE_SERIAL="$LOCK_RESOURCE"
     trap '.kent/adapters/mobile/emulator-resource-lock.sh release "$LOCK_RESOURCE" "$LOCK_TOKEN"' EXIT
     ```
   - Keep the token until smoke testing is fully reported. The `trap` releases it on normal exit or failure; explicit
     release is also fine after the report:
     ```bash
     .kent/adapters/mobile/emulator-resource-lock.sh release "$LOCK_RESOURCE" "$LOCK_TOKEN"
     ```
   - Before any install, launch, log, or shell command, verify `DEVICE_SERIAL` is non-empty and pass `adb -s
     "$DEVICE_SERIAL"`. If `DEVICE_SERIAL` is empty, complete with `needs_user_action` instead of running adb.
   - If all running emulators are busy, inspect lock owners with:
     ```bash
     .kent/adapters/mobile/emulator-resource-lock.sh status <emulator-serial>
     ```
   - Start a second emulator only when the task/user explicitly allows parallel device usage and a suitable AVD/host
     capacity is available. If a second emulator is used, acquire a distinct lock name such as
     `emulator-5556` or `avd-<name>-<port>` before starting or using it.
   - If no device can be safely acquired, complete the workflow with `needs_user_action` and explain who/what holds the
     resource.
3. **Always builds and installs a fresh `devDebug` APK immediately before device testing**
   - Do this even if the user says the app is already running; stale APKs can hide or misattribute regressions.
   - Do not use Gradle `install*` tasks for smoke tests; they may invoke adb without the selected serial.
   - Build the APK, then install it with explicit `adb -s "$DEVICE_SERIAL"`:
     ```bash
     test -n "$DEVICE_SERIAL"
     if pwd | grep -q '/.kent/worktrees/'; then
       ./tools/agentw :app:assembleDevDebug
     else
       ./gradlew :app:assembleDevDebug
     fi
     adb -s "$DEVICE_SERIAL" install -r app/build/outputs/apk/dev/debug/app-dev-debug.apk
     adb -s "$DEVICE_SERIAL" shell am force-stop com.kino.puber.stage
     adb -s "$DEVICE_SERIAL" shell am start -n com.kino.puber.stage/com.kino.puber.MainActivity
     ```
4. **Binds Mobile MCP to the locked serial**
   - List devices and confirm `DEVICE_SERIAL` is present:
     ```bash
     .kent/adapters/mcp/mcp-call.sh mobile.device \
       action=list \
       --output json \
       --raw-dir <raw-dir>
     ```
   - Select the exact locked serial:
     ```bash
     .kent/adapters/mcp/mcp-call.sh mobile.device \
       action=set \
       platform=android \
       deviceId="$DEVICE_SERIAL" \
       --allow-mutate \
       --output json \
       --raw-dir <raw-dir>
     ```
   - Pass `deviceId="$DEVICE_SERIAL"` to every target-specific Mobile MCP call.
   - If Mobile MCP cannot list or select the locked serial, complete with
     `needs_user_action`; never switch to an implicit target.
5. **Launches app via adb**:
   ```bash
   test -n "$DEVICE_SERIAL"
   adb -s "$DEVICE_SERIAL" shell am start -n com.kino.puber.stage/com.kino.puber.MainActivity
   ```
   Note: `com.kino.puber.stage` is the dev flavor package. For prod builds use `com.kino.puber`.
6. Navigates to feature
7. Goes through main screens
8. Audits the evidence directory and outputs a sanitized report
9. Releases the mobile resource lock

## Testing Strategy

### Use assertions before full screen inspection:
- Call Mobile MCP only through `.kent/adapters/mcp/mcp-call.sh` and pass the
  locked `deviceId` to every target-specific call.
- Use `assert_visible` to establish the expected non-sensitive state first.
- **`get_ui`** — main tool for reading screen state, verifying content, and finding focus/tap targets
- **`get_ui(showAll: true)`** — when you need to see non-interactive elements too
- **`screenshot`** only for visual bug evidence or when user explicitly asks
- **NEVER use `screenshot` to read screen state** — always use `get_ui` instead
- If an unexpected authenticated/account state appears, do not persist its
  full UI tree or screenshot. Record a redacted blocker and use
  `needs_user_action`.

### Speed optimizations:
- **`tap(hints: true)`** — get state change info without extra get_ui
- **`wait_for_element`** instead of `wait(ms)` for loading/animations
- **`find_and_tap`** for fuzzy element matching when exact text is unknown
- **`get_logs(package: "com.kino.puber.stage", level: "E")`** for a
  task-specific error signal; summarize and redact instead of persisting raw
  output

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
- Takes a screenshot only for a visual bug on a known non-sensitive screen
- Saves artifacts to build/test-artifacts/ on errors
- Keeps only package-scoped crash/ANR/liveness summaries
- Never saves full `adb logcat` output
- Runs `.kent/adapters/mobile/mobile-evidence-audit.sh
  <evidence-dir> <package-name>` before reporting

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
