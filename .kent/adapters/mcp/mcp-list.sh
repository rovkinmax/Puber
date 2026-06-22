#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=mcp-common.sh
source "$SCRIPT_DIR/mcp-common.sh"

usage() {
  cat <<'EOF'
Usage: mcp-list.sh [server] [--schema] [--refresh] [mcporter list options]

Examples:
  .kent/adapters/mcp/mcp-list.sh figma --schema --timeout 5000
  .kent/adapters/mcp/mcp-list.sh mobile --schema --refresh --timeout 15000
EOF
}

if [[ "${1-}" == "--help" ]]; then
  usage
  exit 0
fi

server=""
if [[ $# -gt 0 && "$1" != --* ]]; then
  server="$1"
  shift
fi

schema=false
refresh=false
args=()
while [[ $# -gt 0 ]]; do
  case "$1" in
    --schema)
      schema=true
      args+=("$1")
      shift
      ;;
    --refresh)
      refresh=true
      shift
      ;;
    *)
      args+=("$1")
      shift
      ;;
  esac
done

root="$(workspace_root)"
require_mcporter
config="$(resolve_mcp_config)"
config_display="${config:-mcporter-default}"

cache_path=""
if [[ "$schema" == true && -n "$server" ]]; then
  mkdir -p "$root/build/mcp-cache"
  cache_path="$root/build/mcp-cache/$(safe_name "$server")-schema.json"
  if [[ "$refresh" != true && -s "$cache_path" ]]; then
    cat "$cache_path"
    append_call_log "$root" "$server" "list-schema-cache" "$config_display" "json" "$cache_path" 0 ""
    exit 0
  fi
fi

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
  cmd+=(list --stdio "$serena_cmd" "${serena_args[@]}")
else
  if [[ -n "$config" ]]; then
    cmd+=(--config "$config")
  fi
  cmd+=(--root "$root")
  cmd+=(list)
  if [[ -n "$server" ]]; then
    cmd+=("$server")
  fi
fi
cmd+=("${args[@]}")

set +e
with_serena_lock_if_needed "$server" "$root" "${cmd[@]}" >"$tmp_out" 2>"$tmp_err"
exit_code=$?
set -e

cat "$tmp_out"
if [[ -s "$tmp_err" ]]; then
  cat "$tmp_err" >&2
fi

raw_log_path=""
if [[ $exit_code -eq 0 && -n "$cache_path" ]]; then
  if grep -Eqi 'tools unavailable|Tools: <unavailable>|Next: run .*auth|Non-200 status code' "$tmp_out"; then
    raw_log_path=""
  else
    cp "$tmp_out" "$cache_path"
    raw_log_path="$cache_path"
  fi
fi

error_code=""
if [[ $exit_code -ne 0 ]]; then
  error_code="mcporter_list_failed"
fi
append_call_log "$root" "${server:-all}" "list" "$config_display" "json" "$raw_log_path" "$exit_code" "$error_code"

exit "$exit_code"
