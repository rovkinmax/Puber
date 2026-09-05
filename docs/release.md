# Release

## Kent-managed release preparation

Release preparation runs through the non-default `Puber Release` workflow.
The source graph is repository documentation only; it does not apply or
relink a live Kent workflow.

Preparation is ordered and fail-closed:

1. `prepare` creates or reuses `release/<version>` at the exact current
   `origin/master` commit. It creates neither a version commit nor a pull
   request.
2. `profile_generation` discovers an eligible TV emulator, acquires its
   project lease, and runs the mock-backed profile generator with that exact
   serial.
3. `finalize_release` validates production packaging, applies or reuses the
   version bump, and creates one additive preparation commit. It stages
   `app/build.gradle.kts` and only generated profile files whose bytes changed.
4. Compliance and Ship validate the digest-bound preparation checkpoint against
   the exact branch head before the release PR is created or updated.

The release PR is the only preparation change set. An identical generator
output creates no synthetic profile diff. Tag and GitHub Release publication
remain approval-gated and happen only after the release PR is merged.

## Profile generation

The release profile stage requires a running **Android TV emulator**, an exact
ADB serial, and a lease acquired through:

This standalone recipe is **fresh acquisition only**, not checkpoint recovery.
If a release checkpoint already exists, resume only the descriptor-safe workflow
Script; it owns checkpoint admission, lease recovery and rollback. Do not use
this block (or standalone `resume-owned`) to recover that checkpoint.

```bash
set -euo pipefail
: "${KENT_TASK_ID:?Set KENT_TASK_ID to the immutable Kent task UUID}"
: "${DEVICE_SERIAL:?Select an exact running TV emulator serial}"
[[ "$DEVICE_SERIAL" =~ ^emulator-[A-Za-z0-9._=-]+$ ]] || exit 1
export KENT_TASK_ID
LOCK_ADAPTER="$PWD/.kent/adapters/mobile/emulator-resource-lock.sh"
# Preserve complete inventory lines, including trailing blank lines.
INVENTORY_ACK="$("$LOCK_ADAPTER" adb-emulators tv; status=$?; printf '.'; exit "$status")"
INVENTORY="${INVENTORY_ACK%.}"
[[ -n "$INVENTORY" && "$INVENTORY" == *$'\n' ]] || exit 1
INVENTORY="${INVENTORY%$'\n'}"
EMULATORS=()
SELECTED=0
while IFS= read -r serial; do
  [[ "$serial" =~ ^emulator-[A-Za-z0-9._=-]+$ ]] || exit 1
  for existing in "${EMULATORS[@]}"; do
    [[ "$existing" != "$serial" ]] || exit 1
  done
  EMULATORS+=("$serial")
  if [[ "$serial" == "$DEVICE_SERIAL" ]]; then SELECTED=$((SELECTED + 1)); fi
done <<< "$INVENTORY"
[[ "$SELECTED" == 1 ]] || exit 1
LEASE_HELD=0
LEASE_TOKEN=""
parse_token() {
  local response="$1"
  [[ "$response" == *$'\n' ]] || return 1
  response="${response%$'\n'}"
  [[ "$response" =~ ^[A-Za-z0-9][A-Za-z0-9._-]*$ ]] || return 1
  LEASE_TOKEN="$response"
}
release_emulator() {
  local status=$?
  trap - EXIT
  if [[ "$LEASE_HELD" == 1 ]]; then
    if [[ -z "$LEASE_TOKEN" ]]; then
      # Recovery is only for THIS successful acquire's malformed acknowledgement.
      RECOVERY_ACK="$("$LOCK_ADAPTER" resume-owned "$DEVICE_SERIAL"; result=$?; printf '.'; exit "$result")" || exit 1
      parse_token "${RECOVERY_ACK%.}" || exit 1
    fi
    "$LOCK_ADAPTER" release "$DEVICE_SERIAL" "$LEASE_TOKEN" || exit 1
    LEASE_HELD=0
  fi
  exit "$status"
}
trap release_emulator EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
ACQUIRE_ACK="$("$LOCK_ADAPTER" acquire "$DEVICE_SERIAL" 900 7200; status=$?; printf '.'; exit "$status")" || exit 1
LEASE_HELD=1
parse_token "${ACQUIRE_ACK%.}" || exit 1
ANDROID_SERIAL="$DEVICE_SERIAL" ./tools/generate-baseline-profile.sh
```

