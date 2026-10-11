#!/usr/bin/env bash
set -euo pipefail

umask 077

script_path="$(
  cd "$(dirname "${BASH_SOURCE[0]}")"
  printf '%s/%s\n' "$PWD" "$(basename "${BASH_SOURCE[0]}")"
)"
runtime_root="${KENT_RESOURCE_LOCK_DIR:-$HOME/.kent/runtime/resource-locks}"
guard_root="$runtime_root/.guards"

read -r -d '' storage_tool_code <<'PY' || true
import ctypes
import hashlib
import os
import platform
import re
import secrets
import stat
import sys

MAX_OWNER = 4096
MAX_REQUEST = 1024
RESOURCE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._=-]{0,127}$")
TASK = re.compile(r"^task-[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")
ANY_TASK = re.compile(r"^(?:[A-Z][A-Z0-9]*-[0-9]+|task-[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12})$")
UUID = re.compile(r"^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")
HEX64 = re.compile(r"^[0-9a-f]{64}$")
SCRIPT_TOKEN = re.compile(r"^[A-Za-z0-9_-]{43}$")
LEGACY_TOKEN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._=-]{0,127}$")
LEGACY = ("token", "resource", "pid", "cwd", "created_at", "task_id", "session_id")
SCRIPT = ("owner_schema", "owner_kind", "token", "resource", "task_id",
          "context_sha256", "pid", "process_platform", "boot_id",
          "process_start", "pgid", "cwd", "created_at")
SNAPSHOT = ("token", "resource", "pid", "cwd", "created_at", "task_id",
            "session_id", "owner_schema", "owner_kind", "context_sha256",
            "process_platform", "boot_id", "process_start", "pgid")
DIR_FLAGS = os.O_RDONLY | getattr(os, "O_DIRECTORY", 0) | getattr(os, "O_NOFOLLOW", 0)
READ_FLAGS = os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0)


def decimal(value, positive=False):
    if not re.fullmatch(r"[0-9]{1,20}", value):
        return False
    return int(value) > 0 if positive else True


def cwd_valid(value):
    return (value.startswith("/") and len(value) <= 4096
            and not any(ord(char) < 32 or ord(char) == 127 for char in value))


def parse_owner(raw, resource):
    if len(raw) > MAX_OWNER or not raw.endswith(b"\n") or b"\r" in raw or b"\0" in raw:
        raise ValueError("owner_bytes")
    values = {}
    for line in raw[:-1].decode("ascii", "strict").split("\n"):
        if not line or "=" not in line:
            raise ValueError("owner_line")
        key, value = line.split("=", 1)
        if key in values or not value:
            raise ValueError("owner_duplicate")
        values[key] = value
    if tuple(values) == LEGACY:
        kind, fields = "LegacySessionOwner", LEGACY
        if (not LEGACY_TOKEN.fullmatch(values["token"])
                or not decimal(values["pid"], True)
                or not decimal(values["created_at"])
                or not ANY_TASK.fullmatch(values["task_id"])
                or not UUID.fullmatch(values["session_id"])):
            raise ValueError("legacy_fields")
    elif tuple(values) == SCRIPT:
        kind, fields = "ScriptOwnerV1", SCRIPT
        if (values["owner_schema"] != "mobile-script-owner-v1"
                or values["owner_kind"] != "script"
                or not SCRIPT_TOKEN.fullmatch(values["token"])
                or not TASK.fullmatch(values["task_id"])
                or not HEX64.fullmatch(values["context_sha256"])
                or values["process_platform"] not in ("Darwin", "Linux")
                or not UUID.fullmatch(values["boot_id"])
                or not decimal(values["pid"], True)
                or not decimal(values["process_start"], True)
                or not decimal(values["pgid"], True)
                or not decimal(values["created_at"])):
            raise ValueError("script_fields")
    else:
        raise ValueError("owner_fields")
    if set(values) != set(fields) or values["resource"] != resource:
        raise ValueError("owner_shape")
    if not RESOURCE.fullmatch(resource) or not cwd_valid(values["cwd"]):
        raise ValueError("owner_identity")
    canonical = b"".join((key + "=" + values[key] + "\n").encode("ascii") for key in fields)
    if raw != canonical:
        raise ValueError("owner_order")
    return kind, values


def open_tree(path, create=False, private=False):
    if not os.path.isabs(path) or os.path.normpath(path) != path:
        raise ValueError("directory_path")
    current = os.open("/", DIR_FLAGS)
    try:
        parts = [part for part in path.split(os.sep) if part]
        for index, part in enumerate(parts):
            try:
                child = os.open(part, DIR_FLAGS, dir_fd=current)
            except FileNotFoundError:
                if not create:
                    raise
                try:
                    os.mkdir(part, 0o700, dir_fd=current)
                except FileExistsError:
                    pass
                else:
                    os.fsync(current)
                child = os.open(part, DIR_FLAGS, dir_fd=current)
            os.close(current)
            current = child
            info = os.fstat(current)
            if not stat.S_ISDIR(info.st_mode):
                raise ValueError("not_directory")
            if private and index == len(parts) - 1:
                if info.st_uid != os.geteuid() or info.st_mode & 0o077:
                    raise ValueError("directory_owner_or_mode")
        return current
    except Exception:
        os.close(current)
        raise


def read_at(directory_fd, name, limit):
    fd = os.open(name, READ_FLAGS, dir_fd=directory_fd)
    try:
        before = os.fstat(fd)
        if (not stat.S_ISREG(before.st_mode) or before.st_uid != os.geteuid()
                or before.st_nlink != 1 or before.st_mode & 0o077
                or before.st_size > limit):
            raise ValueError("file_identity")
        data = bytearray()
        while len(data) <= limit:
            chunk = os.read(fd, min(1024, limit + 1 - len(data)))
            if not chunk:
                break
            data.extend(chunk)
        after = os.fstat(fd)
        if (len(data) != before.st_size or len(data) > limit
                or (before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns)
                != (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns)):
            raise ValueError("file_changed")
        return bytes(data)
    finally:
        os.close(fd)


