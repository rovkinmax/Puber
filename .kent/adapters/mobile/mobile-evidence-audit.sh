#!/usr/bin/env bash
set -euo pipefail

usage() {
  printf '%s\n' \
    'Usage: mobile-evidence-audit.sh <evidence-directory> [package-name]' >&2
}

[[ $# -ge 1 && $# -le 2 ]] || {
  usage
  exit 64
}

evidence_dir="$1"
package_name="${2:-}"

while [[ "$evidence_dir" != "/" && "$evidence_dir" == */ ]]; do
  evidence_dir="${evidence_dir%/}"
done

path_has_symlink_component() {
  local path="$1"
  local candidate part previous_ifs
  local -a parts

  if [[ "$path" == /* ]]; then
    candidate="/"
  else
    candidate="."
  fi

  previous_ifs="$IFS"
  IFS='/'
  read -r -a parts <<<"$path"
  IFS="$previous_ifs"

  for part in "${parts[@]}"; do
    case "$part" in
      ""|.)
        continue
        ;;
      ..)
        return 2
        ;;
    esac

    if [[ "$candidate" == "/" ]]; then
      candidate="/$part"
    else
      candidate="$candidate/$part"
    fi
    [[ ! -L "$candidate" ]] || return 0
  done

  return 1
}

if path_has_symlink_component "$evidence_dir"; then
  printf 'evidence directory path must not contain symlinks: %s\n' \
    "$evidence_dir" >&2
  exit 64
else
  path_status=$?
  if [[ "$path_status" -eq 2 ]]; then
    printf 'evidence directory path must not contain .. components: %s\n' \
      "$evidence_dir" >&2
    exit 64
  fi
fi

if [[ ! -d "$evidence_dir" ]]; then
  printf 'evidence directory not found: %s\n' "$evidence_dir" >&2
  exit 64
fi
if [[ $# -eq 2 && -z "$package_name" ]]; then
  printf 'package name must not be empty\n' >&2
  exit 64
fi

evidence_root="$(cd "$evidence_dir" && pwd -P)"
sensitive_pattern='"?(authorization|proxy-authorization)"?'
sensitive_pattern+='[[:space:]]*[:=][[:space:]]*"?(bearer|basic)[[:space:]]+'
sensitive_pattern+='|bearer[[:space:]]+[A-Za-z0-9._~+/-]{8,}'
sensitive_pattern+='|"(access_?token|refresh_?token|id_?token|client_?secret)"'
sensitive_pattern+='[[:space:]]*:'
sensitive_pattern+='|"(password|email|phone(_?number)?|first_?name|last_?name)"'
sensitive_pattern+='[[:space:]]*:'
sensitive_pattern+='|(^|[[:space:]])'
sensitive_pattern+='(access_?token|refresh_?token|id_?token|client_?secret|password)'
sensitive_pattern+='[[:space:]]*='
sensitive_pattern+='|"(set-)?cookie"[[:space:]]*:'
sensitive_pattern+='|[[:alnum:]._%+-]+@[[:alnum:].-]+\.[[:alpha:]]{2,}'
sensitive_pattern+='|[+][1-9][0-9][0-9 ()-]{7,}[0-9]'
sensitive_pattern+='|-----BEGIN ([A-Z0-9 ]+ )?PRIVATE KEY-----'
sensitive_pattern+='|gh[pousr]_[A-Za-z0-9]{20,}|xox[baprs]-[A-Za-z0-9-]{10,}'

logcat_pattern='[0-9]{2}-[0-9]{2}[[:space:]]+'
logcat_pattern+='[0-9]{2}:[0-9]{2}:[0-9]{2}\.[0-9]{3}'
logcat_pattern+='[[:space:]]+[0-9]+[[:space:]]+[0-9]+'
logcat_pattern+='[[:space:]]+[VDIWEF][[:space:]]'
logcat_pattern+='|[VDIWEF]/[^[:space:]]+[[:space:]]*\([[:space:]]*[0-9]+\):'

signal_pattern='FATAL EXCEPTION|ANR in|Process:|Fatal signal'
signal_pattern+='|Force finishing activity|has died|am_crash|am_anr'

unsafe=0
files_scanned=0

report_unsafe() {
  local relative_path="$1"
  local reason="$2"
  printf 'unsafe_evidence_file=%s reason=%s\n' \
    "$relative_path" "$reason" >&2
  unsafe=1
}

if ! find "$evidence_root" -print >/dev/null; then
  printf 'evidence_audit_status=failed reason=unreadable_tree\n' >&2
  exit 65
fi

while IFS= read -r -d '' path; do
  relative_path="${path#"$evidence_root"/}"
  report_unsafe "$relative_path" "symlink_not_allowed"
done < <(find "$evidence_root" -type l -print0)

while IFS= read -r -d '' path; do
  relative_path="${path#"$evidence_root"/}"
  report_unsafe "$relative_path" "unsupported_file_type"
done < <(
  find "$evidence_root" \
    ! -type d \
    ! -type f \
    ! -type l \
    -print0
)

while IFS= read -r -d '' path; do
  relative_path="${path#"$evidence_root"/}"
  basename_lower="$(basename "$path" | tr '[:upper:]' '[:lower:]')"
  files_scanned=$((files_scanned + 1))

  if [[ ! -r "$path" ]]; then
    report_unsafe "$relative_path" "unreadable_file"
    continue
  fi

  case "$basename_lower" in
    *logcat*|*device-log*|*system-log*)
      report_unsafe "$relative_path" "broad_log_filename"
      ;;
  esac

  set +e
  LC_ALL=C grep -a -E -i -q -- "$sensitive_pattern" "$path"
  sensitive_status=$?
  set -e
  case "$sensitive_status" in
    0)
      report_unsafe "$relative_path" "sensitive_content_marker"
      ;;
    1)
      ;;
    *)
      report_unsafe "$relative_path" "scan_error"
      ;;
  esac

  set +e
  LC_ALL=C grep -a -E -i -q -- "$logcat_pattern" "$path"
  logcat_status=$?
  set -e
  case "$logcat_status" in
    0)
      if [[ -z "$package_name" ]]; then
        report_unsafe "$relative_path" "logcat_content_requires_package"
        continue
      fi

      set +e
      LC_ALL=C grep -a -E -i -- "$logcat_pattern" "$path" |
        grep -F -v -- "$package_name" >/dev/null
      package_statuses=("${PIPESTATUS[@]}")
      set -e
      if [[ "${package_statuses[0]}" -ne 0 ||
        "${package_statuses[1]}" -gt 1 ]]; then
        report_unsafe "$relative_path" "scan_error"
      elif [[ "${package_statuses[1]}" -eq 0 ]]; then
        report_unsafe "$relative_path" "unscoped_logcat_content"
      fi

      set +e
      LC_ALL=C grep -a -E -i -- "$logcat_pattern" "$path" |
        grep -E -i -v -- "$signal_pattern" >/dev/null
      signal_statuses=("${PIPESTATUS[@]}")
      set -e
      if [[ "${signal_statuses[0]}" -ne 0 ||
        "${signal_statuses[1]}" -gt 1 ]]; then
        report_unsafe "$relative_path" "scan_error"
      elif [[ "${signal_statuses[1]}" -eq 0 ]]; then
        report_unsafe "$relative_path" "non_signal_logcat_content"
      fi
      ;;
    1)
      ;;
    *)
      report_unsafe "$relative_path" "scan_error"
      ;;
  esac
done < <(find "$evidence_root" -type f -print0)

if [[ "$unsafe" -ne 0 ]]; then
  printf 'evidence_audit_status=failed files_scanned=%s\n' \
    "$files_scanned" >&2
  exit 65
fi

printf 'evidence_audit_status=passed\n'
printf 'evidence_dir=%s\n' "$evidence_root"
printf 'files_scanned=%s\n' "$files_scanned"
