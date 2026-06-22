#!/usr/bin/env bash
set -euo pipefail

workspace_root() {
  git rev-parse --show-toplevel 2>/dev/null || pwd
}

json_escape() {
  python3 -c 'import json,sys; print(json.dumps(sys.stdin.read())[1:-1])'
}

read_env_file_value() {
  local file="$1"
  local key="$2"
  [[ -f "$file" ]] || return 0

  while IFS= read -r line || [[ -n "$line" ]]; do
    case "$line" in
      ""|\#*) continue ;;
    esac
    [[ "$line" == "$key="* ]] || continue
    printf '%s\n' "${line#"$key="}"
  done < "$file" | tail -n 1
}

resolve_mcp_config() {
  local root root_name original env_value project_value config_path
  root="$(workspace_root)"
  root_name="$(basename "$root")"
  original="${MCP_CONFIG_PATH-}"

  env_value="$(read_env_file_value "$HOME/.kent/mcp.env" MCP_CONFIG_PATH || true)"
  project_value="$(read_env_file_value "$HOME/.kent/mcp.${root_name}.env" MCP_CONFIG_PATH || true)"

  config_path="$env_value"
  [[ -n "$project_value" ]] && config_path="$project_value"
  [[ -n "$original" ]] && config_path="$original"

  if [[ -n "$config_path" ]]; then
    if [[ -f "$config_path" ]]; then
      printf '%s\n' "$config_path"
      return 0
    fi
    printf 'mcp_config_missing: MCP_CONFIG_PATH does not point to a readable file: %s\n' "$config_path" >&2
    return 2
  fi

  if [[ -f "$root/.mcp.json" ]]; then
    printf '%s\n' "$root/.mcp.json"
    return 0
  fi

  # Empty output means callers should omit --config and let mcporter use its
  # default merged discovery: project config/mcporter.json, global
  # ~/.mcporter/mcporter.json, and supported editor imports.
  printf '\n'
  return 0
}

require_mcporter() {
  if ! command -v mcporter >/dev/null 2>&1; then
    printf 'mcporter_missing: install mcporter and ensure it is available in PATH\n' >&2
    return 127
  fi
}

timestamp_utc() {
  date -u +%Y%m%d-%H%M%S
}

timestamp_iso() {
  date -u +%Y-%m-%dT%H:%M:%SZ
}

safe_name() {
  printf '%s' "$1" | tr -c 'A-Za-z0-9_.-' '-'
}

output_ext() {
  local mode="$1"
  case "$mode" in
    json|raw) printf 'json' ;;
    markdown) printf 'md' ;;
    text|"") printf 'txt' ;;
    *) printf 'txt' ;;
  esac
}