def snapshot(directory, resource):
    try:
        parent = open_tree(os.path.dirname(directory))
    except FileNotFoundError:
        return "Absent", "none", {}
    except Exception:
        return "UnknownOwner", "unknown", {}
    try:
        name = os.path.basename(directory)
        try:
            info = os.stat(name, dir_fd=parent, follow_symlinks=False)
        except FileNotFoundError:
            return "Absent", "none", {}
        if not stat.S_ISDIR(info.st_mode):
            return "UnknownOwner", "unknown", {}
        try:
            child = os.open(name, DIR_FLAGS, dir_fd=parent)
        except Exception:
            return "UnknownOwner", "unknown", {}
        try:
            info = os.fstat(child)
            if (info.st_uid != os.geteuid() or info.st_mode & 0o077
                    or sorted(os.listdir(child)) != ["created_at", "owner"]):
                return "UnknownOwner", "unknown", {}
            raw = read_at(child, "owner", MAX_OWNER)
            stamp = read_at(child, "created_at", 32)
            kind, values = parse_owner(raw, resource)
            if stamp != (values["created_at"] + "\n").encode("ascii"):
                return "UnknownOwner", "unknown", {}
            return kind, hashlib.sha256(raw).hexdigest(), values
        except Exception:
            return "UnknownOwner", "unknown", {}
        finally:
            os.close(child)
    finally:
        os.close(parent)


def write_at(directory_fd, name, data):
    temporary = ".tmp-" + secrets.token_hex(12)
    fd = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL
                 | getattr(os, "O_NOFOLLOW", 0), 0o600, dir_fd=directory_fd)
    try:
        view = memoryview(data)
        while view:
            count = os.write(fd, view)
            view = view[count:]
        os.fsync(fd)
    finally:
        os.close(fd)
    os.replace(temporary, name, src_dir_fd=directory_fd, dst_dir_fd=directory_fd)
    os.fsync(directory_fd)


def write_owner(directory, resource, expected, expected_kind, raw):
    kind, values = parse_owner(raw, resource)
    wanted = "LegacySessionOwner" if expected_kind == "session" else "ScriptOwnerV1"
    if kind != wanted:
        raise ValueError("owner_type")
    parent = open_tree(os.path.dirname(directory), private=True)
    try:
        name = os.path.basename(directory)
        if expected == "absent":
            os.mkdir(name, 0o700, dir_fd=parent)
            os.fsync(parent)
        else:
            old_kind, old_hash, _ = snapshot(directory, resource)
            if old_kind != wanted or old_hash != expected:
                raise ValueError("owner_changed")
        child = os.open(name, DIR_FLAGS, dir_fd=parent)
        try:
            info = os.fstat(child)
            if info.st_uid != os.geteuid() or info.st_mode & 0o077:
                raise ValueError("directory_identity")
            write_at(child, "created_at", (values["created_at"] + "\n").encode("ascii"))
            write_at(child, "owner", raw)
        finally:
            os.close(child)
    finally:
        os.close(parent)
    result_kind, result_hash, _ = snapshot(directory, resource)
    if result_kind != wanted or result_hash != hashlib.sha256(raw).hexdigest():
        raise ValueError("owner_readback")
    return result_hash


def remove_owner(directory, resource, expected, expected_kind):
    kind, digest, _ = snapshot(directory, resource)
    wanted = "LegacySessionOwner" if expected_kind == "session" else "ScriptOwnerV1"
    if kind != wanted or digest != expected:
        raise ValueError("owner_changed")
    parent = open_tree(os.path.dirname(directory), private=True)
    try:
        name = os.path.basename(directory)
        child = os.open(name, DIR_FLAGS, dir_fd=parent)
        try:
            info = os.fstat(child)
            if info.st_uid != os.geteuid() or info.st_mode & 0o077:
                raise ValueError("directory_identity")
            raw = read_at(child, "owner", MAX_OWNER)
            actual_kind, values = parse_owner(raw, resource)
            if actual_kind != wanted or hashlib.sha256(raw).hexdigest() != expected:
                raise ValueError("owner_changed")
            if sorted(os.listdir(child)) != ["created_at", "owner"]:
                raise ValueError("directory_contents")
            stamp = read_at(child, "created_at", 32)
            if stamp != (values["created_at"] + "\n").encode("ascii"):
                raise ValueError("created_at_changed")
            os.unlink("owner", dir_fd=child)
            os.unlink("created_at", dir_fd=child)
            os.fsync(child)
        finally:
            os.close(child)
        os.rmdir(name, dir_fd=parent)
        os.fsync(parent)
    finally:
        os.close(parent)


def guard_file(path, create):
    try:
        parent = open_tree(os.path.dirname(path), create=create, private=True)
    except FileNotFoundError:
        return 4
    try:
        flags = os.O_RDWR | getattr(os, "O_NOFOLLOW", 0) | getattr(os, "O_CLOEXEC", 0)
        if create:
            flags |= os.O_CREAT
        for attempt in range(3):
            try:
                fd = os.open(os.path.basename(path), flags, 0o600, dir_fd=parent)
                break
            except FileNotFoundError:
                if not create:
                    return 4
                if attempt == 2:
                    return 1
        try:
            info = os.fstat(fd)
            if (not stat.S_ISREG(info.st_mode) or info.st_uid != os.geteuid()
                    or info.st_nlink != 1 or info.st_mode & 0o077 or info.st_size != 0):
                return 1
            if create:
                os.fsync(fd)
                os.fsync(parent)
            return 0
        finally:
            os.close(fd)
    finally:
        os.close(parent)