Inventory admission precedes every lease effect. A successful fresh acquire is
owned by cleanup before acknowledgement parsing. Only a malformed successful
acknowledgement permits cleanup to recover the same selected resource through
`resume-owned`; every success/failure/cancellation releases its exact token.
A failed recovery/release is a blocking error, never reported as released.
The wait limit is 900 seconds and TTL 7,200 seconds. Physical devices and
implicit device selection are forbidden.

The hermetic generator uses the benchmark-owned `MockWebServer` and synthetic
authentication. It does not use KinoPub/TMDB credentials, a token cache, OAuth,
the production backend, DNS, or external network access:

The generator invocation is inside the leased block above.

In a managed worktree the helper invokes `tools/agentw`; in the primary
checkout use `./gradlew :app:generateBaselineProfile` directly. Successful
generation updates only:

```text
app/src/main/generated/baselineProfiles/baseline-prof.txt
app/src/main/generated/baselineProfiles/startup-prof.txt
```

The profile stage validates the mock/network-isolation result, output files,
candidate branch identity, and changed-path allowlist before releasing the
emulator. It records sanitized output hashes and the generation result in the
task-local checkpoint; lease tokens and credentials are never persisted.

## Production packaging

Before finalization creates the preparation commit, run the production profile
and release packaging checks:

```bash
env -i HOME="$HOME" PATH="$PATH" JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" \
  PUBER_RELEASE_SIGNING_MODE=debug_validation ./tools/agentw :app:compileProdReleaseArtProfile
env -i HOME="$HOME" PATH="$PATH" JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" \
  PUBER_RELEASE_SIGNING_MODE=debug_validation ./tools/agentw :app:assembleProdRelease
```

Use `./gradlew` instead of `./tools/agentw` in the primary checkout. Release
builds do not generate profiles automatically:

```kotlin
baselineProfile {
    automaticGenerationDuringBuild = false
}
```

They package the checked-in files listed above. A packaging, instrumentation,
lease, mock, network-isolation, output, branch, or checkpoint error blocks
compliance and cannot reach merge, tag, or GitHub Release publication.

## Recovery and idempotent retry

The profile operation writes
`.kent/runtime/<task-short-id>/release-profile-checkpoint.json` atomically
with mode `0600`. On interruption or infrastructure failure it restores the
pre-generation profile bytes, releases the lease, and records a failed
checkpoint. A retry reuses the exact task, operation, branch, base, and
candidate identity; when ownership metadata is present it uses the adapter's
`resume-owned` operation before cleanup. It never force-pushes, overwrites
unrelated user changes, or creates a duplicate release PR.

Do not manually remove a checkpoint or release branch to recover a run. Resume
the Kent release stage after the external cause is fixed. Cleanup removes only
the validated terminal checkpoint after publication evidence is sealed.

## GitHub CI boundaries

Pull-request CI (`.github/workflows/pr-checks.yml`) is source-only and runs
Java 21 `detekt`, unit tests, and `assembleProdDebug`. It does not sign,
publish, create tags, or generate release profiles.

Post-merge release automation (`.github/workflows/release.yml`) is tag-push
only:

```bash
git tag v1.2.0
git push origin v1.2.0
```

