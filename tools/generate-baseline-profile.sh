#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CACHE_FILE="${PUBER_BASELINE_AUTH_CACHE:-"$ROOT_DIR/.todo/baseline-profile-auth.env"}"
TARGET_PACKAGE="${PUBER_BASELINE_TARGET_PACKAGE:-com.kino.puber.stage}"
MAIN_ACTIVITY="${TARGET_PACKAGE}/com.kino.puber.MainActivity"
AUTH_RECEIVER="${TARGET_PACKAGE}/com.kino.puber.profile.BaselineProfileAuthReceiver"
AUTH_PROVIDER_URI="content://${TARGET_PACKAGE}.profile.auth/tokens"

load_cached_auth() {
    if [[ -f "$CACHE_FILE" ]]; then
        # shellcheck source=/dev/null
        source "$CACHE_FILE"
    fi
}

decode_base64() {
    local value="$1"
    local decoded

    if decoded="$(printf '%s' "$value" | base64 --decode 2>/dev/null)"; then
        printf '%s' "$decoded"
        return
    fi

    printf '%s' "$value" | base64 -D
}

cache_auth() {
    mkdir -p "$(dirname "$CACHE_FILE")"
    {
        printf 'PUBER_BASELINE_ACCESS_TOKEN=%q\n' "$PUBER_BASELINE_ACCESS_TOKEN"
        printf 'PUBER_BASELINE_REFRESH_TOKEN=%q\n' "$PUBER_BASELINE_REFRESH_TOKEN"
        if [[ -n "${PUBER_BASELINE_USERNAME:-}" ]]; then
            printf 'PUBER_BASELINE_USERNAME=%q\n' "$PUBER_BASELINE_USERNAME"
        fi
        if [[ -n "${PUBER_BASELINE_API_DOMAIN:-}" ]]; then
            printf 'PUBER_BASELINE_API_DOMAIN=%q\n' "$PUBER_BASELINE_API_DOMAIN"
        fi
    } > "$CACHE_FILE"
    chmod 600 "$CACHE_FILE"
}

export_auth_from_device() {
    local output
    local access_token_base64
    local refresh_token_base64
    local username_base64
    local api_domain_base64

    output="$(adb shell content query --uri "$AUTH_PROVIDER_URI")"

    access_token_base64="$(printf '%s\n' "$output" | sed -n 's/.*name=access_token, value_base64=//p' | tail -n 1)"
    refresh_token_base64="$(printf '%s\n' "$output" | sed -n 's/.*name=refresh_token, value_base64=//p' | tail -n 1)"
    username_base64="$(printf '%s\n' "$output" | sed -n 's/.*name=username, value_base64=//p' | tail -n 1)"
    api_domain_base64="$(printf '%s\n' "$output" | sed -n 's/.*name=api_domain, value_base64=//p' | tail -n 1)"

    if [[ -z "$access_token_base64" || -z "$refresh_token_base64" ]]; then
        echo "Unable to export auth tokens from $TARGET_PACKAGE." >&2
        echo "Content query output:" >&2
        echo "$output" >&2
        return 1
    fi

    PUBER_BASELINE_ACCESS_TOKEN="$(decode_base64 "$access_token_base64")"
    PUBER_BASELINE_REFRESH_TOKEN="$(decode_base64 "$refresh_token_base64")"
    PUBER_BASELINE_USERNAME=""
    PUBER_BASELINE_API_DOMAIN=""
    if [[ -n "$username_base64" ]]; then
        PUBER_BASELINE_USERNAME="$(decode_base64 "$username_base64")"
    fi
    if [[ -n "$api_domain_base64" ]]; then
        PUBER_BASELINE_API_DOMAIN="$(decode_base64 "$api_domain_base64")"
    fi
    export PUBER_BASELINE_ACCESS_TOKEN
    export PUBER_BASELINE_REFRESH_TOKEN
    export PUBER_BASELINE_USERNAME
    export PUBER_BASELINE_API_DOMAIN

    if [[ -z "$PUBER_BASELINE_ACCESS_TOKEN" || -z "$PUBER_BASELINE_REFRESH_TOKEN" ]]; then
        echo "Exported auth payload did not contain both access and refresh tokens." >&2
        return 1
    fi

    cache_auth
}

bootstrap_auth_if_needed() {
    if [[ -n "${PUBER_BASELINE_ACCESS_TOKEN:-}" && -n "${PUBER_BASELINE_REFRESH_TOKEN:-}" ]]; then
        return
    fi

    load_cached_auth
    if [[ -n "${PUBER_BASELINE_ACCESS_TOKEN:-}" && -n "${PUBER_BASELINE_REFRESH_TOKEN:-}" ]]; then
        return
    fi

    echo "No cached baseline-profile auth tokens found."
    echo "Installing devNonMinifiedRelease and starting the normal OAuth device-code flow."
    (cd "$ROOT_DIR" && ./gradlew :app:installDevNonMinifiedRelease)

    adb shell am force-stop "$TARGET_PACKAGE" >/dev/null 2>&1 || true
    adb shell am start -n "$MAIN_ACTIVITY" >/dev/null

    echo
    echo "Complete authorization on the TV/emulator screen, then press Enter here."
    echo "Tokens will be exported through the nonMinifiedRelease-only receiver and cached at:"
    echo "$CACHE_FILE"
    read -r

    export_auth_from_device
}

run_generation() {
    local gradle_args=(
        ":app:generateBaselineProfile"
        "-Pandroid.testInstrumentationRunnerArguments.puber.baselineProfile.accessToken=$PUBER_BASELINE_ACCESS_TOKEN"
        "-Pandroid.testInstrumentationRunnerArguments.puber.baselineProfile.refreshToken=$PUBER_BASELINE_REFRESH_TOKEN"
    )

    if [[ -n "${PUBER_BASELINE_USERNAME:-}" ]]; then
        gradle_args+=(
            "-Pandroid.testInstrumentationRunnerArguments.puber.baselineProfile.username=$PUBER_BASELINE_USERNAME"
        )
    fi

    if [[ -n "${PUBER_BASELINE_API_DOMAIN:-}" ]]; then
        gradle_args+=(
            "-Pandroid.testInstrumentationRunnerArguments.puber.baselineProfile.apiDomain=$PUBER_BASELINE_API_DOMAIN"
        )
    fi

    (cd "$ROOT_DIR" && ./gradlew "${gradle_args[@]}")
}

bootstrap_auth_if_needed
run_generation
