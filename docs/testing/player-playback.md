# Player playback test guide

This document describes the hermetic test pyramid for the Puber video player.
The suite uses synthetic, committed media only and does not require KinoPub
credentials, authenticated responses, a production backend, or a public
stream.

## Test layers

| Layer | Entry point | What it proves |
| --- | --- | --- |
| Fixture integrity | `:player-test-fixtures:test` and its Android test | The same committed bytes, metadata, playlist references, rendition labels, and WebVTT cue are available to JVM and Android tests. |
| Host policy/state | `:app:testDevDebugUnitTest` and `:app:testProdDebugUnitTest`, filtered to `com.kino.puber.ui.feature.player.vm.*` | HLS error policy, subtitle MIME and identity, audio/subtitle preference matching, AC3 fallback state, VM transitions, and generation guards. |
| Real player instrumentation | `PlaybackControllerDeviceTest`, `PlaybackControllerNetworkTracksTest`, and `PlaybackControllerResourcesTest` | The production `PlaybackController` and Media3 player load loopback MP4/HLS, render on a real `PlayerView`, select tracks/cues, recover from programmed faults, and release cleanly. |
| Production-screen instrumentation | `PlayerScreenE2ETest` | The production screen/Koin scope handles real media, D-pad/media/back events, lifecycle progress saves, resume, and series episode identity. |

The instrumentation tests use a per-test `SimpleCache`, unique scenario
identities, event/latch/player fences instead of `Thread.sleep`, and bounded
failure diagnostics. Teardown waits for zero active server requests, releases
the player and surface, removes only test-owned cache state, and fails on
unknown or external-origin requests.

## Fixture pack and provenance

The single source of truth is
`player-test-fixtures/src/main/assets/player-fixtures`. It contains:

- a four-second H.264/AAC progressive MP4;
- an HLS VOD master with 360p and 720p H.264 variants;
- English (`en`, `English`) and Spanish (`es`, `Español`) AAC renditions;
- a WebVTT subtitle with a cue from 1,000 ms through 2,000 ms;
- the HLS playlists and committed MPEG-TS segments required by those routes.

Expected metadata is in
`player-test-fixtures/src/main/assets/player-fixtures/fixture-manifest.properties`.
The complete byte manifest is
`player-test-fixtures/SHA256SUMS` (and the packaged copy under the fixture
asset directory). Generation details and synthetic-input provenance are in
`player-test-fixtures/fixtures-provenance.md`.

Regeneration is a maintainer-only action. It requires `ffmpeg 9.0.1`, uses
only `lavfi` test-pattern and sine-wave inputs, and is deliberately not part
of Gradle or test execution:

```sh
FFMPEG_BIN=/path/to/ffmpeg tools/generate-player-test-fixtures.sh --force
(cd player-test-fixtures/src/main/assets/player-fixtures &&
  shasum -a 256 -c SHA256SUMS)
```

The generator refuses another ffmpeg version and refuses output outside the
fixture directory. A normal test run verifies the committed hashes instead of
transcoding media.

The pack contains no third-party or production media and has no media license
to reproduce. The only external tool assumption is the locally installed
`ffmpeg 9.0.1` generator binary; ffmpeg is not packaged in the application or
test artifacts.

## Hermetic server and network policy

`HermeticTestServer` matches typed method/path/query routes independently of
request order. It supports static bytes/text, redirects, byte ranges, response
sequences, delayed responses, truncation, disconnects, and recovery. Its
bounded journal records only sanitized method/path, selected headers, range,
route, outcome, and completion state.

The app instrumentation flavor pins API and media routes to the server's
`127.0.0.1` origin. `LoopbackNetworkBlocker` rejects every other origin
through the existing PUB-77 fail-closed control and records a normalized
violation. Unknown routes and unexpected egress fail teardown. Authorization
values, credentials, redirect query data, authenticated payloads, broad UI
dumps, and raw logs are not test evidence.

## Host commands

Run the deterministic fixture and resolver checks first:

```sh
./tools/agentw :player-test-fixtures:test
./tools/test-resolve-android-test-apk-pair
```

Run the player host suites for both app flavors:

```sh
./tools/agentw :app:testDevDebugUnitTest \
  --tests 'com.kino.puber.ui.feature.player.vm.*'
./tools/agentw :app:testProdDebugUnitTest \
  --tests 'com.kino.puber.ui.feature.player.vm.*'
```

Run compilation checks:

```sh
./tools/agentw :app:compileDevDebugKotlin
./tools/agentw :app:compileInstrumentationDebugAndroidTestKotlin
```

## TV emulator execution

Use a leased TV emulator only. Discover a running TV emulator, acquire its
exact serial lock, export `ANDROID_SERIAL`, and keep release in a trap:

```sh
EMULATORS=($(
  .kent/adapters/mobile/emulator-resource-lock.sh adb-emulators tv
))
LOCK_OUTPUT="$(
  .kent/adapters/mobile/emulator-resource-lock.sh \
    acquire-any "${EMULATORS[@]}" -- 900 7200
)"
LOCK_RESOURCE="$(printf '%s\n' "$LOCK_OUTPUT" | sed -n 's/^resource=//p')"
LOCK_TOKEN="$(printf '%s\n' "$LOCK_OUTPUT" | sed -n 's/^token=//p')"
export ANDROID_SERIAL="$LOCK_RESOURCE"
trap '.kent/adapters/mobile/emulator-resource-lock.sh release \
  "$LOCK_RESOURCE" "$LOCK_TOKEN"' EXIT
test -n "$ANDROID_SERIAL"
```

