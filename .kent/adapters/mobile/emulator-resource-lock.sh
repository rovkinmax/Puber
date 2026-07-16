#!/usr/bin/env bash
set -euo pipefail

umask 077

script_path="$(
  cd "$(dirname "${BASH_SOURCE[0]}")"
  printf '%s/%s\n' "$PWD" "$(basename "${BASH_SOURCE[0]}")"
)"
runtime_root="${KENT_RESOURCE_LOCK_DIR:-$HOME/.kent/runtime/resource-locks}"
guard_root="$runtime_root/.guards"
mkdir -p "$runtime_root" "$guard_root"

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
Physical devices are listed separately and must be used only with explicit user
permission and an explicit serial.
Locks live under ~/.kent/runtime/resource-locks so main checkouts and Kent
worktrees share them.
USAGE
}

sanitize_resource() {
  printf '%s' "$1" | tr -c 'A-Za-z0-9._=-' '_'
}

lock_dir_for() {
  printf '%s/mobile-%s.lock' "$runtime_root" "$(sanitize_resource "$1")"
}

guard_file_for() {
  printf '%s/mobile-%s.guard' "$guard_root" "$(sanitize_resource "$1")"
}

now_epoch() {
  date +%s
}

require_nonnegative_integer() {
  local label="$1"
  local value="$2"
  if [[ ! "$value" =~ ^[0-9]+$ ]]; then
    printf 'invalid_%s value=%s\n' "$label" "$value" >&2
    return 64
  fi
}

lock_age_seconds() {
  local dir="$1"
  local created
  created="$(cat "$dir/created_at" 2>/dev/null || printf '0')"
  if [[ ! "$created" =~ ^[0-9]+$ ]]; then
    created=0
  fi
  printf '%s' "$(( $(now_epoch) - created ))"
}

remove_known_lock_dir() {
  local dir="$1"
  rm -f "$dir/owner" "$dir/created_at"
  rmdir "$dir"
}

locked_status() {
  local resource="$1"
  local dir
  dir="$(lock_dir_for "$resource")"

  if [[ ! -d "$dir" ]]; then
    printf 'unlocked\n'
    return
  fi

  printf 'locked\n'
  sed -E 's/^token=.*/token=<redacted>/' "$dir/owner" 2>/dev/null |
    sed 's/^/  /' || true
  printf '  age_seconds=%s\n' "$(lock_age_seconds "$dir")"
}

locked_try_acquire() {
  local resource="$1"
  local ttl_seconds="$2"
  local dir age token

  dir="$(lock_dir_for "$resource")"
  if [[ -d "$dir" ]]; then
    age="$(lock_age_seconds "$dir")"
    if [[ "$age" -le "$ttl_seconds" ]]; then
      return 75
    fi

    printf 'stale_lock_reclaimed resource=%s age_seconds=%s dir=%s\n' \
      "$resource" "$age" "$dir" >&2
    remove_known_lock_dir "$dir"
  fi

  mkdir "$dir"
  token="$(uuidgen 2>/dev/null || printf '%s-%s' "$$" "$(now_epoch)")"
  {
    printf 'token=%s\n' "$token"
    printf 'resource=%s\n' "$resource"
    printf 'pid=%s\n' "$PPID"
    printf 'cwd=%s\n' "$PWD"
    printf 'created_at=%s\n' "$(now_epoch)"
    printf 'task_id=%s\n' "${KENT_TASK_ID:-unknown}"
    printf 'session_id=%s\n' "${KENT_SESSION_ID:-unknown}"
  } >"$dir/owner"
  printf '%s\n' "$(now_epoch)" >"$dir/created_at"
  printf '%s\n' "$token"
}

locked_release() {
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
    printf 'resource_lock_token_mismatch resource=%s lock_dir=%s\n' \
      "$resource" "$dir" >&2
    return 64
  fi

  remove_known_lock_dir "$dir"
}