append_call_log() {
  local root="$1"
  local server="$2"
  local tool="$3"
  local config="$4"
  local output_mode="$5"
  local raw_path="$6"
  local exit_code="$7"
  local error_code="$8"
  local log_dir log_file
  log_dir="$root/.todo/_mcp-log"
  log_file="$log_dir/mcporter-calls.jsonl"
  mkdir -p "$log_dir"

  python3 - "$log_file" "$server" "$tool" "$config" "$output_mode" "$raw_path" "$exit_code" "$error_code" "$root" <<'PY'
import json
import sys
from datetime import datetime, timezone

log_file, server, tool, config, output_mode, raw_path, exit_code, error_code, root = sys.argv[1:]
record = {
    "timestamp": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
    "workspace": root,
    "server": server,
    "tool": tool,
    "configPath": config,
    "outputMode": output_mode,
    "rawOutputPath": raw_path or None,
    "exitCode": int(exit_code),
    "errorCode": error_code or None,
}

with open(log_file, "a", encoding="utf-8") as fh:
    fh.write(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n")
PY
}

with_serena_lock_if_needed() {
  local server="$1"
  local root="$2"
  shift 2

  if [[ "$server" != "serena" ]]; then
    "$@"
    return
  fi

  local lock_dir lock_file
  lock_dir="$root/.serena"
  lock_file="$lock_dir/mcp-serena.lock"
  mkdir -p "$lock_dir"

  if command -v flock >/dev/null 2>&1; then
    (
      flock 9
      run_serena_command_with_cleanup "$root" "$@"
    ) 9>"$lock_file"
    return
  fi

  if command -v lockf >/dev/null 2>&1; then
    printf 'serena_lock_cleanup_limited: flock not found; lockf fallback cannot cleanup Kotlin LSP orphans\n' >&2
    lockf "$lock_file" "$@"
    return
  fi

  printf 'serena_lock_unavailable: flock/lockf not found; running without serialization\n' >&2
  "$@"
}

run_serena_command_with_cleanup() {
  local root="$1"
  shift

  cleanup_orphan_kotlin_lsp_processes_for_root "$root"

  set +e
  "$@"
  local exit_code=$?
  set -e

  cleanup_orphan_kotlin_lsp_processes_for_root "$root"
  return "$exit_code"
}

cleanup_orphan_kotlin_lsp_processes_for_root() {
  local root="$1"
  local physical_root killed_pids pid ppid

  if [[ "${SERENA_SKIP_LSP_CLEANUP:-}" == "1" ]]; then
    return 0
  fi

  if ! command -v lsof >/dev/null 2>&1; then
    return 0
  fi

  physical_root="$(cd "$root" && pwd -P)"
  killed_pids=()

  while IFS= read -r pid; do
    [[ -n "$pid" ]] || continue
    [[ "$(kotlin_lsp_cwd "$pid")" == "$physical_root" ]] || continue

    ppid="$(ps -p "$pid" -o ppid= 2>/dev/null | tr -d '[:space:]' || true)"
    [[ "$ppid" == "1" ]] || continue

    if kill "$pid" 2>/dev/null; then
      killed_pids+=("$pid")
    fi
  done < <(kotlin_lsp_pids)

  wait_for_process_shutdown "${killed_pids[@]}"

  if [[ "${#killed_pids[@]}" -gt 0 ]]; then
    printf 'serena_lsp_cleanup: terminated orphan Kotlin LSP pids for %s: %s\n' \
      "$physical_root" "${killed_pids[*]}" >&2
  fi

  return 0
}

wait_for_process_shutdown() {
  local pids=("$@")
  local pid attempt alive_pids

  [[ "${#pids[@]}" -gt 0 ]] || return 0

  for attempt in {1..10}; do
    alive_pids=()
    for pid in "${pids[@]}"; do
      if kill -0 "$pid" 2>/dev/null; then
        alive_pids+=("$pid")
      fi
    done

    [[ "${#alive_pids[@]}" -eq 0 ]] && return 0
    sleep 0.2
  done

  for pid in "${alive_pids[@]}"; do
    kill -9 "$pid" 2>/dev/null || true
  done

  sleep 0.2

  for pid in "${alive_pids[@]}"; do
    if kill -0 "$pid" 2>/dev/null; then
      printf 'serena_lsp_cleanup_warning: Kotlin LSP pid still alive after SIGKILL: %s\n' "$pid" >&2
    fi
  done

  return 0
}

kotlin_lsp_pids() {
  pgrep -f 'com.jetbrains.ls.kotlinLsp.KotlinLspServerKt --stdio' 2>/dev/null || true
}

kotlin_lsp_cwd() {
  local pid="$1"
  lsof -a -p "$pid" -d cwd -Fn 2>/dev/null | sed -n 's/^n//p' | tail -n 1
}

resolve_serena_command() {
  local config="$1"
  local configured

  if [[ -n "${SERENA_COMMAND:-}" ]]; then
    printf '%s\n' "$SERENA_COMMAND"
    return
  fi

  if [[ -n "$config" && -f "$config" ]]; then
    configured="$(
      python3 - "$config" <<'PY'
import json
import sys

try:
    with open(sys.argv[1], "r", encoding="utf-8") as fh:
        data = json.load(fh)
    print(data.get("mcpServers", {}).get("serena", {}).get("command", ""))
except Exception:
    print("")
PY
    )"
    if [[ -n "$configured" ]]; then
      printf '%s\n' "$configured"
      return
    fi
  fi

  if command -v serena >/dev/null 2>&1; then
    command -v serena
    return
  fi

  printf '%s\n' "$HOME/.local/bin/serena"
}

serena_stdio_args() {
  local root="$1"
  printf '%s\0' \
    --stdio-arg start-mcp-server \
    --stdio-arg --context=ide \
    --stdio-arg --project \
    --stdio-arg "$root" \
    --stdio-arg --enable-web-dashboard=false \
    --stdio-arg --open-web-dashboard=false \
    --cwd "$root" \
    --name serena
}