def process_identity(pid_text):
    if not decimal(pid_text, True) or int(pid_text) < 2 or int(pid_text) > 2147483647:
        raise ValueError("pid")
    pid = int(pid_text)
    system = platform.system()
    if system == "Linux":
        fd = os.open("/proc/%d/stat" % pid, READ_FLAGS)
        try:
            raw = os.read(fd, 4097)
        finally:
            os.close(fd)
        if len(raw) > 4096 or b"\0" in raw:
            raise ValueError("stat_size")
        prefix, separator, tail = raw.rpartition(b")")
        if not separator or not prefix.startswith((str(pid) + " (").encode("ascii")):
            raise ValueError("stat_pid")
        fields = tail.split()
        if len(fields) <= 19:
            raise ValueError("stat_fields")
        pgid, start = fields[2].decode("ascii"), fields[19].decode("ascii")
        boot_fd = os.open("/proc/sys/kernel/random/boot_id", READ_FLAGS)
        try:
            boot = os.read(boot_fd, 128).decode("ascii", "strict").strip().lower()
        finally:
            os.close(boot_fd)
        if not UUID.fullmatch(boot) or not decimal(pgid, True) or not decimal(start, True):
            raise ValueError("linux_identity")
        return system, boot, start, pgid
    if system == "Darwin":
        class ProcBsdInfo(ctypes.Structure):
            _fields_ = [
                ("pbi_flags", ctypes.c_uint32), ("pbi_status", ctypes.c_uint32),
                ("pbi_xstatus", ctypes.c_uint32), ("pbi_pid", ctypes.c_uint32),
                ("pbi_ppid", ctypes.c_uint32), ("pbi_uid", ctypes.c_uint32),
                ("pbi_gid", ctypes.c_uint32), ("pbi_ruid", ctypes.c_uint32),
                ("pbi_rgid", ctypes.c_uint32), ("pbi_svuid", ctypes.c_uint32),
                ("pbi_svgid", ctypes.c_uint32), ("rfu_1", ctypes.c_uint32),
                ("pbi_comm", ctypes.c_char * 16), ("pbi_name", ctypes.c_char * 32),
                ("pbi_nfiles", ctypes.c_uint32), ("pbi_pgid", ctypes.c_uint32),
                ("pbi_pjobc", ctypes.c_uint32), ("e_tdev", ctypes.c_uint32),
                ("e_tpgid", ctypes.c_int32), ("pbi_nice", ctypes.c_int32),
                ("pbi_start_tvsec", ctypes.c_uint64), ("pbi_start_tvusec", ctypes.c_uint64)
            ]
        libproc = ctypes.CDLL("/usr/lib/libproc.dylib", use_errno=True)
        call = libproc.proc_pidinfo
        call.argtypes = [ctypes.c_int, ctypes.c_int, ctypes.c_uint64,
                         ctypes.c_void_p, ctypes.c_int]
        call.restype = ctypes.c_int
        info = ProcBsdInfo()
        size = ctypes.sizeof(info)
        if call(pid, 3, 0, ctypes.byref(info), size) < size or info.pbi_pid != pid:
            raise ValueError("proc_pidinfo")
        libsystem = ctypes.CDLL("/usr/lib/libSystem.B.dylib", use_errno=True)
        sysctl = libsystem.sysctlbyname
        sysctl.argtypes = [ctypes.c_char_p, ctypes.c_void_p,
                           ctypes.POINTER(ctypes.c_size_t), ctypes.c_void_p,
                           ctypes.c_size_t]
        sysctl.restype = ctypes.c_int
        boot_buffer = ctypes.create_string_buffer(128)
        boot_size = ctypes.c_size_t(128)
        if sysctl(b"kern.bootsessionuuid", boot_buffer, ctypes.byref(boot_size),
                  None, 0) != 0 or boot_size.value > 128:
            raise ValueError("boot_uuid")
        boot = boot_buffer.value.decode("ascii", "strict").strip().lower()
        start = str(info.pbi_start_tvsec * 1000000 + info.pbi_start_tvusec)
        pgid = str(info.pbi_pgid)
        if not UUID.fullmatch(boot) or not decimal(start, True) or not decimal(pgid, True):
            raise ValueError("darwin_identity")
        return system, boot, start, pgid
    raise ValueError("platform")


def parse_request(operation):
    raw = sys.stdin.buffer.read(MAX_REQUEST + 1)
    if len(raw) > MAX_REQUEST or not raw.endswith(b"\n") or b"\r" in raw or b"\0" in raw:
        raise ValueError("request_bytes")
    lines = raw[:-1].decode("ascii", "strict").split("\n")
    keys = ["schema", "task_id", "context_sha256", "pid"]
    if operation == "release":
        keys.extend(("token", "owner_sha256"))
    if len(lines) != len(keys):
        raise ValueError("request_lines")
    values = {}
    for expected, line in zip(keys, lines):
        if "=" not in line:
            raise ValueError("request_key")
        key, value = line.split("=", 1)
        if key != expected or not value:
            raise ValueError("request_order")
        values[key] = value
    if (values["schema"] != "mobile-script-lease-request-v1"
            or not TASK.fullmatch(values["task_id"])
            or not HEX64.fullmatch(values["context_sha256"])
            or not decimal(values["pid"], True)
            or int(values["pid"]) < 2 or int(values["pid"]) > 2147483647):
        raise ValueError("request_fields")
    if operation == "release":
        if (not SCRIPT_TOKEN.fullmatch(values["token"])
                or not HEX64.fullmatch(values["owner_sha256"])):
            raise ValueError("release_fields")
    result = [values["task_id"], values["context_sha256"], values["pid"]]
    if operation == "release":
        result.extend((values["token"], values["owner_sha256"]))
    return "\t".join(result)