If no suitable running emulator is available, stop rather than starting one
implicitly. Physical devices require separate explicit authorization.

Build a fresh app instrumentation target and Android-test APK with
`--rerun-tasks`; do not use `connected*`, `install*`, or another Gradle task
that implicitly selects a device:

```sh
BUILD_STARTED_AT="$(date +%s)"
./tools/agentw :app:assembleInstrumentationDebug \
  :app:assembleInstrumentationDebugAndroidTest --rerun-tasks
PAIR_JSON="$(
  ./tools/resolve-android-test-apk-pair \
    --pairing-contract app-android-test \
    --target-metadata \
      app/build/outputs/apk/instrumentation/debug/output-metadata.json \
    --test-metadata \
      app/build/outputs/apk/androidTest/instrumentation/debug/output-metadata.json \
    --built-after "$BUILD_STARTED_AT"
)"
APP_APK="$(jq -r '.target.apk_path' <<<"$PAIR_JSON")"
APP_TEST_APK="$(jq -r '.test.apk_path' <<<"$PAIR_JSON")"
APP_PACKAGE="$(jq -r '.target.application_id' <<<"$PAIR_JSON")"
APP_TEST_PACKAGE="$(jq -r '.test.application_id' <<<"$PAIR_JSON")"
.kent/adapters/mobile/android-apk-install-preserve install-preserve \
  --serial "$ANDROID_SERIAL" --package "$APP_PACKAGE" --apk "$APP_APK"
.kent/adapters/mobile/android-apk-install-preserve install-preserve \
  --serial "$ANDROID_SERIAL" --package "$APP_TEST_PACKAGE" --apk "$APP_TEST_APK"
```

Run the complete player instrumentation suite with an explicit serial and
runner. During development, use one class filter at a time:

```sh
adb -s "$ANDROID_SERIAL" shell am instrument -w -r \
  -e package com.kino.puber.ui.feature.player \
  "$APP_TEST_PACKAGE/androidx.test.runner.AndroidJUnitRunner"
```

The final acceptance run repeats this command twice from clean
test-owned state. It does not clear app data, uninstall packages, or use
flaky retry as a condition of success. The resolver output is the authority
for APK paths, package IDs, instrumentation target package, runner, and
SHA-256 values.

The migrated PUB-77 baseline regression uses the separate
`standalone-self-target` resolver contract and is documented with its
explicit build/install/instrumentation commands in the task plan and
`02-server-contract.md`; it must not be paired with the app
`app-android-test` contract.

## Emulator capabilities and diagnostics

The mandatory device path is H.264/AAC. A device lacking that capability
produces a concise capability failure and does not silently turn decode
coverage into a pass. HEVC and real AC3 decode diagnostics are intentionally
not part of this fixture pack or acceptance suite; AC3 retry/fallback remains
mandatory in the host state-machine tests.

There are no assumption-based skips for mandatory scenarios. A capability
report may explain a failed H.264/AAC setup, while the intentionally omitted
HEVC/AC3 decode diagnostics are out of scope rather than a passing skip.

On failure, diagnostics are bounded to player state, selected formats,
decoder name/counters, relevant Media3 analytics events, capability
information, and a sanitized request-journal tail. Startup, dropped-frame,
and rebuffer measurements are informational and have no absolute emulator
performance threshold.

## Production artifact exclusion

The fixture module is test-only. `:app` references it through
`testImplementation` and the `instrumentationImplementation` flavor edge;
`:baselineprofile` is itself a test module. No production
`implementation`, `prodImplementation`, or runtime edge may resolve
`:player-test-fixtures`.

Run the dependency and packaging checks:

```sh
./tools/agentw :app:dependencies \
  --configuration prodReleaseRuntimeClasspath
./tools/agentw :app:assembleProdDebug
./tools/agentw :app:assembleProdRelease
./tools/agentw :app:bundleProdRelease
```

Inspect only bounded listings and fail if production artifacts contain the
fixture prefix, fixture hashes/provenance, test-control components, or server
packages:

```sh
PROD_APK=app/build/outputs/apk/prod/release/app-prod-release.apk
PROD_AAB="$(printf '%s\n' app/build/outputs/bundle/prodRelease/*.aab)"
if unzip -l "$PROD_APK" | grep -Eq \
  'player-fixtures|fixtures-provenance|playertestfixtures|TestControl'; then
  echo "test-only player content found in production APK" >&2
  exit 1
fi
if unzip -l "$PROD_AAB" | grep -Eq \
  'player-fixtures|fixtures-provenance|playertestfixtures|TestControl'; then
  echo "test-only player content found in production AAB" >&2
  exit 1
fi
```

The production runtime dependency report must likewise contain no
`project :player-test-fixtures`. If release signing credentials are absent in
a local worktree, report that package-signing limitation explicitly; never
copy credentials into the worktree or weaken the production exclusion check.
