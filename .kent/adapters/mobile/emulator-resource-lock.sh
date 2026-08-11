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
if [[ -z "${KENT_RESOURCE_LOCK_OWNER_PID:-}" ]]; then
  export KENT_RESOURCE_LOCK_OWNER_PID="$PPID"
fi

usage() {
  cat >&2 <<'USAGE'
Usage:
  emulator-resource-lock.sh acquire <resource> [wait_seconds] [ttl_seconds]
  emulator-resource-lock.sh acquire-any <resource>... -- [wait_seconds] [ttl_seconds]
  emulator-resource-lock.sh resume <resource> <token>
  emulator-resource-lock.sh resume-owned <resource>
  emulator-resource-lock.sh release <resource> <token>
  emulator-resource-lock.sh status [resource]
  emulator-resource-lock.sh adb-emulators [any|phone|tv]
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
  local dir age token owner_pid

  owner_pid="$KENT_RESOURCE_LOCK_OWNER_PID"
  require_nonnegative_integer owner_pid "$owner_pid"

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
    printf 'pid=%s\n' "$owner_pid"
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

locked_resume() {
  local resource="$1"
  local token="$2"
  local dir current owner_pid

  owner_pid="$KENT_RESOURCE_LOCK_OWNER_PID"
  require_nonnegative_integer owner_pid "$owner_pid"

  dir="$(lock_dir_for "$resource")"
  if [[ -d "$dir" ]]; then
    current="$(sed -n 's/^token=//p' "$dir/owner" 2>/dev/null || true)"
    if [[ "$current" != "$token" ]]; then
      printf 'resource_lock_token_mismatch resource=%s lock_dir=%s\n' \
        "$resource" "$dir" >&2
      return 75
    fi
  else
    mkdir "$dir"
  fi

  {
    printf 'token=%s\n' "$token"
    printf 'resource=%s\n' "$resource"
    printf 'pid=%s\n' "$owner_pid"
    printf 'cwd=%s\n' "$PWD"
    printf 'created_at=%s\n' "$(now_epoch)"
    printf 'task_id=%s\n' "${KENT_TASK_ID:-unknown}"
    printf 'session_id=%s\n' "${KENT_SESSION_ID:-unknown}"
  } >"$dir/owner"
  printf '%s\n' "$(now_epoch)" >"$dir/created_at"
  printf '%s\n' "$token"
}

locked_resume_owned() {
  local resource="$1"
  local dir token owner_task_id task_id owner_pid

  task_id="${KENT_TASK_ID:-}"
  if [[ -z "$task_id" || "$task_id" == "unknown" ]]; then
    printf 'resource_lock_task_identity_required resource=%s\n' \
      "$resource" >&2
    return 64
  fi

  owner_pid="$KENT_RESOURCE_LOCK_OWNER_PID"
  require_nonnegative_integer owner_pid "$owner_pid"

  dir="$(lock_dir_for "$resource")"
  if [[ ! -d "$dir" ]]; then
    printf 'resource_lock_not_owned resource=%s task_id=%s\n' \
      "$resource" "$task_id" >&2
    return 75
  fi

  token="$(sed -n 's/^token=//p' "$dir/owner" 2>/dev/null || true)"
  owner_task_id="$(
    sed -n 's/^task_id=//p' "$dir/owner" 2>/dev/null || true
  )"
  if [[ -z "$token" || -z "$owner_task_id" ||
    "$owner_task_id" == "unknown" ]]; then
    printf 'resource_lock_owner_metadata_missing resource=%s\n' \
      "$resource" >&2
    return 75
  fi
  if [[ "$owner_task_id" != "$task_id" ]]; then
    printf 'resource_lock_owned_by_other_task resource=%s owner_task_id=%s task_id=%s\n' \
      "$resource" "$owner_task_id" "$task_id" >&2
    return 75
  fi

  {
    printf 'token=%s\n' "$token"
    printf 'resource=%s\n' "$resource"
    printf 'pid=%s\n' "$owner_pid"
    printf 'cwd=%s\n' "$PWD"
    printf 'created_at=%s\n' "$(now_epoch)"
    printf 'task_id=%s\n' "$task_id"
    printf 'session_id=%s\n' "${KENT_SESSION_ID:-unknown}"
  } >"$dir/owner"
  printf '%s\n' "$(now_epoch)" >"$dir/created_at"
  printf '%s\n' "$token"
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
    resume)
      [[ $# -eq 1 ]] || return 64
      locked_resume "$resource" "$1"
      ;;
    resume-owned)
      [[ $# -eq 0 ]] || return 64
      locked_resume_owned "$resource"
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
  local form_factor="${1:-any}"
  local serial characteristics model product_name identity

  if ! command -v adb >/dev/null 2>&1; then
    printf 'adb_unavailable\n' >&2
    return 69
  fi

  case "$form_factor" in
    any|phone|tv)
      ;;
    *)
      printf 'invalid_emulator_form_factor value=%s\n' "$form_factor" >&2
      return 64
      ;;
  esac

  while IFS= read -r serial; do
    if [[ "$form_factor" == "any" ]]; then
      printf '%s\n' "$serial"
      continue
    fi

    if ! characteristics="$(
      adb -s "$serial" shell getprop ro.build.characteristics \
        </dev/null 2>/dev/null |
        tr -d '\r[:space:]'
    )" ||
      ! model="$(
        adb -s "$serial" shell getprop ro.product.model \
          </dev/null 2>/dev/null |
          tr -d '\r[:space:]' |
          tr '[:upper:]' '[:lower:]'
      )" ||
      ! product_name="$(
        adb -s "$serial" shell getprop ro.product.name \
          </dev/null 2>/dev/null |
          tr -d '\r[:space:]' |
          tr '[:upper:]' '[:lower:]'
      )"; then
      printf 'emulator_form_factor_probe_failed serial=%s\n' "$serial" >&2
      return 69
    fi

    identity=",$characteristics,$model,$product_name,"
    case "$identity" in
      *,tv,*|*atv*|*google-tv*|*google_tv*|*television*)
        if [[ "$form_factor" == "tv" ]]; then
          printf '%s\n' "$serial"
        fi
        ;;
      *,watch,*|*,automotive,*)
        ;;
      *)
        if [[ "$form_factor" == "phone" ]]; then
          printf '%s\n' "$serial"
        fi
        ;;
    esac
  done < <(
    adb devices |
      awk 'NR > 1 && $2 == "device" && $1 ~ /^emulator-/ { print $1 }'
  )
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
  resume)
    [[ $# -eq 3 ]] || { usage; exit 64; }
    with_resource_guard "$2" resume "$3"
    ;;
  resume-owned)
    [[ $# -eq 2 ]] || { usage; exit 64; }
    with_resource_guard "$2" resume-owned
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
    [[ $# -le 2 ]] || { usage; exit 64; }
    adb_emulators "${2:-any}"
    ;;
  adb-physical-devices)
    adb_physical_devices
    ;;
  *)
    usage
    exit 64
    ;;
esac