def main():
    command = sys.argv[1]
    if command == "snapshot":
        kind, digest, values = snapshot(sys.argv[2], sys.argv[3])
        fields = [kind, digest] + [values.get(key, "-") for key in SNAPSHOT]
        print("\t".join(fields))
        return 0
    if command == "write":
        raw = sys.stdin.buffer.read(MAX_OWNER + 1)
        if len(raw) > MAX_OWNER:
            return 1
        print(write_owner(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5], raw))
        return 0
    if command == "remove":
        remove_owner(sys.argv[2], sys.argv[3], sys.argv[4], sys.argv[5])
        return 0
    if command == "ensure-roots":
        for path in sys.argv[2:]:
            fd = open_tree(path, create=True, private=True)
            os.close(fd)
        return 0
    if command == "guard-init":
        return guard_file(sys.argv[2], True)
    if command == "guard-check":
        return guard_file(sys.argv[2], False)
    if command == "identity":
        print("\t".join(process_identity(sys.argv[2])))
        return 0
    if command == "request":
        print(parse_request(sys.argv[2]))
        return 0
    return 64


try:
    raise SystemExit(main())
except Exception:
    raise SystemExit(1)
PY

storage_tool() {
  python3 -c "$storage_tool_code" "$@"
}

if [[ -z "${KENT_RESOURCE_LOCK_OWNER_PID:-}" ]]; then
  export KENT_RESOURCE_LOCK_OWNER_PID="$PPID"
fi

ensure_runtime_roots() {
  storage_tool ensure-roots "$runtime_root" "$guard_root" || {
    printf 'resource_lock_storage_identity_invalid\n' >&2
    return 69
  }
}

task_id_is_valid() {
  local task_id="$1"
  [[ "$task_id" =~ ^[A-Z][A-Z0-9]*-[0-9]+$ ||
    "$task_id" =~ ^task-[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$ ]]
}

session_id_is_valid() {
  local session_id="$1"
  [[ "$session_id" =~ ^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$ ]]
}

script_task_id_is_valid() {
  [[ "$1" =~ ^task-[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$ ]]
}

context_sha256_is_valid() {
  [[ "$1" =~ ^[0-9a-f]{64}$ ]]
}

resource_is_valid() {
  [[ "$1" =~ ^[A-Za-z0-9][A-Za-z0-9._=-]{0,127}$ ]]
}

require_lock_identity() {
  local task_id="${KENT_TASK_ID:-}"
  local session_id="${KENT_SESSION_ID:-}"

  if ! task_id_is_valid "$task_id"; then
    printf 'resource_lock_task_identity_invalid\n' >&2
    return 64
  fi
  if ! session_id_is_valid "$session_id"; then
    printf 'resource_lock_session_identity_invalid\n' >&2
    return 64
  fi
}

LOCK_OWNER_TOKEN=""
LOCK_OWNER_TASK_ID=""
LOCK_OWNER_RESOURCE=""
OWNER_KIND=""
OWNER_HASH=""
OWNER_TOKEN=""
OWNER_RESOURCE=""
OWNER_PID=""
OWNER_CWD=""
OWNER_CREATED_AT=""
OWNER_TASK_ID=""
OWNER_SESSION_ID=""
OWNER_SCHEMA=""
OWNER_OWNER_KIND=""
OWNER_CONTEXT_SHA256=""
OWNER_PROCESS_PLATFORM=""
OWNER_BOOT_ID=""
OWNER_PROCESS_START=""
OWNER_PGID=""

read_owner_snapshot() {
  local directory="$1"
  local resource="$2"
  local output

  output="$(storage_tool snapshot "$directory" "$resource")" || return 1
  IFS=$'\t' read -r \
    OWNER_KIND OWNER_HASH OWNER_TOKEN OWNER_RESOURCE OWNER_PID OWNER_CWD \
    OWNER_CREATED_AT OWNER_TASK_ID OWNER_SESSION_ID OWNER_SCHEMA \
    OWNER_OWNER_KIND OWNER_CONTEXT_SHA256 OWNER_PROCESS_PLATFORM \
    OWNER_BOOT_ID OWNER_PROCESS_START OWNER_PGID <<<"$output"
  [[ -n "$OWNER_KIND" ]]
}

write_owner_record() {
  local directory="$1"
  local resource="$2"
  local expected="$3"
  local owner_kind="$4"
  local content="$5"

  printf '%s\n' "$content" |
    storage_tool write "$directory" "$resource" "$expected" "$owner_kind"
}

remove_owner_record() {
  storage_tool remove "$1" "$2" "$3" "$4"
}

script_request() {
  local operation="$1"
  local output

  output="$(storage_tool request "$operation")" || {
    printf 'resource_lock_script_request_invalid\n' >&2
    return 64
  }
  IFS=$'\t' read -r \
    SCRIPT_TASK_ID SCRIPT_CONTEXT_SHA256 SCRIPT_PID SCRIPT_TOKEN \
    SCRIPT_OWNER_SHA256 <<<"$output"
}

usage() {
  cat >&2 <<'USAGE'
Usage:
  emulator-resource-lock.sh acquire <resource> [wait_seconds] [ttl_seconds]
  emulator-resource-lock.sh acquire-any <resource>... -- [wait_seconds] [ttl_seconds]
  emulator-resource-lock.sh resume <resource> <token>
  emulator-resource-lock.sh resume-owned <resource>
  emulator-resource-lock.sh release <resource> <token>
  emulator-resource-lock.sh status [resource]
  emulator-resource-lock.sh acquire-script <resource> [wait_seconds]
  emulator-resource-lock.sh acquire-script-any <resource>... -- [wait_seconds]
  emulator-resource-lock.sh release-script <resource>  # request on stdin
  emulator-resource-lock.sh inspect-script-recovery <resource>
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
  local created="$1"
  printf '%s' "$(( $(now_epoch) - created ))"
}

session_owner_content() {
  local token="$1"
  local resource="$2"
  local pid="$3"
  local cwd="$4"
  local created_at="$5"
  local task_id="$6"
  local session_id="$7"

  printf 'token=%s\n' "$token"
  printf 'resource=%s\n' "$resource"
  printf 'pid=%s\n' "$pid"
  printf 'cwd=%s\n' "$cwd"
  printf 'created_at=%s\n' "$created_at"
  printf 'task_id=%s\n' "$task_id"
  printf 'session_id=%s\n' "$session_id"
}

locked_status() {
  local resource="$1" dir
  dir="$(lock_dir_for "$resource")"

  read_owner_snapshot "$dir" "$resource" || {
    printf 'resource_lock_owner_metadata_invalid\n' >&2
    return 75
  }
  if [[ "$OWNER_KIND" == "Absent" ]]; then
    printf 'unlocked\n'
    return
  fi

  printf 'locked\n'
  if [[ "$OWNER_KIND" == "UnknownOwner" ]]; then
    printf '  owner_kind=UnknownOwner\n'
    return
  fi
  if [[ "$OWNER_KIND" == "LegacySessionOwner" ]]; then
    printf '  token=<redacted>\n'
    printf '  resource=%s\n' "$OWNER_RESOURCE"
    printf '  pid=%s\n' "$OWNER_PID"
    printf '  cwd=%s\n' "$OWNER_CWD"
    printf '  created_at=%s\n' "$OWNER_CREATED_AT"
    printf '  task_id=%s\n' "$OWNER_TASK_ID"
    printf '  session_id=%s\n' "$OWNER_SESSION_ID"
  else
    printf '  owner_schema=%s\n' "$OWNER_SCHEMA"
    printf '  owner_kind=%s\n' "$OWNER_OWNER_KIND"
    printf '  token=<redacted>\n'
    printf '  resource=%s\n' "$OWNER_RESOURCE"
    printf '  task_id=%s\n' "$OWNER_TASK_ID"
    printf '  context_sha256=%s\n' "$OWNER_CONTEXT_SHA256"
    printf '  pid=%s\n' "$OWNER_PID"
    printf '  process_platform=%s\n' "$OWNER_PROCESS_PLATFORM"
    printf '  boot_id=%s\n' "$OWNER_BOOT_ID"
    printf '  process_start=%s\n' "$OWNER_PROCESS_START"
    printf '  pgid=%s\n' "$OWNER_PGID"
    printf '  cwd=%s\n' "$OWNER_CWD"
    printf '  created_at=%s\n' "$OWNER_CREATED_AT"
  fi
  printf '  age_seconds=%s\n' "$(lock_age_seconds "$OWNER_CREATED_AT")"
}

locked_try_acquire() {
  local resource="$1"
  local ttl_seconds="$2"
  local dir age token owner_pid cwd created_at content

  require_lock_identity || return $?
  owner_pid="$KENT_RESOURCE_LOCK_OWNER_PID"
  require_nonnegative_integer owner_pid "$owner_pid"
  dir="$(lock_dir_for "$resource")"
  resource_is_valid "$resource" || return 64
  read_owner_snapshot "$dir" "$resource" || return 75
  case "$OWNER_KIND" in
    Absent)
      ;;
    LegacySessionOwner)
      age="$(lock_age_seconds "$OWNER_CREATED_AT")"
      if [[ "$age" -le "$ttl_seconds" ]]; then
        return 75
      fi
      printf 'stale_session_lock_reclaimed resource=%s age_seconds=%s\n' \
        "$resource" "$age" >&2
      remove_owner_record "$dir" "$resource" "$OWNER_HASH" session || return 75
      ;;
    ScriptOwnerV1|UnknownOwner)
      return 75
      ;;
    *)
      return 75
      ;;
  esac

  token="$(uuidgen 2>/dev/null || printf '%s-%s' "$$" "$(now_epoch)")"
  if [[ ! "$token" =~ ^[A-Za-z0-9][A-Za-z0-9._=-]{0,127}$ ]]; then
    printf 'resource_lock_token_generation_failed\n' >&2
    return 69
  fi
  cwd="$(pwd -P)" || return 69
  created_at="$(now_epoch)" || return 69
  content="$(session_owner_content \
    "$token" "$resource" "$owner_pid" "$cwd" "$created_at" \
    "$KENT_TASK_ID" "$KENT_SESSION_ID")"
  write_owner_record "$dir" "$resource" absent session "$content" >/dev/null || {
    printf 'resource_lock_owner_write_failed\n' >&2
    return 69
  }
  printf '%s\n' "$token"
}

