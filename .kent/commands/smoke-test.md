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

- Use `.kent/scripts/workflow-checkpoint` to maintain the canonical ignored
  `.kent/runtime/<TASK-ID>/smoke-checkpoint.json`. Reconcile it before repeating
  build, install, launch, navigation, mutation, or evidence work, and persist it
  before every workflow transition.
- Store only the minimum evidence required for the Smoke decision.
- On the locked test emulator, bounded semantic or visual inspection and safe
  navigation of the already-authenticated app UI are allowed without another
  user question. Authentication alone is not a blocker.
- Focus movement, scrolling, Back, and opening or closing screens, dialogs,
  drawers, and menus are local navigation, not external side effects.
- Never persist full `adb logcat`, network payloads, authentication headers, or
  a broad/raw UI dump. Scoped screenshots from the dev/stage package may be
  retained in the ignored evidence directory without another user question.
  Do not perform account-, server-, playback-progress-, or otherwise externally
  observable state changes unless the task body or a durable task comment
  explicitly authorizes them.
- Credentials, MFA, physical devices, and additional emulator startup always
  require the applicable explicit authorization.
- Allocate evidence before device work: runtime proves rendering,
  focus/navigation, integration, restoration, and liveness; deterministic tests
  prove non-observable defaults, classification, filtering, paging, and state
  transitions. Do not clear profiles, require fixtures, or add test-only
  semantics merely to duplicate passing deterministic proof.
- Required Smoke summary, report, and checklist artifacts must be non-empty.
- If the user grants a scoped exception during Smoke, record its exact boundary
  in a durable task comment before continuing. Recovery and compacted sessions
  must reuse that authorization instead of asking again.
- Mark a Smoke checklist item complete and report Smoke as passed only when
  returning the workflow's passing transition. Keep it unchecked when
  returning `needs_user_action`, `needs_changes`, or any other blocker/finding.
  Kent task state is authoritative over checklist text.
- Run `.kent/adapters/mobile/mobile-evidence-audit.sh
  <evidence-dir> <package-name>` before reporting success or a blocker.

## What it does

