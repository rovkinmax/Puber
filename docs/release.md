# Release And Baseline Profiles

## GitHub Release

Releases are published by `.github/workflows/release.yml`.

The normal release flow is tag based:

```bash
git tag v1.2.0
git push origin v1.2.0
```

The workflow builds `prodRelease`, uploads the APK and SHA-256 checksum as a workflow artifact, and attaches both files
to the GitHub Release for the tag.

The workflow also supports manual runs from GitHub Actions through `workflow_dispatch`. Use the `release_tag` input with
the same `vX.Y.Z` format.

Required GitHub Secrets:

```text
RELEASE_KEYSTORE_BASE64
STOREPASS
KEYALIAS
PUBER_CLIENT_SECRET
TMDB_READ_ACCESS_TOKEN
```

Optional GitHub Secret:

```text
KEYPASS
```

When `KEYPASS` is not set, Gradle uses `STOREPASS` as the key password.

## Local Release Build

Run:

```bash
./gradlew :app:assembleProdRelease
```

Baseline profile generation is intentionally disabled during release builds:

```kotlin
baselineProfile {
    automaticGenerationDuringBuild = false
}
```

Release builds package the checked-in profiles from:

```text
app/src/main/generated/baselineProfiles/baseline-prof.txt
app/src/main/generated/baselineProfiles/startup-prof.txt
```

## Refresh Baseline Profiles

Use the helper script:

```bash
./tools/generate-baseline-profile.sh
```

On the first run, if no tokens are provided and `.todo/baseline-profile-auth.env` does not exist, the script:

1. Installs `devNonMinifiedRelease`.
2. Starts the app with the normal OAuth device-code flow.
3. Waits while you enter the code manually.
4. Exports tokens through a provider that is registered only for `devNonMinifiedRelease` and `prodNonMinifiedRelease`.
5. Stores tokens locally in `.todo/baseline-profile-auth.env` with file mode `600`.
6. Runs `./gradlew :app:generateBaselineProfile`.

On later runs, the script reuses `.todo/baseline-profile-auth.env`, so you do not need to authorize again while the
refresh token remains valid.

You can also bypass the cached file by providing tokens explicitly:

```bash
PUBER_BASELINE_ACCESS_TOKEN="..." \
PUBER_BASELINE_REFRESH_TOKEN="..." \
./tools/generate-baseline-profile.sh
```

Optional values:

```bash
PUBER_BASELINE_USERNAME="..." \
PUBER_BASELINE_API_DOMAIN="..." \
./tools/generate-baseline-profile.sh
```

The helper ultimately passes these values to the profile instrumentation tests:

```bash
./gradlew :app:generateBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.puber.baselineProfile.accessToken="$PUBER_BASELINE_ACCESS_TOKEN" \
  -Pandroid.testInstrumentationRunnerArguments.puber.baselineProfile.refreshToken="$PUBER_BASELINE_REFRESH_TOKEN"
```

The profile auth receiver/provider are not packaged in `prodRelease`.

## Verification

Run:

```bash
./gradlew :app:compileDevDebugKotlin
./gradlew :app:testProdDebugUnitTest
./gradlew :app:assembleProdRelease
```