locked_release() {
  local resource="$1"
  local token="$2"
  local dir

  dir="$(lock_dir_for "$resource")"
  read_owner_snapshot "$dir" "$resource" || return 75
  if [[ "$OWNER_KIND" == "Absent" ]]; then
    printf 'resource_already_unlocked resource=%s\n' "$resource" >&2
    return 0
  fi

  if [[ "$OWNER_KIND" != "LegacySessionOwner" ]]; then
    printf 'resource_lock_owner_not_session resource=%s\n' "$resource" >&2
    return 75
  fi
  if [[ "$OWNER_TOKEN" != "$token" ]]; then
    printf 'resource_lock_token_mismatch resource=%s lock_dir=%s\n' \
      "$resource" "$dir" >&2
    return 64
  fi

  remove_owner_record "$dir" "$resource" "$OWNER_HASH" session || {
    printf 'resource_lock_release_conflict resource=%s\n' "$resource" >&2
    return 75
  }
}

locked_resume() {
  local resource="$1"
  local token="$2"
  local dir owner_pid cwd created_at expected content

  require_lock_identity || return $?
  if [[ ! "$token" =~ ^[A-Za-z0-9][A-Za-z0-9._=-]{0,127}$ ]]; then
    printf 'resource_lock_token_invalid resource=%s\n' "$resource" >&2
    return 64
  fi

  owner_pid="$KENT_RESOURCE_LOCK_OWNER_PID"
  require_nonnegative_integer owner_pid "$owner_pid"
  resource_is_valid "$resource" || return 64
  dir="$(lock_dir_for "$resource")"
  read_owner_snapshot "$dir" "$resource" || return 75
  case "$OWNER_KIND" in
    Absent)
      expected=absent
      ;;
    LegacySessionOwner)
      if [[ "$OWNER_TOKEN" != "$token" ]]; then
        printf 'resource_lock_token_mismatch resource=%s lock_dir=%s\n' \
          "$resource" "$dir" >&2
        return 75
      fi
      if [[ "$OWNER_TASK_ID" != "$KENT_TASK_ID" ]]; then
        printf 'resource_lock_owned_by_other_task resource=%s\n' \
          "$resource" >&2
        return 75
      fi
      expected="$OWNER_HASH"
      ;;
    *)
      printf 'resource_lock_owner_metadata_invalid resource=%s\n' \
        "$resource" >&2
      return 75
      ;;
  esac

  cwd="$(pwd -P)" || return 69
  created_at="$(now_epoch)" || return 69
  content="$(session_owner_content \
    "$token" "$resource" "$owner_pid" "$cwd" "$created_at" \
    "$KENT_TASK_ID" "$KENT_SESSION_ID")"
  if ! write_owner_record "$dir" "$resource" "$expected" session "$content" \
    >/dev/null; then
    printf 'resource_lock_owner_write_failed resource=%s\n' "$resource" >&2
    return 75
  fi
  printf '%s\n' "$token"
}