Preflight and build-and-prepare alone receive `PUBER_RELEASE_SIGNING_MODE=production`
and exactly `RELEASE_KEYSTORE_BASE64`, `STOREPASS`, `KEYALIAS`; key password is
`STOREPASS` and `KEYPASS` is forbidden. Missing secrets fail before Gradle. Both
the alias and APK signer must match the production pin below. A trap installed
before decoding removes transient `app/release.jks`, signing properties and
`app/build` on every exit. Only the verified APK/checksum survives in
`release-artifacts`. Upload receives no signing inputs; only GitHub Release
creation receives `GH_TOKEN=${{ github.token }}`. No secrets are job-scoped.
Production credentials never enter a task or release-preparation worktree. There is no manual GitHub Actions dispatch path;
tags must only be pushed after the approved release PR has merged.

## Duration and verification

Observed duration from one clean TV-emulator run: profile generation
**830.46 seconds**; production packaging **296.78 seconds** total
(`compileProdReleaseArtProfile`: 230.18 seconds,
`assembleProdRelease`: 66.60 seconds). This is an observation, not an SLO or
an expected upper bound. It is separate from the 900-second lease wait limit
and 7,200-second lease TTL.

For a local release candidate, the deterministic packaging commands are:

```bash
env -i HOME="$HOME" PATH="$PATH" JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" \
  PUBER_RELEASE_SIGNING_MODE=debug_validation ./tools/agentw :app:compileProdReleaseArtProfile
env -i HOME="$HOME" PATH="$PATH" JAVA_HOME="$JAVA_HOME" ANDROID_HOME="$ANDROID_HOME" \
  PUBER_RELEASE_SIGNING_MODE=debug_validation ./tools/agentw :app:assembleProdRelease
```

For a manual profile refresh, acquire the exact TV-emulator lease first,
export `ANDROID_SERIAL`, run the helper, inspect the mock/network-isolation
diagnostics, and release the lease on every exit path. Never run profile or
connected instrumentation tasks with an implicit ADB target.

## Revision 90 signing and checkpoint contract

`puber_release_profile_checkpoint_v2` uses one descriptor-relative `O_NOFOLLOW`
store in `workflow-puber-release-intent` for read/write and receipt-matched
terminal deletion. Every parent is current-user owned, non-writable by others;
the final regular single-link file is mode `0600`. Revision-89/v1 checkpoints
are stale: never delete or reuse them. No path or receipt is a graph carrier.

Pre-PR packaging explicitly uses `debug_validation`: only tracked
`app/debug.jks`, Git blob `109bfc97479cd17724a3ba75a8d3b5ca9df22f52`, SHA-256
`91c981cee70e84338ff5c7938ae8110f8c0a2378a7a936c245133b4d5ee7620f`, certificate
`6b29181257cb520329553691b7a48c9b1123899950105127b3dd67012c49a83e`. The APK
must have exactly this signer. Reject signing properties, release keystores
and all production signing environment inputs before validation. Use the
allowlisted environment above; never stage, retain, upload or publish the APK.
Discard the transient APK and build output after validation, including failure.

The closed `puber_release_preparation_report_v2` preserves all prior fields
and adds `packaging_signing_mode="debug_validation"`,
`packaging_artifact_publishable=false`, `production_signing_used=false`,
`validation_signing_source="tracked_app_debug_jks"`, `validation_signing_blob_oid`,
`validation_signing_sha256`, `validation_signer_certificate_sha256` and
`packaging_artifact_signer_certificate_sha256` bound to those exact identities.
Compliance/Ship/Publish/Cleanup reject v1, unknown/missing fields and drift.
A validation report proves packaging/PR readiness, not publishable artifacts.

Production mode has no debug fallback. It admits either regular non-symlink
`app/keystore.properties` + `app/release.jks` (exactly nonblank `storePassword`,
`keyAlias`, `keyPassword`) or the complete three-variable environment tuple,
never both. The alias and APK signer must equal production certificate SHA-256
`3e0ddb2c5d39953d278f8cce813ff07a6b74059f1f9caa8fd752602e2bb8b61a`.
Missing/blank/unknown mode fails `prodRelease`. Certificate rotation requires
separate governance. Tag approval authorizes an effect; only successful
production preflight/build/signer checks authorize GitHub Release artifacts.
No manual secret-name attestation or authenticated secret inventory is required.
