#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=mcp-common.sh
source "$SCRIPT_DIR/mcp-common.sh"

usage() {
  cat <<'EOF'
Usage: mcp-call.sh <server.tool> [arguments] [--raw-dir <dir>] [--no-save-raw] [--allow-mutate]
       mcp-call.sh --self-test

Examples:
  .kent/adapters/mcp/mcp-call.sh figma.get_design_context nodeId="1:2" --output markdown --raw-dir .todo/my-feature/mcp
  .kent/adapters/mcp/mcp-call.sh mobile.input action=tap text="Invoices" --output json --allow-mutate --raw-dir build/test-artifacts/mcp
  .kent/adapters/mcp/mcp-call.sh --self-test
EOF
}

if [[ "${1-}" == "--help" || $# -lt 1 ]]; then
  usage
  [[ $# -lt 1 ]] && exit 2 || exit 0
fi

if [[ "${1-}" == "--self-test" ]]; then
  self_test=true
else
  self_test=false
fi

target="$1"
shift

allow_mutate=false
save_raw=true
raw_dir=""
output_mode="text"
args=()

while [[ $# -gt 0 ]]; do
  case "$1" in
    --allow-mutate)
      allow_mutate=true
      shift
      ;;
    --no-save-raw)
      save_raw=false
      shift
      ;;
    --raw-dir)
      raw_dir="${2-}"
      if [[ -z "$raw_dir" ]]; then
        printf 'missing_value: --raw-dir requires a value\n' >&2
        exit 2
      fi
      shift 2
      ;;
    --output)
      output_mode="${2-}"
      if [[ -z "$output_mode" ]]; then
        printf 'missing_value: --output requires a value\n' >&2
        exit 2
      fi
      args+=("$1" "$2")
      shift 2
      ;;
    --output=*)
      output_mode="${1#--output=}"
      args+=("$1")
      shift
      ;;
    *)
      args+=("$1")
      shift
      ;;
  esac
done

server="${target%%.*}"
tool="${target#*.}"
if [[ "$self_test" != true && ( "$server" == "$target" || -z "$server" || -z "$tool" ) ]]; then
  printf 'invalid_target: expected <server.tool>, got %s\n' "$target" >&2
  exit 2
fi

arg_value() {
  local key="$1"
  local arg
  for arg in "${args[@]}"; do
    if [[ "$arg" == "$key="* ]]; then
      printf '%s\n' "${arg#*=}"
      return 0
    fi
  done
  return 1
}

action_is_one_of() {
  local action="$1"
  shift

  local allowed
  for allowed in "$@"; do
    if [[ "$action" == "$allowed" ]]; then
      return 0
    fi
  done

  return 1
}

mobile_tool_mutates() {
  local tool_name="$1"
  local action_name="$2"

  case "$tool_name" in
    device)
      ! action_is_one_of "$action_name" list get_target list_modules
      ;;
    screen)
      ! action_is_one_of "$action_name" capture annotate
      ;;
    input|flow)
      return 0
      ;;
    ui)
      ! action_is_one_of "$action_name" tree find analyze wait assert_visible assert_gone
      ;;
    app)
      ! action_is_one_of "$action_name" list
      ;;
    system)
      ! action_is_one_of "$action_name" wait logs info webview clipboard_get metrics
      ;;
    browser)
      ! action_is_one_of "$action_name" list_sessions snapshot screenshot wait_for_selector
      ;;
    desktop)
      ! action_is_one_of "$action_name" windows clipboard_get performance monitors get_target_pid
      ;;
    store)
      ! action_is_one_of "$action_name" get_releases get_versions
      ;;
    visual)
      ! action_is_one_of "$action_name" list
      ;;
    recorder)
      ! action_is_one_of "$action_name" status list show
      ;;
    sync)
      ! action_is_one_of "$action_name" status list
      ;;
    accessibility)
      ! action_is_one_of "$action_name" audit check summary rules
      ;;
    performance)
      ! action_is_one_of "$action_name" snapshot compare crashes framestats
      ;;
    autopilot)
      ! action_is_one_of "$action_name" status
      ;;
    sandbox)
      ! action_is_one_of "$action_name" prefs_read file_list file_read
      ;;
    intent)
      ! action_is_one_of "$action_name" services
      ;;
    sensor)
      ! action_is_one_of "$action_name" notifications
      ;;
    network)
      ! action_is_one_of "$action_name" traffic connectivity
      ;;
    *)
      return 0
      ;;
  esac
}

target_requires_mutation_approval() {
  local target_name="$1"
  local action_name="$2"

  case "$target_name" in
    mobile.*)
      mobile_tool_mutates "${target_name#mobile.}" "$action_name"
      ;;
    jetbrains.rename_refactoring|jetbrains.reformat_file|jetbrains.execute_run_configuration)
      return 0
      ;;
    jetbrains.build_project)
      printf 'unsupported_mcp_tool: jetbrains.build_project is intentionally not used; run Gradle directly\n' >&2
      exit 2
      ;;
    firebase.firebase_login|firebase.firebase_logout|firebase.firebase_create_project|firebase.firebase_create_app|firebase.firebase_create_android_sha|firebase.firebase_update_environment|firebase.firebase_init|firebase.firebase_deploy|firebase.dataconnect_execute|firebase.auth_update_user|firebase.auth_set_sms_region_policy|firebase.messaging_send_message|firebase.remoteconfig_update_template|firebase.crashlytics_create_note|firebase.crashlytics_delete_note|firebase.crashlytics_update_issue|firebase.realtimedatabase_set_data|firebase.firestore_add_document|firebase.firestore_update_document|firebase.firestore_delete_document|firebase.firestore_create_database|firebase.firestore_update_database|firebase.firestore_delete_database|firebase.firestore_create_index|firebase.firestore_delete_index)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