locked_resume_owned() {
  local resource="$1"
  local dir token owner_task_id task_id owner_pid cwd created_at content

  require_lock_identity || return $?
  task_id="${KENT_TASK_ID:-}"
  owner_pid="$KENT_RESOURCE_LOCK_OWNER_PID"
  require_nonnegative_integer owner_pid "$owner_pid"
  resource_is_valid "$resource" || return 64
  dir="$(lock_dir_for "$resource")"
  read_owner_snapshot "$dir" "$resource" || return 75
  if [[ "$OWNER_KIND" != "LegacySessionOwner" ]]; then
    printf 'resource_lock_owner_metadata_invalid resource=%s\n' \
      "$resource" >&2
    return 75
  fi

  token="$OWNER_TOKEN"
  owner_task_id="$OWNER_TASK_ID"
  if [[ "$owner_task_id" != "$task_id" ]]; then
    printf 'resource_lock_owned_by_other_task resource=%s\n' \
      "$resource" >&2
    return 75
  fi

  cwd="$(pwd -P)" || return 69
  created_at="$(now_epoch)" || return 69
  content="$(session_owner_content \
    "$token" "$resource" "$owner_pid" "$cwd" "$created_at" \
    "$task_id" "$KENT_SESSION_ID")"
  if ! write_owner_record "$dir" "$resource" "$OWNER_HASH" session "$content" \
    >/dev/null; then
    printf 'resource_lock_owner_write_failed resource=%s\n' "$resource" >&2
    return 75
  fi
  printf '%s\n' "$token"
}

script_owner_content() {
  local token="$1"
  local resource="$2"
  local task_id="$3"
  local context_sha256="$4"
  local pid="$5"
  local process_platform="$6"
  local boot_id="$7"
  local process_start="$8"
  local pgid="$9"
  local cwd="${10}"
  local created_at="${11}"

  printf 'owner_schema=mobile-script-owner-v1\n'
  printf 'owner_kind=script\n'
  printf 'token=%s\n' "$token"
  printf 'resource=%s\n' "$resource"
  printf 'task_id=%s\n' "$task_id"
  printf 'context_sha256=%s\n' "$context_sha256"
  printf 'pid=%s\n' "$pid"
  printf 'process_platform=%s\n' "$process_platform"
  printf 'boot_id=%s\n' "$boot_id"
  printf 'process_start=%s\n' "$process_start"
  printf 'pgid=%s\n' "$pgid"
  printf 'cwd=%s\n' "$cwd"
  printf 'created_at=%s\n' "$created_at"
}

locked_script_acquire() {
  local resource="$1"
  local dir identity process_platform boot_id process_start pgid cwd
  local created_at token content owner_hash

  script_request acquire || return $?
  resource_is_valid "$resource" || return 64
  identity="$(storage_tool identity "$SCRIPT_PID")" || {
    printf 'resource_lock_script_process_identity_unknown\n' >&2
    return 69
  }
  IFS=$'\t' read -r process_platform boot_id process_start pgid <<<"$identity"
  cwd="$(pwd -P)" || return 69
  created_at="$(now_epoch)" || return 69
  token="$(python3 -c 'import secrets; print(secrets.token_urlsafe(32))')" || {
    printf 'resource_lock_script_token_generation_failed\n' >&2
    return 69
  }
  dir="$(lock_dir_for "$resource")"
  read_owner_snapshot "$dir" "$resource" || return 75
  if [[ "$OWNER_KIND" != "Absent" ]]; then
    return 75
  fi

  content="$(script_owner_content \
    "$token" "$resource" "$SCRIPT_TASK_ID" "$SCRIPT_CONTEXT_SHA256" \
    "$SCRIPT_PID" "$process_platform" "$boot_id" "$process_start" \
    "$pgid" "$cwd" "$created_at")"
  owner_hash="$(write_owner_record \
    "$dir" "$resource" absent script "$content")" || {
    printf 'resource_lock_script_owner_write_failed\n' >&2
    return 69
  }
  printf 'schema=mobile-script-lease-result-v1\n'
  printf 'resource=%s\n' "$resource"
  printf 'token=%s\n' "$token"
  printf 'owner_sha256=%s\n' "$owner_hash"
}