1. Reads the MCP Mobile Testing section in `AGENTS.md` to understand the process
2. **Acquire an emulator resource lock before touching any emulator/device**
   - Physical devices, including a real TV, are forbidden unless the task/user explicitly provides permission and an
     explicit serial for that physical device. Never rely on adb's default target selection.
   - Prefer already-running healthy emulators. Discover them with:
     ```bash
     EMULATORS=($(.kent/adapters/mobile/emulator-resource-lock.sh adb-emulators tv))
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
3. **Builds and preservation-installs a fresh `devDebug` APK before device testing**
   - An initial Smoke run always builds a fresh APK. A resumed run first reads
     the checkpoint. If it proves a successful install of the same APK SHA-256
     and the required authenticated state remains available, skip the duplicate
     build and install.
   - Before installation, observe authentication with the narrowest semantic
     check and store only `authenticated`, `unauthenticated`, or `unknown`.
   - Do not use Gradle `install*` tasks; they may invoke adb without the
     selected serial.
   - Build the APK, then use only the preservation adapter:
     ```bash
     test -n "$DEVICE_SERIAL"
     if pwd | grep -q '/.kent/worktrees/'; then
       ./tools/agentw :app:assembleDevDebug
     else
       ./gradlew :app:assembleDevDebug
     fi
     APK_PATH=app/build/outputs/apk/dev/debug/app-dev-debug.apk
     INSTALL_REPORT="$(
       .kent/adapters/mobile/android-apk-install-preserve \
         install-preserve \
         --serial "$DEVICE_SERIAL" \
         --package com.kino.puber.stage \
         --apk "$APK_PATH"
     )"
     printf '%s\n' "$INSTALL_REPORT" |
       jq -e '.outcome == "installed" and .destructive_action == false' >/dev/null
     LAUNCH_BOUNDARY_EPOCH="$(
       adb -s "$DEVICE_SERIAL" shell date +%s |
         tr -d '\r'
     )"
     [[ "$LAUNCH_BOUNDARY_EPOCH" =~ ^[0-9]{10,}$ ]]
     adb -s "$DEVICE_SERIAL" shell am force-stop com.kino.puber.stage
     adb -s "$DEVICE_SERIAL" shell am start -n com.kino.puber.stage/com.kino.puber.MainActivity
     ```
   - The adapter may use only a compatible `adb install -r`. It blocks
     downgrade, signer mismatch, unknown installed signer, transport failure,
     and install failure. Never uninstall, clear package data, permit downgrade,
     or replace a signer unless the task or a durable user comment separately
     authorizes that exact destructive boundary.
   - After launch, observe authentication again and store only the enum. Reuse
     a matching durable login authorization; never store credentials.
4. **Verifies Mobile MCP can see the locked serial**
   - Refresh and inspect the schema after Mobile MCP upgrades:
     ```bash
     ~/.kent/bin/kent-mcp-list mobile --schema --refresh --timeout 30000
     ```
   - Use only tools present in that schema. Do not call `list_modules` or
     `enable_module`; every invocation starts an ephemeral server, so
     process-local module state cannot configure later calls.
   - List devices:
     ```bash
     ~/.kent/bin/kent-mcp-call mobile.device \
       action=list \
       --output json
     ```
   - Confirm that the inventory contains `DEVICE_SERIAL`. Do not use
     process-local `action=set` or `action=get_target`.
   - Pass `platform=android` and `deviceId="$DEVICE_SERIAL"` to every
     target-specific Mobile MCP call.
   - If the Mobile schema does not accept explicit `deviceId` for an operation,
     use the exact platform adapter such as `adb -s` instead of implicit state.
   - If Mobile MCP cannot see the locked serial, complete with
     `needs_user_action`; never switch targets.
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

### Use bounded inspection:
- Call Mobile MCP only through `~/.kent/bin/kent-mcp-call`.
- Pass `platform=android` and the locked `deviceId` to every target-specific
  call.
- Every Mobile call other than device discovery must use `--quiet`,
  `--digest-output`, assertions, or bounded hash/marker extraction.
- Prefer `assert_visible` when the expected target is already known.
- When focus or the exact target is unknown, inspect only enough of the current
  authenticated screen to locate the task-scoped control. Do not ask merely
  because the UI is authenticated.
- Use `mobile.ui action=analyze --digest-output` for bounded structure checks.
- Use `--hash-matches '<bounded-regex>'` with required `--marker-present` when
  the check needs only opaque semantic identity sets.
- Use `mobile.screen action=capture maxWidth=800 maxHeight=1400` for
  task-scoped visual inspection when semantics are insufficient. Dev/stage
  captures may be retained as audited evidence; never retain a broad raw UI
  tree or production/unknown-environment screenshot.
- Derive directional routes from current focus and UI source/semantic order,
  execute the bounded route in one call, and verify the destination once.
  Replan on mismatch instead of spending one model turn per key.
- Use `needs_user_action` only when the required test would cross a prohibited
  side-effect/evidence boundary or a required external prerequisite is
  unavailable.

### Speed optimizations:
- Use `tap(hints: true)` with `--allow-mutate --quiet`.
- Use `mobile.ui action=wait` with an explicit serial and safe output mode
  instead of fixed sleeps.
- Prefer exact expected text or semantic keys. Use fuzzy actions only when they
  are present in the refreshed schema and bounded by the task scope.
- Prefer package-scoped `adb -s` crash/ANR/liveness checks to broad MCP logs.

Example expected-state assertion:

```bash
~/.kent/bin/kent-mcp-call mobile.ui \
  action=assert_visible \
  platform=android \
  deviceId="$DEVICE_SERIAL" \
  text="<expected-safe-element>" \
  --quiet
```

Example input:

```bash
~/.kent/bin/kent-mcp-call mobile.input \
  action=tap \
  platform=android \
  deviceId="$DEVICE_SERIAL" \
  text="<target>" \
  hints=true \
  --allow-mutate \
  --quiet
```

### Screen verification checklist:
- Loading → Content transition (use bounded `mobile.ui action=wait`)
- No known placeholder or `null` text (use bounded assertions)
- Expected elements present (use `assert_visible`)
- No package-scoped crash/ANR/liveness failure (use `adb -s`)
- TV remote navigation works (D-pad focus movement)

### TV-specific checks:
- Focus is visible on interactive elements
- D-pad navigation moves focus correctly between items
- Select/Enter activates the focused item
- Back button navigates back properly

## On Issues
- Uses bounded inspection to locate an unclear target before asking.
- Asks only when the required test would cross an explicit-authorization
  boundary or no safe task-scoped target can be established.
- Does not ask again when the task body or a durable task comment already
  authorizes the required boundary.
- Takes a screenshot only for a visual bug on a known non-sensitive screen
- Saves artifacts to build/test-artifacts/ on errors
- Keeps only package-scoped crash/ANR/liveness summaries
- Never saves full `adb logcat` output
- If a task requires a launch-time log boundary, validates the exact
  device-side command and parser first. Android shell `date` and `logcat`
  option forms are not GNU-portable; command or parsing failure must not be
  treated as an empty passing signal result.
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
