# Puber resource-lease Smoke procedure

Read `mobile-operation-policy.md` first. This procedure is source-qualified,
not an authorization to activate a pool, install tooling or use a device.
Use only the separately qualified Puber per-workspace private configuration
at `.kent/runtime/puber-mobile.json`; never fall back to the primary checkout
or another application's configuration.

## Native identity and pre-dispatch checkpoint

Resolve the rendered Task short ID through
`.kent/scripts/workflow-evidence-ledger identity --task SHORT --workspace ROOT`.
Compare its canonical project, Task, Session and execution workspace with
the current invocation. Environment identity values are comparison inputs,
not authority. The Puber adapter independently verifies this native readback.

Read the existing Smoke checkpoint through the configured checkpoint adapter.
Preserve unrelated `stage_data` and checklist/Task identity fields. A checkpoint
containing any legacy lock/token ownership requires separate compatibility
disposition; do not reinterpret or upgrade it.

Before acquisition, select one owner-private record path under
`.kent/runtime/SHORT/` and persist it with protocol `puber-resource-lease-v1`,
profile `smoke.persistent` and the verified native binding in `stage_data`.
Use that same record on retries. Do not create another record/run to work
around a lost response. The adapter itself persists the chosen run-directory
and configuration digest before dispatch.

Invoke `.kent/adapters/mobile/puber-resource-lease start --record RECORD
--project PROJECT --task NATIVE_TASK --session SESSION`. Retain the returned
public run-directory, profile, binding and resource identity in `stage_data`;
never include receipt contents or secrets. Checkpoint write failure blocks
all further device work and requires reconciliation of that exact attempt.

## Work and custody

Use the exact returned `ANDROID_SERIAL` and `RESOURCE_LEASE_RUN_DIRECTORY`.
Run the adapter's `check` with the same native identity/record immediately
before each product-owned build/install/diagnostic operation. Never discover
or substitute another endpoint. Install the fresh task dev APK only through
the preservation installer; revalidate APK hash and authenticated app state
before reusing a prior install.

Use `.kent/adapters/android/android-tools` for native UI and DPAD operations.
Set `PUBER_SMOKE_LEASE_RECORD` and the verified native project/Task/Session
comparison inputs. Its wrapper rechecks native identity and passes the exact
run environment to the installed shared facade. Keep the explicit
`device_serial` argument equal to that endpoint. No UI, DPAD, sanitization or
evidence implementation is duplicated in Puber.

Test visible behavior, focus/navigation, restoration and liveness within the
approved scenario. The new persistent TV has no inherited login. Missing
authentication authority blocks the affected scenario, not unrelated source
qualification. Follow the authoritative operation policy for account effects,
credentials, observations and sanitized evidence.

## Finish and recovery

On every exit, stop product-owned operations synchronously, then call `finish`
with the same record and native identity. Confirm terminal resource cleanup
before marking Smoke passed or its checkpoint complete. Failed cleanup,
lease loss or uncertain command stop cannot produce PASS. Preserve checkpoint,
partial outputs and private run records; report exact unresolved disposition.

Repeated start may reuse only the client's supported exact live binding.
Cross-Session continuation never transfers ownership implicitly: settle the
original run and obtain a freshly authorized binding, then revalidate install
and app state. Missing, multiple, foreign, live or uncertain recovery records
block automatic acquisition replay. Never use Session-oriented `check` for a
Release command lease.

The acquisition deadline is the installed client's 300 seconds. Do not
automatically retry or emulate the former 900-second Release wait.