locked_script_release() {
  local resource="$1"
  local dir identity process_platform boot_id process_start pgid cwd

  script_request release || return $?
  resource_is_valid "$resource" || return 64
  dir="$(lock_dir_for "$resource")"
  read_owner_snapshot "$dir" "$resource" || return 75
  if [[ "$OWNER_KIND" != "ScriptOwnerV1" ||
    "$OWNER_TASK_ID" != "$SCRIPT_TASK_ID" ||
    "$OWNER_CONTEXT_SHA256" != "$SCRIPT_CONTEXT_SHA256" ||
    "$OWNER_PID" != "$SCRIPT_PID" ||
    "$OWNER_TOKEN" != "$SCRIPT_TOKEN" ||
    "$OWNER_HASH" != "$SCRIPT_OWNER_SHA256" ]]; then
    printf 'resource_lock_script_owner_mismatch\n' >&2
    return 75
  fi

  identity="$(storage_tool identity "$SCRIPT_PID")" || {
    printf 'resource_lock_script_process_identity_unknown\n' >&2
    return 75
  }
  IFS=$'\t' read -r process_platform boot_id process_start pgid <<<"$identity"
  cwd="$(pwd -P)" || return 75
  if [[ "$OWNER_PROCESS_PLATFORM" != "$process_platform" ||
    "$OWNER_BOOT_ID" != "$boot_id" ||
    "$OWNER_PROCESS_START" != "$process_start" ||
    "$OWNER_PGID" != "$pgid" ||
    "$OWNER_CWD" != "$cwd" ]]; then
    printf 'resource_lock_script_process_identity_mismatch\n' >&2
    return 75
  fi
  remove_owner_record "$dir" "$resource" "$OWNER_HASH" script || {
    printf 'resource_lock_script_release_conflict\n' >&2
    return 75
  }
}

locked_script_inspect() {
  local resource="$1"
  local dir
  dir="$(lock_dir_for "$resource")"
  read_owner_snapshot "$dir" "$resource" || return 75
  printf 'schema=mobile-script-recovery-v1\n'
  printf 'resource=%s\n' "$resource"
  printf 'owner_kind=%s\n' "$OWNER_KIND"
  printf 'owner_sha256=%s\n' "$OWNER_HASH"
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
    script-acquire)
      [[ $# -eq 0 ]] || return 64
      locked_script_acquire "$resource"
      ;;
    script-release)
      [[ $# -eq 0 ]] || return 64
      locked_script_release "$resource"
      ;;
    script-inspect)
      [[ $# -eq 0 ]] || return 64
      locked_script_inspect "$resource"
      ;;
    *)
      printf 'unknown_locked_operation operation=%s\n' "$operation" >&2
      return 64
      ;;
  esac
}

LOCK_BACKEND=""
LOCK_BACKEND_PATH=""

select_lock_backend() {
  local requested="${KENT_RESOURCE_LOCK_BACKEND:-auto}"
  local system expected canonical selected resolved_expected resolved_selected

  system="$(uname -s)" || return 69
  case "$system" in
    Darwin)
      expected=lockf
      canonical=/usr/bin/lockf
      ;;
    Linux)
      expected=flock
      if [[ -x /usr/bin/flock ]]; then
        canonical=/usr/bin/flock
      elif [[ -x /bin/flock ]]; then
        canonical=/bin/flock
      else
        printf 'resource_lock_backend_unavailable platform=Linux\n' >&2
        return 69
      fi
      ;;
    *)
      printf 'resource_lock_backend_platform_unsupported platform=%s\n' \
        "$system" >&2
      return 69
      ;;
  esac

  if [[ "$requested" != auto && "$requested" != "$expected" ]]; then
    printf 'resource_lock_backend_incompatible requested=%s required=%s\n' \
      "$requested" "$expected" >&2
    return 69
  fi
  selected="$(command -v "$expected" 2>/dev/null || true)"
  if [[ -z "$selected" || ! -x "$canonical" ]]; then
    printf 'resource_lock_backend_unavailable requires=%s\n' "$expected" >&2
    return 69
  fi
  resolved_expected="$(python3 -c 'import os,sys; print(os.path.realpath(sys.argv[1]))' "$canonical")" || return 69
  resolved_selected="$(python3 -c 'import os,sys; print(os.path.realpath(sys.argv[1]))' "$selected")" || return 69
  if [[ "$resolved_selected" != "$resolved_expected" ]]; then
    printf 'resource_lock_backend_path_mismatch backend=%s\n' "$expected" >&2
    return 69
  fi
  LOCK_BACKEND="$expected"
  LOCK_BACKEND_PATH="$resolved_expected"
}

inspect_without_guard() {
  local resource="$1"
  local dir owner_kind owner_hash

  dir="$(lock_dir_for "$resource")"
  if ! read_owner_snapshot "$dir" "$resource"; then
    owner_kind=UnknownOwner
    owner_hash=unknown
  elif [[ "$OWNER_KIND" == "Absent" ]]; then
    owner_kind=Absent
    owner_hash=none
  else
    owner_kind=UnknownOwner
    owner_hash=unknown
  fi
  printf 'schema=mobile-script-recovery-v1\n'
  printf 'resource=%s\n' "$resource"
  printf 'owner_kind=%s\n' "$owner_kind"
  printf 'owner_sha256=%s\n' "$owner_hash"
}

