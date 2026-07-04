#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage:
  emulator-resource-lock.sh acquire <resource> [wait_seconds] [ttl_seconds]
  emulator-resource-lock.sh acquire-any <resource>... -- [wait_seconds] [ttl_seconds]
  emulator-resource-lock.sh release <resource> <token>
  emulator-resource-lock.sh status [resource]
  emulator-resource-lock.sh adb-emulators
  emulator-resource-lock.sh adb-physical-devices

Coordinates Android emulator usage across Kent sessions on this machine.
Physical devices are listed separately and must be used only with explicit user permission and an explicit serial.
Locks live under ~/.kent/runtime/resource-locks so main checkouts and Kent worktrees share them.
USAGE
}

runtime_root="${KENT_RESOURCE_LOCK_DIR:-$HOME/.kent/runtime/resource-locks}"
mkdir -p "$runtime_root"

sanitize_resource() {
  printf '%s' "$1" | tr -c 'A-Za-z0-9._=-' '_'
}

lock_dir_for() {
  printf '%s/mobile-%s.lock' "$runtime_root" "$(sanitize_resource "$1")"
}

now_epoch() {
  date +%s
}

lock_age_seconds() {
  local dir="$1"
  local created
  created="$(cat "$dir/created_at" 2>/dev/null || printf '0')"
  printf '%s' "$(( $(now_epoch) - created ))"
}

lock_status() {
  local dir="$1"
  if [[ ! -d "$dir" ]]; then
    printf 'unlocked\n'
    return
  fi

  printf 'locked\n'
  sed 's/^/  /' "$dir/owner" 2>/dev/null || true
  printf '  age_seconds=%s\n' "$(lock_age_seconds "$dir")"
}

acquire() {
  local resource="$1"
  local wait_seconds="${2:-900}"
  local ttl_seconds="${3:-7200}"
  local dir token started now age

  dir="$(lock_dir_for "$resource")"
  token="$(uuidgen 2>/dev/null || printf '%s-%s' "$$" "$(now_epoch)")"
  started="$(now_epoch)"

  while true; do
    if mkdir "$dir" 2>/dev/null; then
      {
        printf 'token=%s\n' "$token"
        printf 'resource=%s\n' "$resource"
        printf 'pid=%s\n' "$$"
        printf 'cwd=%s\n' "$PWD"
        printf 'created_at=%s\n' "$(now_epoch)"
        printf 'task_id=%s\n' "${KENT_TASK_ID:-unknown}"
        printf 'session_id=%s\n' "${KENT_SESSION_ID:-unknown}"
      } >"$dir/owner"
      printf '%s\n' "$(now_epoch)" >"$dir/created_at"
      printf '%s\n' "$token"
      return 0
    fi

    age="$(lock_age_seconds "$dir")"
    if [[ "$age" -gt "$ttl_seconds" ]]; then
      printf 'stale_lock_reclaimed resource=%s age_seconds=%s dir=%s\n' "$resource" "$age" "$dir" >&2
      rm -rf "$dir"
      continue
    fi

    now="$(now_epoch)"
    if [[ $(( now - started )) -ge "$wait_seconds" ]]; then
      printf 'resource_busy resource=%s lock_dir=%s\n' "$resource" "$dir" >&2
      lock_status "$dir" >&2
      return 75
    fi

    sleep 5
  done
}

acquire_any() {
  local args=("$@")
  local separator=-1
  local wait_seconds=900
  local ttl_seconds=7200
  local resources=()
  local started now resource token

  for i in "${!args[@]}"; do
    if [[ "${args[$i]}" == "--" ]]; then
      separator="$i"
      break
    fi
  done

  if [[ "$separator" -lt 1 ]]; then
    usage
    return 64
  fi

  resources=("${args[@]:0:separator}")
  if [[ ${#args[@]} -gt $(( separator + 1 )) ]]; then
    wait_seconds="${args[$(( separator + 1 ))]}"
  fi
  if [[ ${#args[@]} -gt $(( separator + 2 )) ]]; then
    ttl_seconds="${args[$(( separator + 2 ))]}"
  fi

  started="$(now_epoch)"
  while true; do
    for resource in "${resources[@]}"; do
      if token="$(acquire "$resource" 0 "$ttl_seconds" 2>/dev/null)"; then
        printf 'resource=%s\n' "$resource"
        printf 'token=%s\n' "$token"
        return 0
      fi
    done

    now="$(now_epoch)"
    if [[ $(( now - started )) -ge "$wait_seconds" ]]; then
      printf 'all_resources_busy resources=%s\n' "${resources[*]}" >&2
      for resource in "${resources[@]}"; do
        printf '%s: ' "$resource" >&2
        lock_status "$(lock_dir_for "$resource")" >&2
      done
      return 75
    fi

    sleep 5
  done
}

adb_emulators() {
  if ! command -v adb >/dev/null 2>&1; then
    printf 'adb_unavailable\n' >&2
    return 69
  fi

  adb devices | awk 'NR > 1 && $2 == "device" && $1 ~ /^emulator-/ { print $1 }'
}

adb_physical_devices() {
  if ! command -v adb >/dev/null 2>&1; then
    printf 'adb_unavailable\n' >&2
    return 69
  fi

  adb devices | awk 'NR > 1 && $2 == "device" && $1 !~ /^emulator-/ { print $1 }'
}

release() {
  local resource="$1"
  local token="$2"
  local dir current

  dir="$(lock_dir_for "$resource")"
  if [[ ! -d "$dir" ]]; then
    printf 'resource_already_unlocked resource=%s\n' "$resource" >&2
    return 0
  fi

  current="$(sed -n 's/^token=//p' "$dir/owner" 2>/dev/null || true)"
  if [[ "$current" != "$token" ]]; then
    printf 'resource_lock_token_mismatch resource=%s lock_dir=%s\n' "$resource" "$dir" >&2
    return 64
  fi

  rm -rf "$dir"
}

cmd="${1:-}"
case "$cmd" in
  acquire)
    [[ $# -ge 2 ]] || { usage; exit 64; }
    acquire "$2" "${3:-900}" "${4:-7200}"
    ;;
  acquire-any)
    shift
    acquire_any "$@"
    ;;
  release)
    [[ $# -eq 3 ]] || { usage; exit 64; }
    release "$2" "$3"
    ;;
  status)
    if [[ $# -ge 2 ]]; then
      lock_status "$(lock_dir_for "$2")"
    else
      find "$runtime_root" -maxdepth 1 -type d -name 'mobile-*.lock' -print | sort
    fi
    ;;
  adb-emulators)
    adb_emulators
    ;;
  adb-physical-devices)
    adb_physical_devices
    ;;
  *)
    usage
    exit 64
    ;;
esac
