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

- Smoke lease/device execution is blocked until separately approved adoption
  and source proof of a compatible adapter implementing the KEN-6 mobile Smoke
  contract/template pinned at Kit commit
  `0ba82a89e7b38a1f70defe6ec286e33dd7f6ba8d`. The currently tracked Puber
  adapter is not compatible or ready; this source-only change does not install,
  qualify, or roll out a replacement. Stop before lease status/acquisition,
  emulator discovery, or device work. The examples describe the target
  contract; deterministic tests use synthetic stubs only.
- Use `.kent/scripts/workflow-checkpoint` to maintain the canonical ignored
  `.kent/runtime/<TASK-ID>/smoke-checkpoint.json`. Reconcile and persist its
  complete state before repeating build, install, launch, navigation, mutation,
  or evidence work, and before every workflow transition. Keep the verified
  Task binding and lease fields together in `stage_data`; on handoff pass only
  the ignored checkpoint path, never its JSON or token. A later Session must
  repeat native identity readback and checkpoint validation before resuming.
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
2. **Establish Task ownership and reconcile a lease before device work**
   - The adoption gate in Evidence Safety applies before adapter status,
     acquisition, emulator discovery, or any other device operation. The
     checked-in Puber adapter is not compatible; do not execute the examples
     against it or describe this change as adapter adoption.
   - Resolve identity from the Task short ID rendered for this Smoke invocation.
     Do not infer Task or Session identity from environment variables. In
     particular, `KENT_TASK_ID` and `KENT_SESSION_ID` are adapter inputs, not
     assumed Kent exports. Capture only the required fields; never print or
     persist the full native Task response:
     ```bash
     # smoke-lease-case: identity
     set -euo pipefail
     TASK_SHORT_ID="${SMOKE_TASK_SHORT_ID:?Set this to the Task short ID rendered for this Smoke run}"
     [[ "$TASK_SHORT_ID" =~ ^[A-Z][A-Z0-9]*-[0-9]+$ ]]
     TASK_FIELDS="$(kent task show "$TASK_SHORT_ID" --json |
       jq -er '
         [.summary.id, .summary.short_id]
         | if all(.[]; type == "string" and length > 0)
           then @tsv
           else error("native Task identity is incomplete")
           end
       ')"
     IFS=$'\t' read -r TASK_NATIVE_ID VERIFIED_SHORT_ID <<<"$TASK_FIELDS"
     [[ -n "$TASK_NATIVE_ID" && -n "$VERIFIED_SHORT_ID" ]]
     [[ "$VERIFIED_SHORT_ID" == "$TASK_SHORT_ID" ]]
     SESSION_ID="$(kent session-id)"
     [[ "$SESSION_ID" =~ ^[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$ ]]
     unset KENT_TASK_ID KENT_SESSION_ID
     export KENT_TASK_ID="$VERIFIED_SHORT_ID" KENT_SESSION_ID="$SESSION_ID"
     LOCK_ADAPTER=".kent/adapters/mobile/emulator-resource-lock.sh"
     CHECKPOINT_TOOL=".kent/scripts/workflow-checkpoint"
     CHECKPOINT_JSON="$("$CHECKPOINT_TOOL" read --stage smoke --task "$TASK_SHORT_ID")"
     jq -e --arg short "$TASK_SHORT_ID" --arg native "$TASK_NATIVE_ID" '
       .task_short_id == $short
       and ((.stage_data // {}) | type == "object")
       and (.stage_data.task_short_id == null or .stage_data.task_short_id == $short)
       and (.stage_data.task_native_id == null or .stage_data.task_native_id == $native)
       and (.stage_data.lease_owner_id == null
         or .stage_data.lease_owner_id == $short
         or .stage_data.lease_owner_id == $native)
       and ((.stage_data.lock_resource // "") | type == "string")
       and ((.stage_data.lock_token // "") | type == "string")
     ' <<<"$CHECKPOINT_JSON" >/dev/null
     LOCK_RESOURCE="$(jq -r '.stage_data.lock_resource // ""' <<<"$CHECKPOINT_JSON")"
     LOCK_TOKEN="$(jq -r '.stage_data.lock_token // ""' <<<"$CHECKPOINT_JSON")"
     LOCK_OWNER_ID="$(jq -r '.stage_data.lease_owner_id // ""' <<<"$CHECKPOINT_JSON")"
     [[ -n "$LOCK_RESOURCE" || -z "$LOCK_TOKEN" ]]
     [[ -z "$LOCK_RESOURCE" || "$LOCK_RESOURCE" =~ ^[A-Za-z0-9._=-]+$ ]]
     [[ -z "$LOCK_TOKEN" || "$LOCK_TOKEN" =~ ^[A-Za-z0-9._=-]+$ ]]

     parse_bare_token() {
       local raw="$1" token
       [[ "$raw" == *$'\n' ]] || return 1
       token="${raw%$'\n'}"
       [[ -n "$token" && "$token" != *$'\n'* && "$token" != *$'\r'* ]]
       [[ "$token" =~ ^[A-Za-z0-9._=-]+$ ]] || return 1
       printf '%s' "$token"
     }

     persist_lease() {
       local state="$1" owner="$2" resource="$3" token="$4" next_action="$5"
       local current payload
       current="$("$CHECKPOINT_TOOL" read --stage smoke --task "$TASK_SHORT_ID")"
       payload="$(jq -ce \
         --arg native "$TASK_NATIVE_ID" \
         --arg short "$TASK_SHORT_ID" \
         --arg owner "$owner" \
         --arg resource "$resource" \
         --arg token "$token" \
         --arg state "$state" \
         --arg next "$next_action" '
           if .task_short_id != $short then error("checkpoint Task mismatch") else . end
           | if ((.stage_data // {}) | type) != "object" then error("checkpoint stage_data invalid") else . end
           | if (.stage_data.task_native_id != null and .stage_data.task_native_id != $native) then error("checkpoint native Task mismatch") else . end
           | if (.stage_data.task_short_id != null and .stage_data.task_short_id != $short) then error("checkpoint short Task mismatch") else . end
           | if (.stage_data.lease_owner_id != null and .stage_data.lease_owner_id != $owner) then error("checkpoint lease owner mismatch") else . end
           | if (.stage_data.lock_resource != null and .stage_data.lock_resource != "" and .stage_data.lock_resource != $resource) then error("checkpoint resource mismatch") else . end
           | if (.stage_data.lock_token != null and .stage_data.lock_token != "" and .stage_data.lock_token != $token) then error("checkpoint token mismatch") else . end
           | if ($owner != $short and $owner != $native) then error("lease owner is not the verified Task") else . end
           | .stage_data = ((.stage_data // {}) + {
               task_native_id: $native,
               task_short_id: $short,
               lease_owner_id: $owner,
               lock_resource: $resource,
               lock_token: $token,
               lease_state: $state
             })
           | .next_action = $next
         ' <<<"$current")"
       printf '%s\n' "$payload" |
         "$CHECKPOINT_TOOL" write --stage smoke --task "$TASK_SHORT_ID" >/dev/null
     }

     read_status_owner() {
       local resource="$1" raw status owner status_resource status_token
       raw="$("$LOCK_ADAPTER" status "$resource" && printf '\034')" || return 3
       raw="${raw%$'\034'}"
       [[ "$raw" == $'unlocked\n' ]] && return 2
       [[ "$raw" == locked$'\n'* && "$raw" == *$'\n' ]] || return 1
       status="${raw%$'\n'}"
       [[ "$status" != *$'\n\n'* ]] || return 1
       [[ "${status: -1}" != $'\n' ]] || return 1
       owner="$(sed -n 's/^  task_id=//p' <<<"$status")"
       status_resource="$(sed -n 's/^  resource=//p' <<<"$status")"
       status_token="$(sed -n 's/^  token=//p' <<<"$status")"
       [[ -n "$owner" && "$owner" != *$'\n'* ]] || return 1
       [[ "$status_resource" == "$resource" ]] || return 1
       [[ "$status_token" == "<redacted>" ]] || return 1
       [[ "$owner" =~ ^[A-Z][A-Z0-9]*-[0-9]+$ ||
          "$owner" =~ ^task-[[:xdigit:]]{8}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{4}-[[:xdigit:]]{12}$ ]] || return 1
       printf '%s' "$owner"
     }
     ```
     The same cleanup handler must be installed before any device work, but
     only after a lease is held and checkpointed:
     ```bash
     # smoke-lease-case: release
     cleanup_lease() {
       [[ "${LEASE_HELD:-false}" == true ]] || return 0
       local release_status=0 status_raw
       if "$LOCK_ADAPTER" release "$LOCK_RESOURCE" "$LOCK_TOKEN" >/dev/null; then
         :
       else
         release_status=$?
       fi
       if ! status_raw="$("$LOCK_ADAPTER" status "$LOCK_RESOURCE" && printf '\034')"; then
         persist_lease held "$LOCK_OWNER_ID" "$LOCK_RESOURCE" "$LOCK_TOKEN" \
           "Resolve Smoke lease cleanup; status readback failed" || true
         echo "Smoke lease cleanup unresolved: status readback failed" >&2
         return 1
       fi
       status_raw="${status_raw%$'\034'}"
       if [[ "$status_raw" != $'unlocked\n' ]]; then
         persist_lease held "$LOCK_OWNER_ID" "$LOCK_RESOURCE" "$LOCK_TOKEN" \
           "Resolve Smoke lease cleanup; do not continue" || true
         echo "Smoke lease cleanup unresolved: resource is not verified unlocked" >&2
         return 1
       fi
       if ! persist_lease released "$LOCK_OWNER_ID" "$LOCK_RESOURCE" "$LOCK_TOKEN" \
         "Continue only after verified lease release"; then
         echo "Smoke lease cleanup unresolved: checkpoint update failed" >&2
         return 1
       fi
       LEASE_HELD=false
       if (( release_status != 0 )); then
         echo "Release command failed, but exact-resource readback is unlocked" >&2
       fi
     }

     on_smoke_exit() {
       local smoke_status=$?
       trap - EXIT
       if cleanup_lease; then
         exit "$smoke_status"
       fi
       exit 1
     }
     ```
     A fresh Smoke run must first have a valid canonical checkpoint initialized
     from its actual completed/remaining checklist and next action. If the
     checkpoint is missing, malformed, bound to another Task, or internally
     conflicting, stop; do not replace it with an empty checkpoint or acquire
     a lease.
   - Extend only the existing checkpoint `stage_data`, preserving its other
     fields and top-level `task_short_id`. Store
     `task_native_id`, `task_short_id`, `lease_owner_id`, `lock_resource`, and
     `lock_token` together. Reject conflicting identity or owner fields.
     Enrich missing legacy Task identity fields only after the native readback
     above matches the checkpoint's existing short ID. New leases use the
     verified short ID as owner. Accept a legacy native-ID owner only when
     redacted status proves that exact native-ID/short-ID mapping; retain that
     owner ID unchanged for resume and release.
   - Reconcile before every acquisition or resume:
     - **Resource and token both present:** inspect redacted status for that
       exact resource. A locked record must have one valid owner/resource record
       for this Task. Resume only that pair with `resume`; require its single
       bare-token response to equal the checkpoint token. A foreign, malformed,
       duplicate, or mismatched record, failed resume, or wrong token blocks.
       Do not fall through to discovery or switch targets. The pinned contract's
       identity-bound behavior for `resume` when the lock is absent remains in
       force.
     - **Known resource, missing token (including discarded acquisition
       stdout):** inspect that exact resource with `status`. Only one valid
       redacted owner record for the verified Task permits `resume-owned`.
       Require one valid bare-token response, then immediately persist it.
       A foreign, malformed, duplicate, resource-mismatched, or unlocked status
       blocks this known-resource recovery.
     - **No resource and no token:** inventory only the eligible resources for
       this Smoke form factor and inspect each with redacted `status`. Recover
       only when exactly one candidate proves the same Task; use
       `resume-owned`, then immediately persist the returned token. Multiple
       same-Task candidates are ambiguous and block. Zero proven same-Task
       candidates proceeds to ordinary fresh acquisition. Foreign, malformed,
       or duplicate ownership is never proof; a sole occupied resource is not
       adoptable.
     - **Token without resource:** inconsistent checkpoint; block.
   - For a retained resource/token pair, resume exactly that pair after the
     current Task and Session identity is verified:
     ```bash
     # smoke-lease-case: resume
     if [[ -n "$LOCK_RESOURCE" && -n "$LOCK_TOKEN" ]]; then
       if STATUS_OWNER="$(read_status_owner "$LOCK_RESOURCE")"; then
         [[ "$STATUS_OWNER" == "$TASK_SHORT_ID" || "$STATUS_OWNER" == "$TASK_NATIVE_ID" ]]
         [[ -z "$LOCK_OWNER_ID" || "$LOCK_OWNER_ID" == "$STATUS_OWNER" ]]
         LOCK_OWNER_ID="$STATUS_OWNER"
       else
         STATUS_RESULT=$?
         [[ "$STATUS_RESULT" -eq 2 ]] || exit 1
         LOCK_OWNER_ID="${LOCK_OWNER_ID:-$TASK_SHORT_ID}"
       fi
       export KENT_TASK_ID="$LOCK_OWNER_ID" KENT_SESSION_ID="$SESSION_ID"
       LOCK_OUTPUT="$("$LOCK_ADAPTER" resume "$LOCK_RESOURCE" "$LOCK_TOKEN" && printf '\034')"
       LOCK_OUTPUT="${LOCK_OUTPUT%$'\034'}"
       RESUMED_TOKEN="$(parse_bare_token "$LOCK_OUTPUT")"
       [[ "$RESUMED_TOKEN" == "$LOCK_TOKEN" ]]
       persist_lease held "$LOCK_OWNER_ID" "$LOCK_RESOURCE" "$LOCK_TOKEN" \
         "Continue Smoke on the retained resource"
       LEASE_HELD=true
       DEVICE_SERIAL="$LOCK_RESOURCE"
       trap on_smoke_exit EXIT
     fi
     ```
     If the exact pair cannot be proven or resumed, stop; never fall through
     to discovery or fresh acquisition.
   - For a known resource with a missing token, or an interruption that lost
     acquire stdout, recover only from a valid same-Task status record:
     ```bash
     # smoke-lease-case: recovery
     if [[ -n "$LOCK_RESOURCE" && -z "$LOCK_TOKEN" ]]; then
       STATUS_OWNER="$(read_status_owner "$LOCK_RESOURCE")"
       [[ "$STATUS_OWNER" == "$TASK_SHORT_ID" || "$STATUS_OWNER" == "$TASK_NATIVE_ID" ]]
       [[ -z "$LOCK_OWNER_ID" || "$LOCK_OWNER_ID" == "$STATUS_OWNER" ]]
       LOCK_OWNER_ID="$STATUS_OWNER"
       export KENT_TASK_ID="$LOCK_OWNER_ID" KENT_SESSION_ID="$SESSION_ID"
       LOCK_OUTPUT="$("$LOCK_ADAPTER" resume-owned "$LOCK_RESOURCE" && printf '\034')"
       LOCK_OUTPUT="${LOCK_OUTPUT%$'\034'}"
       LOCK_TOKEN="$(parse_bare_token "$LOCK_OUTPUT")"
       persist_lease held "$LOCK_OWNER_ID" "$LOCK_RESOURCE" "$LOCK_TOKEN" \
         "Continue Smoke after owned-resource recovery"
       LEASE_HELD=true
       DEVICE_SERIAL="$LOCK_RESOURCE"
       trap on_smoke_exit EXIT
     elif [[ -z "$LOCK_RESOURCE" && -z "$LOCK_TOKEN" ]]; then
       declare -A SEEN_EMULATORS=()
       EMULATORS=()
       if [[ -n "${AUTHORIZED_PHYSICAL_SERIAL:-}" ]]; then
         [[ "$AUTHORIZED_PHYSICAL_SERIAL" =~ ^[A-Za-z0-9._=-]+$ ]]
         EMULATORS=("$AUTHORIZED_PHYSICAL_SERIAL")
       else
         EMULATOR_OUTPUT="$("$LOCK_ADAPTER" adb-emulators tv)"
         if [[ -n "$EMULATOR_OUTPUT" ]]; then
           mapfile -t EMULATORS <<<"$EMULATOR_OUTPUT"
         fi
       fi
       for candidate in "${EMULATORS[@]}"; do
         [[ "$candidate" =~ ^[A-Za-z0-9._=-]+$ && -z "${SEEN_EMULATORS[$candidate]+x}" ]]
         SEEN_EMULATORS["$candidate"]=1
       done
       SAME_TASK_RESOURCES=()
       SAME_TASK_OWNERS=()
       for candidate in "${EMULATORS[@]}"; do
         if STATUS_OWNER="$(read_status_owner "$candidate")"; then
           if [[ "$STATUS_OWNER" == "$TASK_SHORT_ID" || "$STATUS_OWNER" == "$TASK_NATIVE_ID" ]]; then
             SAME_TASK_RESOURCES+=("$candidate")
             SAME_TASK_OWNERS+=("$STATUS_OWNER")
           fi
         else
           STATUS_RESULT=$?
           [[ "$STATUS_RESULT" -eq 1 || "$STATUS_RESULT" -eq 2 ]] || exit 1
         fi
       done
       if (( ${#SAME_TASK_RESOURCES[@]} > 1 )); then
         echo "Smoke lease recovery is ambiguous: multiple same-Task resources" >&2
         exit 1
       elif (( ${#SAME_TASK_RESOURCES[@]} == 1 )); then
         LOCK_RESOURCE="${SAME_TASK_RESOURCES[0]}"
         LOCK_OWNER_ID="${SAME_TASK_OWNERS[0]}"
         export KENT_TASK_ID="$LOCK_OWNER_ID" KENT_SESSION_ID="$SESSION_ID"
         LOCK_OUTPUT="$("$LOCK_ADAPTER" resume-owned "$LOCK_RESOURCE" && printf '\034')"
         LOCK_OUTPUT="${LOCK_OUTPUT%$'\034'}"
         LOCK_TOKEN="$(parse_bare_token "$LOCK_OUTPUT")"
         persist_lease held "$LOCK_OWNER_ID" "$LOCK_RESOURCE" "$LOCK_TOKEN" \
           "Continue Smoke after same-Task resource recovery"
         LEASE_HELD=true
         DEVICE_SERIAL="$LOCK_RESOURCE"
         trap on_smoke_exit EXIT
       fi
     elif [[ -z "$LOCK_RESOURCE" || -z "$LOCK_TOKEN" ]]; then
       echo "Smoke lease checkpoint contains an inconsistent resource/token pair" >&2
       exit 1
     fi
     ```
     A zero-match inventory leaves the resource unset for fresh acquisition.
     Malformed/foreign metadata is never adopted; a valid foreign owner is a
     non-match, and an ambiguous multiple same-Task match blocks.
   - Retained-resource and no-resource recovery both use the current Session
     ID obtained above. On interruption before checkpoint persistence, a later
     Session re-resolves identity and uses the bounded status inventory; it
     never guesses from sole occupancy. Before a workflow handoff, reconcile
     and persist the complete checkpoint and next action. Carry only the
     canonical ignored checkpoint reference; never put the token or checkpoint
     JSON in comments, transition text, or reports.
   - Preserve the KEN-6 caller-TTL contract exactly: at equality (`age <= TTL`)
     a competing lock remains busy; replacement is allowed only when age is
     **strictly greater** than the acquiring caller's explicit TTL. TTL is not
     stored in owner metadata. Exact-owner `resume` and `resume-owned` may
     refresh a still-present lease even after that age threshold. The first
     guarded operation wins: resume-first refreshes; replacement-first makes
     old owner/token recovery fail. `resume-owned` never creates an absent lock
     or reclaims a foreign/unknown owner.
   - Output contracts are distinct: `acquire` returns one bare token;
     `acquire-any` returns exactly `resource=...` followed by `token=...`.
     Filter emulator candidates by TV form factor first, reject empty,
     duplicate, malformed, or unexpected inventory, and pass only eligible
     resources to `acquire-any`. Reject missing, empty, duplicate, malformed,
     or extra output records, and verify its selected resource is eligible.
     Persist the selected resource and token immediately, before setting the
     device target or performing device work:
     ```bash
     # smoke-lease-case: acquire
     if [[ "${LEASE_HELD:-false}" != true ]]; then
       if [[ -n "${AUTHORIZED_PHYSICAL_SERIAL:-}" ]]; then
         # Set only after explicit task/user authorization naming this serial.
         LOCK_RESOURCE="$AUTHORIZED_PHYSICAL_SERIAL"
         [[ "$LOCK_RESOURCE" =~ ^[A-Za-z0-9._=-]+$ ]]
         LOCK_OUTPUT="$("$LOCK_ADAPTER" acquire "$LOCK_RESOURCE" 900 7200 && printf '\034')"
         LOCK_OUTPUT="${LOCK_OUTPUT%$'\034'}"
         LOCK_TOKEN="$(parse_bare_token "$LOCK_OUTPUT")"
       else
         if [[ ${#EMULATORS[@]} -eq 0 ]]; then
           echo "No eligible TV emulator is available; do not use an implicit target" >&2
           exit 1
         fi
         LOCK_OUTPUT="$("$LOCK_ADAPTER" acquire-any "${EMULATORS[@]}" -- 900 7200 && printf '\034')"
         LOCK_OUTPUT="${LOCK_OUTPUT%$'\034'}"
         [[ "$LOCK_OUTPUT" == *$'\n' ]]
         ACQUIRE_RECORDS="${LOCK_OUTPUT%$'\n'}"
         [[ "$ACQUIRE_RECORDS" == *$'\n'* ]]
         RESOURCE_RECORD="${ACQUIRE_RECORDS%%$'\n'*}"
         TOKEN_RECORD="${ACQUIRE_RECORDS#*$'\n'}"
         [[ "$TOKEN_RECORD" != *$'\n'* ]]
         [[ "$RESOURCE_RECORD" == resource=* && "$TOKEN_RECORD" == token=* ]]
         LOCK_RESOURCE="${RESOURCE_RECORD#resource=}"
         LOCK_TOKEN="${TOKEN_RECORD#token=}"
         [[ "$LOCK_RESOURCE" =~ ^[A-Za-z0-9._=-]+$ ]]
         [[ "$LOCK_TOKEN" =~ ^[A-Za-z0-9._=-]+$ ]]
         [[ -n "${SEEN_EMULATORS[$LOCK_RESOURCE]+x}" ]]
       fi
       LOCK_OWNER_ID="$TASK_SHORT_ID"
       persist_lease held "$LOCK_OWNER_ID" "$LOCK_RESOURCE" "$LOCK_TOKEN" \
         "Use the acquired Smoke resource"
       LEASE_HELD=true
       DEVICE_SERIAL="$LOCK_RESOURCE"
       trap on_smoke_exit EXIT
     fi
     ```
     `persist_lease` must re-read and validate the checkpoint, merge only the
     lease fields into `stage_data`, and pipe the resulting complete JSON object
     to `.kent/scripts/workflow-checkpoint write --stage smoke --task
     "$TASK_SHORT_ID"`; do not log its input or output. Apply the same strict
     single bare-token parser to `acquire` (explicit serial only),
     `resume`, and `resume-owned`; never parse bare `acquire` as acquire-any
     records. A physical serial requires explicit task/user authorization and
     an explicit serial. Starting an additional emulator requires explicit
     permission and suitable capacity. With no safe eligible target, return
     `needs_user_action` and explain the unavailable or occupied resource;
     never use adb's default target.
   - Keep tokens only in the ignored checkpoint and process memory; do not
     echo them, enable shell tracing, or copy them to evidence. Before any
     device operation, require a non-empty `DEVICE_SERIAL` equal to the
     persisted resource and always pass it explicitly as `adb -s
     "$DEVICE_SERIAL"`.
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
9. **Releases and verifies the exact resource**
   - Use the same cleanup function for explicit release and the exit trap.
     Capture release success/failure without exposing the token, then always
     read back `status` for the exact resource. Only one exact `unlocked`
     status line proves cleanup; command success by itself does not. If the
     status command fails or reports locked/malformed output, cleanup is
     unresolved even when release returned success: preserve the held
     checkpoint and token, do not mark released, do not switch resources, and
     do not report Smoke success. Reconcile the same resource under the
     separately approved compatible adapter before any later action.
   - After verified unlocked readback, mark `lease_state=released` in
     `stage_data` and persist before the next workflow transition. Keep the
     token only in that ignored checkpoint until Smoke is fully reported.
   - The exit trap is registered immediately after acquire/resume and checkpoint
     persistence, before any device work. Explicit release later calls the same
     cleanup function and disables the trap only after verified release:
     ```bash
     # smoke-lease-case: explicit-release
     if ! cleanup_lease; then
       exit 1
     fi
     trap - EXIT
     ```
     The cleanup trap preserves the original Smoke exit status after verified
     cleanup; unresolved cleanup returns failure without deleting the held
     checkpoint. A successful release followed by locked or failed status
     remains unresolved.

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