with_resource_guard() {
  local resource="$1"
  local operation="$2"
  shift 2
  local guard_file request_payload guard_status

  case "$operation" in
    try-acquire|resume|resume-owned)
      require_lock_identity || return $?
      ;;
  esac
  resource_is_valid "$resource" || {
    printf 'resource_lock_resource_invalid\n' >&2
    return 64
  }
  if [[ "$operation" == script-acquire || "$operation" == script-release ]]; then
    request_payload="${1:-}"
    [[ -n "$request_payload" ]] || return 64
    shift
  fi
  select_lock_backend || return $?
  guard_file="$(guard_file_for "$resource")"

  if [[ "$operation" == script-inspect ]]; then
    set +e
    storage_tool guard-check "$guard_file"
    guard_status=$?
    set -e
    if [[ "$guard_status" -eq 4 ]]; then
      inspect_without_guard "$resource"
      return $?
    fi
    if [[ "$guard_status" -ne 0 ]]; then
      printf 'resource_lock_guard_identity_invalid\n' >&2
      return 69
    fi
  else
    ensure_runtime_roots || return $?
    storage_tool guard-init "$guard_file" || {
      printf 'resource_lock_guard_identity_invalid\n' >&2
      return 69
    }
  fi

  if [[ "$LOCK_BACKEND" == "flock" ]]; then
    if [[ "$operation" == script-acquire || "$operation" == script-release ]]; then
      printf '%s\n' "$request_payload" |
        "$LOCK_BACKEND_PATH" "$guard_file" \
          "$script_path" __locked "$operation" "$resource"
    else
      "$LOCK_BACKEND_PATH" "$guard_file" \
        "$script_path" __locked "$operation" "$resource" "$@"
    fi
  elif [[ "$LOCK_BACKEND" == "lockf" ]]; then
    if [[ "$operation" == script-acquire || "$operation" == script-release ]]; then
      printf '%s\n' "$request_payload" |
        "$LOCK_BACKEND_PATH" -k "$guard_file" \
          "$script_path" __locked "$operation" "$resource"
    else
      "$LOCK_BACKEND_PATH" -k "$guard_file" \
        "$script_path" __locked "$operation" "$resource" "$@"
    fi
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

  require_lock_identity || return $?
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

  require_lock_identity || return $?
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

script_request_payload() {
  local operation="$1"

  script_request "$operation" || return $?
  printf 'schema=mobile-script-lease-request-v1\n'
  printf 'task_id=%s\n' "$SCRIPT_TASK_ID"
  printf 'context_sha256=%s\n' "$SCRIPT_CONTEXT_SHA256"
  printf 'pid=%s\n' "$SCRIPT_PID"
  if [[ "$operation" == release ]]; then
    printf 'token=%s\n' "$SCRIPT_TOKEN"
    printf 'owner_sha256=%s\n' "$SCRIPT_OWNER_SHA256"
  fi
}

acquire_script() {
  local resource="$1"
  local wait_seconds="${2:-900}"
  local request_payload started now result attempt_status

  resource_is_valid "$resource" || return 64
  require_nonnegative_integer wait_seconds "$wait_seconds" || return $?
  request_payload="$(script_request_payload acquire)" || return $?
  started="$(now_epoch)"

  while true; do
    set +e
    result="$(with_resource_guard "$resource" script-acquire "$request_payload")"
    attempt_status=$?
    set -e
    if [[ "$attempt_status" -eq 0 ]]; then
      printf '%s\n' "$result"
      return 0
    fi
    if [[ "$attempt_status" -ne 75 ]]; then
      return "$attempt_status"
    fi
    now="$(now_epoch)"
    if [[ $(( now - started )) -ge "$wait_seconds" ]]; then
      printf 'resource_busy resource=%s\n' "$resource" >&2
      return 75
    fi
    sleep 5
  done
}

acquire_script_any() {
  local args=("$@")
  local separator=-1
  local wait_seconds=900
  local resources=()
  local started now resource request_payload result attempt_status duplicate

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
  if [[ ${#args[@]} -gt $(( separator + 2 )) ]]; then
    usage
    return 64
  fi
  if [[ ${#args[@]} -eq $(( separator + 2 )) ]]; then
    wait_seconds="${args[$(( separator + 1 ))]}"
  fi
  require_nonnegative_integer wait_seconds "$wait_seconds" || return $?
  for i in "${!resources[@]}"; do
    resource="${resources[$i]}"
    if ! resource_is_valid "$resource"; then
      printf 'resource_lock_resource_invalid\n' >&2
      return 64
    fi
    for j in "${!resources[@]}"; do
      if [[ "$j" -lt "$i" && "${resources[$j]}" == "$resource" ]]; then
        printf 'resource_lock_duplicate_resource\n' >&2
        return 64
      fi
    done
  done
  request_payload="$(script_request_payload acquire)" || return $?
  started="$(now_epoch)"

  while true; do
    for resource in "${resources[@]}"; do
      set +e
      result="$(with_resource_guard "$resource" script-acquire "$request_payload")"
      attempt_status=$?
      set -e
      if [[ "$attempt_status" -eq 0 ]]; then
        printf '%s\n' "$result"
        return 0
      fi
      if [[ "$attempt_status" -ne 75 ]]; then
        return "$attempt_status"
      fi
    done
    now="$(now_epoch)"
    if [[ $(( now - started )) -ge "$wait_seconds" ]]; then
      printf 'all_resources_busy resources=%s\n' "${resources[*]}" >&2
      return 75
    fi
    sleep 5
  done
}

release_script() {
  local resource="$1"
  local request_payload

  resource_is_valid "$resource" || return 64
  request_payload="$(script_request_payload release)" || return $?
  with_resource_guard "$resource" script-release "$request_payload"
}

inspect_script_recovery() {
  local resource="$1"

  resource_is_valid "$resource" || return 64
  with_resource_guard "$resource" script-inspect
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
  acquire-script)
    [[ $# -ge 2 && $# -le 3 ]] || { usage; exit 64; }
    acquire_script "$2" "${3:-900}"
    ;;
  acquire-script-any)
    shift
    acquire_script_any "$@"
    ;;
  release-script)
    [[ $# -eq 2 ]] || { usage; exit 64; }
    release_script "$2"
    ;;
  inspect-script-recovery)
    [[ $# -eq 2 ]] || { usage; exit 64; }
    inspect_script_recovery "$2"
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
      if [[ -d "$runtime_root" ]]; then
        find "$runtime_root" \
          -maxdepth 1 \
          -type d \
          -name 'mobile-*.lock' \
          -print |
          sort
      fi
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
