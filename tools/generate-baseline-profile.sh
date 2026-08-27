#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PROPERTIES_FILE="$ROOT_DIR/gradle.properties"
GRADLEW="$ROOT_DIR/gradlew"
AGENTW="$ROOT_DIR/tools/agentw"

fail() {
    echo "Baseline profile generation: $*" >&2
    exit 1
}

[[ -x "$GRADLEW" ]] || fail "Gradle wrapper is missing or not executable: $GRADLEW"
[[ -f "$PROPERTIES_FILE" ]] || fail "Gradle properties file is missing: $PROPERTIES_FILE"

MOCK_PORT="$(
    awk -F= '
        $1 == "puber.baselineMockPort" {
            gsub(/[[:space:]]/, "", $2)
            print $2
            exit
        }
    ' "$PROPERTIES_FILE"
)"

[[ "$MOCK_PORT" =~ ^[0-9]+$ ]] || fail \
    "puber.baselineMockPort is missing or invalid in $PROPERTIES_FILE"
(( MOCK_PORT >= 1024 && MOCK_PORT <= 65535 )) || fail \
    "puber.baselineMockPort must be between 1024 and 65535 (found $MOCK_PORT)"

for argument in "$@"; do
    case "$argument" in
        *accessToken*|*refreshToken*|*username*|*apiDomain*|*PUBER_BASELINE*)
            fail "credential-bearing arguments are not accepted: $argument"
            ;;
    esac
done

if [[ -x "$AGENTW" ]]; then
    GRADLE_COMMAND=("$AGENTW")
else
    GRADLE_COMMAND=("$GRADLEW")
fi

echo "Generating baseline profiles with the benchmark-owned MockWebServer."
echo "Mock origin: http://127.0.0.1:$MOCK_PORT"
echo "The instrumentation APK supplies synthetic auth and loopback-only networking."
echo "No credentials, token cache, OAuth flow, or production backend is used."

if ! "${GRADLE_COMMAND[@]}" :app:generateBaselineProfile "$@"; then
    echo >&2
    echo "Baseline profile generation failed." >&2
    echo "Check that a leased TV emulator is available and that the mock-backed" >&2
    echo "instrumentation variants can build and reach 127.0.0.1:$MOCK_PORT." >&2
    exit 1
fi

echo "Baseline profiles generated from the local mock backend."
