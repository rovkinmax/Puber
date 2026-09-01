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
| Production-screen instrumentation | `PlayerScreenContentFocusTest`, `PlayerScreenE2ETest`, and `PlayerVideoSurfaceTest` | The production screen/Koin scope handles real media, concrete TV focus, D-pad/media/back events, lifecycle progress saves, resume, series episode identity, and surface ownership. |

The instrumentation tests use a per-test `SimpleCache`, unique scenario
identities, event/latch/player fences instead of `Thread.sleep`, and bounded
failure diagnostics. Teardown waits for zero active server requests, releases
the player and surface, removes only test-owned cache state, and fails on
unknown or external-origin requests.

All six player instrumentation classes share
`PlayerInstrumentationTestCase`, so each JUnit scenario is reported as a named
Kaspresso step. Production `PlayerScreen` journeys use `PlayerComposeScreen`
and the minimal stable player-only test tags. Controller, Media3, network,
cache, lifecycle, codec, request-journal, and resource assertions remain
direct and event-based inside those steps.

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
`player-test-fixtures/src/main/assets/player-fixtures/SHA256SUMS`. This
packaged manifest is also the JVM and Android runtime verification source;
there is no second generated copy. Generation details and synthetic-input
provenance are in `player-test-fixtures/fixtures-provenance.md`.

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

## Verification entry points

The player-owned host entry points are:

- fixture tests: `:player-test-fixtures:test`;
- APK-pair resolver regression:
  `tools/test-resolve-android-test-apk-pair`;
- player VM suites: `:app:testDevDebugUnitTest` and
  `:app:testProdDebugUnitTest`, filtered to
  `com.kino.puber.ui.feature.player.vm.*`;
- production and Android-test compilation:
  `:app:compileDevDebugKotlin` and
  `:app:compileInstrumentationDebugAndroidTestKotlin`.

Repository checkout and worktree command selection is owned by `AGENTS.md`;
this guide intentionally lists Gradle targets rather than duplicating wrapper
procedures.

## APK pair validation

`tools/resolve-android-test-apk-pair` and its executable regression suite,
`tools/test-resolve-android-test-apk-pair`, are project-owned. Keep them with
the variants and manifests whose pairing rules they enforce.

Kent's runtime procedure validates and installs supplied APKs but does not
choose a target/test pair or prove that both APKs belong to the current build.
Before the runtime procedure begins, the resolver therefore:

- requires both variant metadata files and both resolved APKs to be at least
  as new as the caller's build-start timestamp;
- accepts exactly one regular, non-symlink, unfiltered `SINGLE` APK from each
  metadata file;
- reads the APK manifests and requires
  `androidx.test.runner.AndroidJUnitRunner`;
- requires the instrumentation `targetPackage` to match the selected pairing
  contract;
- emits the validated paths, application IDs, target package, runner, and
  SHA-256 values as one bounded JSON object.

The self-test must remain beside the resolver because it fail-closes stale,
ambiguous, split, path-escaping, symlinked, wrong-runner, wrong-package, and
cross-contract pairs without requiring an emulator.

## Device acceptance contract

The complete package
`com.kino.puber.ui.feature.player` runs against a fresh
`app-android-test` resolver-validated target/test APK pair. Final acceptance
requires two clean passes without flaky retry. Each pass preserves the
hermetic network, concrete focus, track selection, lifecycle/restoration,
cache/resource cleanup, codec, request-journal, and production-data-safety
oracles described above. Resolver output remains the authority for APK paths,
package IDs, instrumentation target package, runner, and SHA-256 values.

Kent owns emulator leasing, exact-serial targeting, preservation-safe
installation, runtime cleanup, and evidence capture. Follow
`.kent/context/smoke.md` and `.kent/commands/smoke-test.md`; those procedures
are intentionally not copied into this product guide.

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

Verification covers `prodReleaseRuntimeClasspath`, the prod debug/release APKs,
and the prod release AAB. Bounded artifact listings must contain no fixture
prefix, fixture hashes/provenance, test-control component, server package,
Kaspresso, or Kakao dependency. If release signing credentials are absent in a
local worktree, report that packaging limitation; never copy credentials into
the worktree or weaken the production exclusion check.