locked_dispatch() {
  local operation="$1"
  local resource="$2"
  shift 2

  case "$operation" in
    try-acquire)
      [[ $# -eq 1 ]] || return 64
      locked_try_acquire "$resource" "$1"
      ;;
    release)
      [[ $# -eq 1 ]] || return 64
      locked_release "$resource" "$1"
      ;;
    status)
      [[ $# -eq 0 ]] || return 64
      locked_status "$resource"
      ;;
    *)
      printf 'unknown_locked_operation operation=%s\n' "$operation" >&2
      return 64
      ;;
  esac
}

with_resource_guard() {
  local resource="$1"
  local operation="$2"
  shift 2
  local guard_file backend

  guard_file="$(guard_file_for "$resource")"
  backend="${KENT_RESOURCE_LOCK_BACKEND:-auto}"

  case "$backend" in
    auto)
      if command -v flock >/dev/null 2>&1; then
        backend=flock
      elif command -v lockf >/dev/null 2>&1; then
        backend=lockf
      else
        printf 'resource_lock_backend_unavailable requires=flock_or_lockf\n' >&2
        return 69
      fi
      ;;
    flock)
      command -v flock >/dev/null 2>&1 || {
        printf 'resource_lock_backend_unavailable requires=flock\n' >&2
        return 69
      }
      ;;
    lockf)
      command -v lockf >/dev/null 2>&1 || {
        printf 'resource_lock_backend_unavailable requires=lockf\n' >&2
        return 69
      }
      ;;
    *)
      printf 'invalid_resource_lock_backend value=%s\n' "$backend" >&2
      return 64
      ;;
  esac

  if [[ "$backend" == "flock" ]]; then
    flock "$guard_file" \
      "$script_path" __locked "$operation" "$resource" "$@"
  else
    lockf "$guard_file" \
      "$script_path" __locked "$operation" "$resource" "$@"
  fi
}

try_acquire() {
  local resource="$1"
  local ttl_seconds="$2"
  with_resource_guard "$resource" try-acquire "$ttl_seconds"
}

acquire() {
  local resource="$1"
  local wait_seconds="${2:-900}"
  local ttl_seconds="${3:-7200}"
  local started now token attempt_status

  require_nonnegative_integer wait_seconds "$wait_seconds"
  require_nonnegative_integer ttl_seconds "$ttl_seconds"
  started="$(now_epoch)"

  while true; do
    set +e
    token="$(try_acquire "$resource" "$ttl_seconds")"
    attempt_status=$?
    set -e

    if [[ "$attempt_status" -eq 0 ]]; then
      printf '%s\n' "$token"
      return 0
    fi
    if [[ "$attempt_status" -ne 75 ]]; then
      return "$attempt_status"
    fi

    now="$(now_epoch)"
    if [[ $(( now - started )) -ge "$wait_seconds" ]]; then
      printf 'resource_busy resource=%s lock_dir=%s\n' \
        "$resource" "$(lock_dir_for "$resource")" >&2
      with_resource_guard "$resource" status >&2
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
  local started now resource token attempt_status

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
  require_nonnegative_integer wait_seconds "$wait_seconds"
  require_nonnegative_integer ttl_seconds "$ttl_seconds"

  started="$(now_epoch)"
  while true; do
    for resource in "${resources[@]}"; do
      set +e
      token="$(try_acquire "$resource" "$ttl_seconds")"
      attempt_status=$?
      set -e

      if [[ "$attempt_status" -eq 0 ]]; then
        printf 'resource=%s\n' "$resource"
        printf 'token=%s\n' "$token"
        return 0
      fi
      if [[ "$attempt_status" -ne 75 ]]; then
        return "$attempt_status"
      fi
    done

    now="$(now_epoch)"
    if [[ $(( now - started )) -ge "$wait_seconds" ]]; then
      printf 'all_resources_busy resources=%s\n' "${resources[*]}" >&2
      for resource in "${resources[@]}"; do
        printf '%s: ' "$resource" >&2
        with_resource_guard "$resource" status >&2
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

cmd="${1:-}"
case "$cmd" in
  __locked)
    [[ $# -ge 3 ]] || exit 64
    shift
    locked_dispatch "$@"
    ;;
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
    with_resource_guard "$2" release "$3"
    ;;
  status)
    if [[ $# -ge 2 ]]; then
      with_resource_guard "$2" status
    else
      find "$runtime_root" \
        -maxdepth 1 \
        -type d \
        -name 'mobile-*.lock' \
        -print |
        sort
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