assert_mobile_policy() {
  local tool_name="$1"
  local action_name="$2"
  local expected="$3"
  local actual

  if mobile_tool_mutates "$tool_name" "$action_name"; then
    actual="mutating"
  else
    actual="read-only"
  fi

  if [[ "$actual" != "$expected" ]]; then
    printf 'self_test_failed: mobile.%s action=%s expected %s, got %s\n' \
      "$tool_name" "$action_name" "$expected" "$actual" >&2
    return 1
  fi
}

run_self_test() {
  assert_mobile_policy device list read-only
  assert_mobile_policy device "" mutating
  assert_mobile_policy device enable_module mutating
  assert_mobile_policy screen capture read-only
  assert_mobile_policy input tap mutating
  assert_mobile_policy ui assert_visible read-only
  assert_mobile_policy ui tap_text mutating
  assert_mobile_policy app list read-only
  assert_mobile_policy app launch mutating
  assert_mobile_policy system logs read-only
  assert_mobile_policy system clear_logs mutating
  assert_mobile_policy browser screenshot read-only
  assert_mobile_policy browser evaluate mutating
  assert_mobile_policy desktop windows read-only
  assert_mobile_policy desktop clipboard_set mutating
  assert_mobile_policy store get_versions read-only
  assert_mobile_policy store submit mutating
  assert_mobile_policy visual list read-only
  assert_mobile_policy visual compare mutating
  assert_mobile_policy visual baseline_update mutating
  assert_mobile_policy recorder show read-only
  assert_mobile_policy recorder export mutating
  assert_mobile_policy recorder play mutating
  assert_mobile_policy sync list read-only
  assert_mobile_policy sync run mutating
  assert_mobile_policy accessibility audit read-only
  assert_mobile_policy performance snapshot read-only
  assert_mobile_policy performance monitor mutating
  assert_mobile_policy autopilot status read-only
  assert_mobile_policy autopilot tests mutating
  assert_mobile_policy sandbox file_read read-only
  assert_mobile_policy sandbox sqlite_query mutating
  assert_mobile_policy intent services read-only
  assert_mobile_policy intent deeplink mutating
  assert_mobile_policy sensor notifications read-only
  assert_mobile_policy sensor battery mutating
  assert_mobile_policy network traffic read-only
  assert_mobile_policy network airplane mutating
  assert_mobile_policy future inspect mutating
}

if [[ "$self_test" == true ]]; then
  run_self_test
  exit 0
fi

mutating=false
if target_requires_mutation_approval "$target" "$(arg_value action || true)"; then
  mutating=true
fi

if [[ "$mutating" == true && "$allow_mutate" != true ]]; then
  printf 'mutating_mcp_requires_allow: %s requires --allow-mutate\n' "$target" >&2
  exit 2
fi

root="$(workspace_root)"
require_mcporter
config="$(resolve_mcp_config)"
config_display="${config:-mcporter-default}"

raw_path=""
tmp_out="$(mktemp)"
tmp_err="$(mktemp)"
trap 'rm -f "$tmp_out" "$tmp_err"' EXIT

cmd=(mcporter)
if [[ "$server" == "serena" ]]; then
  serena_cmd="$(resolve_serena_command "$config")"
  serena_args=()
  while IFS= read -r -d '' arg; do
    serena_args+=("$arg")
  done < <(serena_stdio_args "$root")
  cmd+=(call --stdio "$serena_cmd" "${serena_args[@]}" "$tool" "${args[@]}")
else
  if [[ -n "$config" ]]; then
    cmd+=(--config "$config")
  fi
  cmd+=(--root "$root")
  cmd+=(call "$target" "${args[@]}")
fi

set +e
with_serena_lock_if_needed "$server" "$root" "${cmd[@]}" >"$tmp_out" 2>"$tmp_err"
exit_code=$?
set -e

cat "$tmp_out"
if [[ -s "$tmp_err" ]]; then
  cat "$tmp_err" >&2
fi

if [[ "$save_raw" == true ]]; then
  if [[ -z "$raw_dir" ]]; then
    raw_dir="$root/.todo/_mcp-raw"
  fi
  ext="$(output_ext "$output_mode")"
  server_dir="$raw_dir/$(safe_name "$server")"
  mkdir -p "$server_dir"
  raw_path="$server_dir/$(safe_name "$tool")-$(timestamp_utc).$ext"
  cp "$tmp_out" "$raw_path"
fi

error_code=""
if [[ $exit_code -ne 0 ]]; then
  error_code="mcporter_call_failed"
fi
append_call_log "$root" "$server" "$tool" "$config_display" "$output_mode" "$raw_path" "$exit_code" "$error_code"

exit "$exit_code"
