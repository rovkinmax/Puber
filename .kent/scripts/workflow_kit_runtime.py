"""Qualified, standard-library-only selection of a published Kit source tree.

Runtime callers derive the Kit binding from fresh native Task/Workflow
authority before any ``workflowkit`` import. This module is not a sandbox or
an authentication boundary; it keeps one source cohort selected per process.
"""
from __future__ import annotations

from dataclasses import dataclass
import hashlib
import importlib
import json
import os
from pathlib import Path
import re
import select
import shutil
import stat
import subprocess
import sys
import time
import tomllib
from types import ModuleType
from typing import Any, Mapping


KIT_RUNTIME_BINDING_SCHEMA = "kit-runtime-source-binding-v1"
SCRIPT_INGRESS_REF_SCHEMA = "delivery-script-ingress-ref-v1"
SDK_SOURCE_BINDING_COMMAND = "kit_runtime_source_binding"
SDK_SOURCE_BINDING_PATH = ".kent/scripts/workflow_sdk_source_binding.py"
MAX_PROFILE_BYTES = 64 * 1024
MAX_NATIVE_JSON_BYTES = 1024 * 1024
MAX_NATIVE_GRAPH_BYTES = 8 * 1024 * 1024
MAX_RECEIPT_BYTES = 1024 * 1024
MAX_SOURCE_BYTES = 1024 * 1024
MAX_NATIVE_ROWS = 4096
MAX_NATIVE_PAGE_SIZE = 100
NATIVE_TIMEOUT_SECONDS = 15.0
GIT_TIMEOUT_SECONDS = 30.0
OID_RE = re.compile(r"^[0-9a-f]{40}$")
DIGEST_RE = re.compile(r"^[0-9a-f]{64}$")
KEY_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,255}$")
UUID_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
)
REF_RE = re.compile(r"^refs/(?:remotes|tags)/[A-Za-z0-9._/-]+$")
SOURCE_ADMISSION_BEGIN = "KENT_SOURCE_ADMISSION_V1_BEGIN"
SOURCE_ADMISSION_END = "KENT_SOURCE_ADMISSION_V1_END"


class KitRuntimeError(RuntimeError):
    """A fail-closed source, profile, or native-authority qualification error."""


@dataclass(frozen=True)
class KitRuntimeBinding:
    schema: str
    root: str
    commit: str
    publication_ref: str

    def as_dict(self) -> dict[str, str]:
        return {
            "schema": self.schema,
            "root": self.root,
            "commit": self.commit,
            "publication_ref": self.publication_ref,
        }


@dataclass(frozen=True)
class QualifiedKitRuntime:
    """One immutable selected Kit cohort for the lifetime of this invocation."""

    project_root: Path
    source_root: Path
    binding: KitRuntimeBinding
    native_authorized: bool

    def module(self, name: str) -> ModuleType:
        if _qualify_git_cohort(self.binding) != self.source_root:
            raise KitRuntimeError("qualified Kit source root changed during this invocation")
        return _import_kit_module(name, self.source_root)


def _canonical_directory(value: str | os.PathLike[str], label: str) -> Path:
    path = Path(value).expanduser()
    if not path.is_absolute() or ".." in path.parts:
        raise KitRuntimeError(f"{label} must be an absolute normalized path")
    try:
        resolved = path.resolve(strict=True)
    except (OSError, RuntimeError) as error:
        raise KitRuntimeError(f"{label} is unavailable") from error
    if resolved != path or not path.is_dir():
        raise KitRuntimeError(f"{label} is not a canonical directory")
    current = Path(path.anchor)
    for part in path.parts[1:]:
        current /= part
        try:
            metadata = current.lstat()
        except OSError as error:
            raise KitRuntimeError(f"{label} path changed") from error
        if stat.S_ISLNK(metadata.st_mode):
            raise KitRuntimeError(f"{label} contains a symbolic link")
    return path


def _read_regular_file(
    path_value: str | os.PathLike[str],
    *,
    maximum: int,
    private: bool = False,
    exact_mode: int | None = None,
) -> tuple[bytes, int]:
    path = Path(path_value)
    if not path.is_absolute() or ".." in path.parts or str(path) != str(path.resolve()):
        raise KitRuntimeError("source file path is not canonical")
    descriptors: list[int] = []
    directory_bindings: list[tuple[int, str, int]] = []
    try:
        root_fd = os.open(path.anchor, os.O_RDONLY | getattr(os, "O_DIRECTORY", 0))
        descriptors.append(root_fd)
        for component in path.parent.parts[1:]:
            parent_fd = descriptors[-1]
            child_fd = os.open(
                component,
                os.O_RDONLY | getattr(os, "O_DIRECTORY", 0)
                | getattr(os, "O_NOFOLLOW", 0),
                dir_fd=parent_fd,
            )
            descriptors.append(child_fd)
            directory_bindings.append((parent_fd, component, child_fd))
        parent_info = os.fstat(descriptors[-1])
        parent_forbidden = 0o7077 if private else 0o7022
        if (
            not stat.S_ISDIR(parent_info.st_mode)
            or parent_info.st_uid != os.getuid()
            or parent_info.st_mode & parent_forbidden
        ):
            raise KitRuntimeError("source parent directory is unsafe")
        file_fd = os.open(
            path.name,
            os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0)
            | getattr(os, "O_NONBLOCK", 0),
            dir_fd=descriptors[-1],
        )
        descriptors.append(file_fd)
        before = os.fstat(file_fd)
        forbidden = 0o7077 if private else 0o7022
        if (
            not stat.S_ISREG(before.st_mode)
            or before.st_uid != os.getuid()
            or before.st_nlink != 1
            or before.st_mode & forbidden
            or before.st_mode & 0o7000
            or before.st_size < 0
            or before.st_size > maximum
            or (exact_mode is not None and stat.S_IMODE(before.st_mode) != exact_mode)
        ):
            raise KitRuntimeError("source file is unsafe or exceeds its bound")
        chunks: list[bytes] = []
        size = 0
        while True:
            chunk = os.read(file_fd, min(65536, maximum + 1 - size))
            if not chunk:
                break
            size += len(chunk)
            if size > maximum:
                raise KitRuntimeError("source file exceeds its byte limit")
            chunks.append(chunk)
        after = os.fstat(file_fd)
        identity = lambda item: (
            item.st_dev, item.st_ino, item.st_mode, item.st_uid,
            item.st_nlink, item.st_size, item.st_mtime_ns, item.st_ctime_ns,
        )
        named = os.stat(path.name, dir_fd=descriptors[-2], follow_symlinks=False)
        if (
            identity(before) != identity(after)
            or identity(named) != identity(after)
            or size != before.st_size
        ):
            raise KitRuntimeError("source file changed while being read")
        for parent_fd, component, child_fd in directory_bindings:
            named_directory = os.stat(
                component, dir_fd=parent_fd, follow_symlinks=False,
            )
            opened_directory = os.fstat(child_fd)
            if (named_directory.st_dev, named_directory.st_ino) != (
                opened_directory.st_dev, opened_directory.st_ino,
            ):
                raise KitRuntimeError("source directory binding changed")
        return b"".join(chunks), stat.S_IMODE(before.st_mode)
    except KitRuntimeError:
        raise
    except OSError as error:
        raise KitRuntimeError("source file cannot be read safely") from error
    finally:
        for descriptor in reversed(descriptors):
            os.close(descriptor)


def _json_pairs(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise KitRuntimeError("source JSON contains a duplicate key")
        result[key] = value
    return result


def _invalid_json_constant(_value: str) -> None:
    raise KitRuntimeError("source JSON contains a non-finite number")


def _parse_json(raw: bytes, label: str) -> dict[str, Any]:
    try:
        value = json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=_json_pairs,
            parse_constant=_invalid_json_constant,
        )
    except (UnicodeError, json.JSONDecodeError, RecursionError) as error:
        raise KitRuntimeError(f"{label} is not valid bounded JSON") from error
    if not isinstance(value, dict):
        raise KitRuntimeError(f"{label} must be one JSON object")
    return value


def _canonical_json(value: Any) -> bytes:
    try:
        return json.dumps(
            value, ensure_ascii=False, sort_keys=True,
            separators=(",", ":"), allow_nan=False,
        ).encode("utf-8")
    except (TypeError, ValueError, UnicodeError) as error:
        raise KitRuntimeError("source JSON is not canonicalizable") from error


def _parse_binding(value: Any) -> KitRuntimeBinding:
    if not isinstance(value, Mapping) or set(value) != {
        "schema", "root", "commit", "publication_ref",
    }:
        raise KitRuntimeError("native Kit source binding has an invalid shape")
    root = value.get("root")
    commit = value.get("commit")
    publication_ref = value.get("publication_ref")
    if (
        value.get("schema") != KIT_RUNTIME_BINDING_SCHEMA
        or not isinstance(root, str)
        or not root
        or "\x00" in root
        or not Path(root).is_absolute()
        or str(Path(root)) != root
        or ".." in Path(root).parts
        or not isinstance(commit, str)
        or OID_RE.fullmatch(commit) is None
        or not isinstance(publication_ref, str)
        or REF_RE.fullmatch(publication_ref) is None
        or publication_ref.endswith("/HEAD")
        or publication_ref.endswith(".lock")
        or any(part in {"", ".", ".."} for part in publication_ref.split("/"))
    ):
        raise KitRuntimeError("native Kit source binding is malformed")
    return KitRuntimeBinding(
        schema=KIT_RUNTIME_BINDING_SCHEMA,
        root=root,
        commit=commit,
        publication_ref=publication_ref,
    )


def _profile_binding(raw: bytes) -> KitRuntimeBinding | None:
    try:
        profile = tomllib.loads(raw.decode("utf-8"))
    except (UnicodeError, tomllib.TOMLDecodeError) as error:
        raise KitRuntimeError("project profile is invalid TOML") from error
    if not isinstance(profile, dict):
        raise KitRuntimeError("project profile root is invalid")
    return _profile_table_binding(profile.get("kit_runtime"))


def _profile_table_binding(value: Any) -> KitRuntimeBinding | None:
    if value is None:
        return None
    if not isinstance(value, dict) or set(value) != {
        "schema_version", "root", "commit", "publication_ref",
    }:
        raise KitRuntimeError("kit_runtime profile table is not closed")
    if type(value.get("schema_version")) is not int or value["schema_version"] != 1:
        raise KitRuntimeError("kit_runtime schema_version is unsupported")
    return _parse_binding({
        "schema": KIT_RUNTIME_BINDING_SCHEMA,
        "root": value.get("root"),
        "commit": value.get("commit"),
        "publication_ref": value.get("publication_ref"),
    })


def _profile_data(project_root: Path) -> tuple[dict[str, Any], bytes]:
    raw, _ = _read_regular_file(
        project_root / ".kent" / "workflow-profile.toml",
        maximum=MAX_PROFILE_BYTES,
    )
    try:
        value = tomllib.loads(raw.decode("utf-8"))
    except (UnicodeError, tomllib.TOMLDecodeError) as error:
        raise KitRuntimeError("project profile is invalid TOML") from error
    if not isinstance(value, dict):
        raise KitRuntimeError("project profile root is invalid")
    return value, raw


def _git_environment() -> dict[str, str]:
    env = {key: value for key, value in os.environ.items() if not key.startswith("GIT_")}
    env.update({
        "GIT_CONFIG_NOSYSTEM": "1",
        "GIT_CONFIG_GLOBAL": os.devnull,
        "GIT_NO_REPLACE_OBJECTS": "1",
        "GIT_NO_LAZY_FETCH": "1",
        "GIT_TERMINAL_PROMPT": "0",
        "GIT_OPTIONAL_LOCKS": "0",
        "LC_ALL": "C",
    })
    return env


def _bounded_process(
    argv: list[str],
    *,
    cwd: Path,
    timeout: float,
    maximum: int,
    env: Mapping[str, str] | None = None,
) -> bytes:
    try:
        process = subprocess.Popen(
            argv,
            cwd=cwd,
            env=dict(env) if env is not None else None,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            close_fds=True,
        )
    except OSError as error:
        raise KitRuntimeError("bounded source command could not start") from error
    assert process.stdout is not None
    deadline = time.monotonic() + timeout
    raw = bytearray()
    try:
        while True:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                raise KitRuntimeError("bounded source command timed out")
            readable, _, _ = select.select([process.stdout], [], [], remaining)
            if not readable:
                continue
            chunk = os.read(
                process.stdout.fileno(),
                min(65536, maximum + 1 - len(raw)),
            )
            if not chunk:
                break
            raw.extend(chunk)
            if len(raw) > maximum:
                raise KitRuntimeError("bounded source command exceeded its output limit")
        try:
            return_code = process.wait(timeout=max(0.001, deadline - time.monotonic()))
        except subprocess.TimeoutExpired as error:
            raise KitRuntimeError("bounded source command timed out") from error
        if return_code != 0:
            raise KitRuntimeError("bounded source command failed")
        return bytes(raw)
    except BaseException:
        if process.poll() is None:
            process.kill()
            process.wait()
        raise
    finally:
        process.stdout.close()


def _git(root: Path, *args: str, maximum: int = MAX_SOURCE_BYTES) -> bytes:
    executable = shutil.which("git")
    if executable is None:
        raise KitRuntimeError("Git is unavailable")
    return _bounded_process(
        [
            executable, "--no-optional-locks",
            "-c", "core.fsmonitor=false",
            "-c", "core.hooksPath=" + os.devnull,
            "-C", str(root), *args,
        ],
        cwd=root,
        timeout=GIT_TIMEOUT_SECONDS,
        maximum=maximum,
        env=_git_environment(),
    )


def _qualify_git_cohort(binding: KitRuntimeBinding) -> Path:
    root = _canonical_directory(binding.root, "published Kit root")
    if (
        not binding.publication_ref.startswith(("refs/remotes/", "refs/tags/"))
        or binding.publication_ref.endswith("/HEAD")
    ):
        raise KitRuntimeError("Kit publication ref is not explicit")
    try:
        top = _git(root, "rev-parse", "--show-toplevel").decode().strip()
        head = _git(root, "rev-parse", "--verify", "HEAD^{commit}").decode().strip()
        ref = binding.publication_ref
        _git(root, "check-ref-format", ref)
        tip = _git(root, "rev-parse", "--verify", f"{ref}^{{commit}}").decode().strip()
        status = _git(
            root, "status", "--porcelain=v1", "-z",
            "--untracked-files=all", "--ignore-submodules=none",
        )
        _git(root, "merge-base", "--is-ancestor", binding.commit, tip)
    except (UnicodeError, KitRuntimeError) as error:
        raise KitRuntimeError("published Kit cohort could not be qualified") from error
    if (
        top != str(root)
        or head != binding.commit
        or OID_RE.fullmatch(tip) is None
        or status
    ):
        raise KitRuntimeError("published Kit cohort differs from its native binding")
    return root


def _import_kit_module(name: str, source_root: Path) -> ModuleType:
    module_name = name if name.startswith("workflowkit.") or name == "workflowkit" else (
        f"workflowkit.{name}"
    )
    if (
        (not module_name.startswith("workflowkit.") and module_name != "workflowkit")
        or any(not part.isidentifier() for part in module_name.split("."))
    ):
        raise KitRuntimeError("module name is outside the first-party Kit package")
    for loaded_name, module in tuple(sys.modules.items()):
        if loaded_name != "workflowkit" and not loaded_name.startswith("workflowkit."):
            continue
        origin = getattr(module, "__file__", None)
        if origin is None:
            spec = getattr(module, "__spec__", None)
            origin = getattr(spec, "origin", None)
        if not isinstance(origin, str):
            raise KitRuntimeError("a Kit module from another source root is already loaded")
        origin_path = Path(origin)
        try:
            resolved_origin = origin_path.resolve(strict=True)
        except (OSError, RuntimeError) as error:
            raise KitRuntimeError("a loaded Kit module source is unavailable") from error
        if origin_path != resolved_origin or not resolved_origin.is_relative_to(source_root):
            raise KitRuntimeError("a Kit module from another source root is already loaded")
    root_text = str(source_root)
    original_sys_path = list(sys.path)
    original_bytecode_policy = sys.dont_write_bytecode
    try:
        while root_text in sys.path:
            sys.path.remove(root_text)
        sys.path.insert(0, root_text)
        sys.dont_write_bytecode = True
        module = importlib.import_module(module_name)
    except Exception as error:
        raise KitRuntimeError("qualified Kit module import failed") from error
    finally:
        sys.path[:] = original_sys_path
        sys.dont_write_bytecode = original_bytecode_policy
    for loaded_name, loaded in tuple(sys.modules.items()):
        if loaded_name != "workflowkit" and not loaded_name.startswith("workflowkit."):
            continue
        origin = getattr(loaded, "__file__", None)
        if not isinstance(origin, str):
            raise KitRuntimeError("Kit import escaped the selected source root")
        path = Path(origin)
        try:
            metadata = path.lstat()
            resolved_path = path.resolve(strict=True)
        except OSError as error:
            raise KitRuntimeError("qualified Kit source disappeared") from error
        if (
            path != resolved_path
            or not resolved_path.is_relative_to(source_root)
            or not stat.S_ISREG(metadata.st_mode)
            or stat.S_ISLNK(metadata.st_mode)
            or metadata.st_uid != os.getuid()
            or metadata.st_mode & 0o7022
        ):
            raise KitRuntimeError("qualified Kit module source is unsafe")
    return module


def _native_executable() -> Path:
    selected = shutil.which("kent")
    if selected is None:
        raise KitRuntimeError("native Kent CLI is unavailable")
    try:
        executable = Path(selected).resolve(strict=True)
        metadata = executable.stat()
    except (OSError, RuntimeError) as error:
        raise KitRuntimeError("native Kent CLI is unavailable") from error
    if not executable.is_absolute() or not stat.S_ISREG(metadata.st_mode) or not os.access(
        executable, os.X_OK,
    ):
        raise KitRuntimeError("native Kent CLI is unsafe")
    return executable


def _native_read(executable: Path, workspace: Path, arguments: list[str]) -> bytes:
    return _bounded_process(
        [str(executable), *arguments],
        cwd=workspace,
        timeout=NATIVE_TIMEOUT_SECONDS,
        maximum=MAX_NATIVE_JSON_BYTES,
    )


def _native_json(
    executable: Path,
    workspace: Path,
    arguments: list[str],
    *,
    maximum: int = MAX_NATIVE_JSON_BYTES,
) -> dict[str, Any]:
    raw = _bounded_process(
        [str(executable), *arguments],
        cwd=workspace,
        timeout=NATIVE_TIMEOUT_SECONDS,
        maximum=maximum,
    )
    return _parse_json(raw, "native readback")


def _native_project_id(executable: Path, workspace: Path) -> str:
    try:
        value = _native_read(
            executable, workspace, ["project", str(workspace)],
        ).decode("utf-8").strip()
    except UnicodeError as error:
        raise KitRuntimeError("native project identity is invalid") from error
    if not KEY_RE.fullmatch(value):
        raise KitRuntimeError("native project identity is unavailable")
    return value


def _field_alias(value: Mapping[str, Any], snake: str, camel: str) -> Any:
    if snake in value and camel in value and value[snake] != value[camel]:
        raise KitRuntimeError("native worktree status has conflicting identity fields")
    return value[snake] if snake in value else value.get(camel)


def _validate_session_worktree(
    executable: Path,
    workspace: Path,
    session_id: str,
) -> dict[str, Any]:
    if UUID_RE.fullmatch(session_id) is None:
        raise KitRuntimeError("current native Session identity is unavailable")
    status = _native_json(
        executable,
        workspace,
        ["worktree", "status", "--session", session_id, "--json"],
    )
    target = status.get("target")
    worktree = status.get("worktree")
    problems = status.get("problems", [])
    if (
        not isinstance(target, dict)
        or (worktree is not None and not isinstance(worktree, dict))
        or not isinstance(problems, list)
        or problems
    ):
        raise KitRuntimeError("native Session worktree status is incomplete")
    workspace_id = _field_alias(target, "workspace_id", "workspaceId")
    workspace_root = _field_alias(target, "workspace_root", "workspaceRoot")
    effective = _field_alias(target, "effective_workdir", "effectiveWorkdir")
    cwd_relpath = _field_alias(target, "cwd_relpath", "cwdRelpath")
    target_worktree = target.get("worktree")
    if target_worktree is not None and not isinstance(target_worktree, dict):
        raise KitRuntimeError("native Session worktree target is malformed")
    recorded = worktree.get("recorded_root") if isinstance(worktree, dict) else None
    observed = worktree.get("observed_root") if isinstance(worktree, dict) else None
    if (
        not isinstance(workspace_id, str)
        or not KEY_RE.fullmatch(workspace_id)
        or not isinstance(workspace_root, str)
        or not isinstance(effective, str)
        or cwd_relpath != "."
    ):
        raise KitRuntimeError("native Session worktree identity is malformed")
    if target.get("workspace_availability") not in (None, "available"):
        raise KitRuntimeError("native Session workspace is unavailable")
    if (
        isinstance(target_worktree, dict)
        and target_worktree.get("availability") not in (None, "available")
    ):
        raise KitRuntimeError("native Session worktree is unavailable")
    try:
        canonical_workspace_root = _canonical_directory(workspace_root, "native source workspace")
        canonical_effective = _canonical_directory(effective, "native execution workspace")
        canonical_recorded = (
            _canonical_directory(recorded, "recorded worktree")
            if isinstance(recorded, str) else None
        )
        canonical_observed = (
            _canonical_directory(observed, "observed worktree")
            if isinstance(observed, str) else None
        )
        canonical_target_worktree = (
            _canonical_directory(
                _field_alias(target_worktree, "root", "root_path"),
                "native Session worktree",
            )
            if target_worktree is not None
            and isinstance(_field_alias(target_worktree, "root", "root_path"), str)
            else None
        )
    except KitRuntimeError as error:
        raise KitRuntimeError("native Session worktree root is invalid") from error
    if canonical_effective != workspace:
        raise KitRuntimeError("native Session is bound to another execution root")
    is_worktree = (
        canonical_workspace_root != workspace
        or canonical_target_worktree is not None
    )
    if is_worktree:
        if (
            canonical_recorded != workspace
            or canonical_observed != workspace
            or canonical_target_worktree != workspace
        ):
            raise KitRuntimeError("native Session worktree roots disagree")
    elif any(
        value not in (None, workspace)
        for value in (
            canonical_recorded, canonical_observed, canonical_target_worktree,
        )
    ):
        raise KitRuntimeError("native primary Session worktree roots disagree")
    return {
        "session_id": session_id,
        "workspace_id": workspace_id,
        "workspace_root": str(canonical_workspace_root),
        "effective_workdir": str(canonical_effective),
        "raw": status,
    }


def _current_session_task(
    executable: Path,
    workspace: Path,
    project_id: str,
    session: Mapping[str, Any],
) -> dict[str, Any]:
    session_id = session["session_id"]
    task_rows: list[dict[str, Any]] = []
    seen_ids: set[str] = set()
    offset = 0
    inventory_deadline = time.monotonic() + 120.0
    while True:
        if time.monotonic() >= inventory_deadline:
            raise KitRuntimeError("native Task inventory exceeded its time bound")
        page = _native_json(
            executable,
            workspace,
            [
                "task", "list", "--project", project_id,
                "--sort", "short-id:asc", "--limit", str(MAX_NATIVE_PAGE_SIZE),
                "--offset", str(offset), "--json",
            ],
        )
        scope = page.get("scope")
        rows = page.get("tasks")
        if (
            not isinstance(scope, dict)
            or scope.get("project_id") != project_id
            or not isinstance(rows, list)
            or len(rows) > MAX_NATIVE_PAGE_SIZE
        ):
            raise KitRuntimeError("native Task catalog is incomplete")
        if not rows:
            break
        for row in rows:
            if not isinstance(row, dict):
                raise KitRuntimeError("native Task catalog row is malformed")
            task_id = row.get("task_id")
            short_id = row.get("short_id")
            if (
                not isinstance(task_id, str)
                or not KEY_RE.fullmatch(task_id)
                or not isinstance(short_id, str)
                or not KEY_RE.fullmatch(short_id)
                or task_id in seen_ids
            ):
                raise KitRuntimeError("native Task catalog identity is ambiguous")
            seen_ids.add(task_id)
            task_rows.append(row)
            if len(task_rows) > MAX_NATIVE_ROWS:
                raise KitRuntimeError("native Task catalog exceeds its bound")
        offset += len(rows)
        if len(rows) < MAX_NATIVE_PAGE_SIZE:
            break

    matches: list[dict[str, Any]] = []
    for row in task_rows:
        status = row.get("status")
        status_kind = status.get("kind") if isinstance(status, dict) else None
        if status_kind in {"backlog", "done"}:
            continue
        detail = _native_json(
            executable,
            workspace,
            ["task", "show", row["task_id"], "--project", project_id, "--json"],
        )
        summary = detail.get("summary")
        source = detail.get("source_workspace")
        if (
            not isinstance(summary, dict)
            or summary.get("id") != row["task_id"]
            or summary.get("short_id") != row["short_id"]
            or summary.get("project_id") != project_id
            or not isinstance(source, dict)
            or not isinstance(source.get("workspace_id"), str)
            or not isinstance(source.get("root_path"), str)
        ):
            raise KitRuntimeError("native Task identity is inconsistent")
        task_root_value = detail.get("worktree_path") or source["root_path"]
        try:
            task_root = _canonical_directory(task_root_value, "native Task workspace")
            source_root = _canonical_directory(source["root_path"], "native source workspace")
        except (TypeError, KitRuntimeError):
            continue
        if task_root != workspace:
            continue
        current_nodes = detail.get("current_nodes", [])
        live_sessions = detail.get("live_sessions", [])
        if (
            not isinstance(current_nodes, list)
            or len(current_nodes) > 256
            or not isinstance(live_sessions, list)
            or len(live_sessions) > 256
            or any(not isinstance(item, dict) for item in current_nodes)
            or any(not isinstance(item, dict) for item in live_sessions)
        ):
            raise KitRuntimeError("native current Task/Session inventory is malformed")
        current_match = any(item.get("session_id") == session_id for item in current_nodes)
        live_match = any(item.get("session_id") == session_id for item in live_sessions)
        if not current_match and not live_match:
            continue
        if (
            source["workspace_id"] != session["workspace_id"]
            or str(source_root) != session["workspace_root"]
            or (detail.get("worktree_path") or str(source_root)) != str(workspace)
        ):
            raise KitRuntimeError("native Session/Task workspace binding differs")
        if _native_project_id(executable, source_root) != project_id:
            raise KitRuntimeError("native Task source workspace belongs to another project")
        matches.append({
            "detail": detail,
            "project_id": project_id,
            "task_id": row["task_id"],
            "short_id": row["short_id"],
            "workflow_id": summary.get("workflow_id"),
            "current_nodes": current_nodes,
            "source_workspace": source,
            "session_id": session_id,
        })
        if len(matches) > 1:
            raise KitRuntimeError("native Session is linked to multiple current Tasks")
    if len(matches) != 1:
        raise KitRuntimeError("native Session has no unique current Task")
    return matches[0]


def _task_from_script_context(
    executable: Path,
    workspace: Path,
    project_id: str,
    native_context: Mapping[str, Any],
) -> dict[str, Any]:
    if set(native_context) not in (
        {"task_id", "node_id"},
        {"task_id", "node_id", "transition_branch_key"},
    ):
        raise KitRuntimeError("native Script context has an invalid shape")
    task_id = native_context.get("task_id")
    node_id = native_context.get("node_id")
    branch_key = native_context.get("transition_branch_key")
    if (
        not isinstance(task_id, str)
        or not KEY_RE.fullmatch(task_id)
        or not isinstance(node_id, str)
        or not KEY_RE.fullmatch(node_id)
        or (
            branch_key is not None
            and (not isinstance(branch_key, str) or not KEY_RE.fullmatch(branch_key))
        )
    ):
        raise KitRuntimeError("native Script context is malformed")
    detail = _native_json(
        executable,
        workspace,
        ["task", "show", task_id, "--project", project_id, "--json"],
    )
    summary = detail.get("summary")
    source = detail.get("source_workspace")
    if (
        not isinstance(summary, dict)
        or summary.get("id") != task_id
        or summary.get("project_id") != project_id
        or not isinstance(summary.get("workflow_id"), str)
        or not isinstance(source, dict)
        or not isinstance(source.get("root_path"), str)
        or (detail.get("worktree_path") or source["root_path"]) != str(workspace)
    ):
        raise KitRuntimeError("native Script Task/workspace identity differs")
    scripts = detail.get("current_scripts")
    if not isinstance(scripts, list) or len(scripts) > 256:
        raise KitRuntimeError("native current Script inventory is unavailable")
    matches = [
        row for row in scripts
        if isinstance(row, dict)
        and isinstance(row.get("current_node"), dict)
        and row["current_node"].get("node_id") == node_id
        and row["current_node"].get("transition_branch_key") == branch_key
    ]
    if len(matches) != 1 or matches[0]["current_node"].get("session_id"):
        raise KitRuntimeError("native current Script ownership is ambiguous")
    return {
        "detail": detail,
        "project_id": project_id,
        "task_id": task_id,
        "short_id": summary.get("short_id"),
        "workflow_id": summary["workflow_id"],
        "current_node_id": node_id,
        "transition_branch_key": branch_key,
        "current_script": matches[0],
        "source_workspace": source,
    }


def _validate_script_session_membership(
    task_detail: Mapping[str, Any],
    *,
    node_id: str,
    session_id: str,
) -> None:
    current_nodes = task_detail.get("current_nodes")
    live_sessions = task_detail.get("live_sessions")
    if (
        not isinstance(current_nodes, list)
        or len(current_nodes) > 256
        or not isinstance(live_sessions, list)
        or len(live_sessions) > 256
        or any(not isinstance(row, dict) for row in current_nodes)
        or any(not isinstance(row, dict) for row in live_sessions)
    ):
        raise KitRuntimeError("native Script Session inventory is incomplete")
    current_matches = [
        row for row in current_nodes
        if row.get("session_id") == session_id
        and row.get("node_id", row.get("id")) == node_id
    ]
    live_matches = [
        row for row in live_sessions
        if row.get("session_id") == session_id
    ]
    if len(current_matches) > 1 or len(live_matches) > 1:
        raise KitRuntimeError("native Script Session membership is ambiguous")
    if not current_matches and not live_matches:
        raise KitRuntimeError("native Script Session is not linked to its Task")


def _session_current_node(task: Mapping[str, Any]) -> str:
    nodes = task["current_nodes"]
    session_id = task["session_id"]
    matching = [
        row for row in nodes
        if row.get("session_id") == session_id
        and isinstance(row.get("node_id", row.get("id")), str)
    ]
    if not matching and len(nodes) == 1:
        matching = nodes
    if len(matching) != 1:
        raise KitRuntimeError("native current node is not uniquely bound to the Session")
    node_id = matching[0].get("node_id", matching[0].get("id"))
    if not isinstance(node_id, str) or not KEY_RE.fullmatch(node_id):
        raise KitRuntimeError("native current node identity is malformed")
    return node_id


def _native_graph(
    executable: Path,
    workspace: Path,
    workflow_id: str,
) -> dict[str, Any]:
    if not KEY_RE.fullmatch(workflow_id):
        raise KitRuntimeError("native Workflow identity is malformed")
    response = _native_json(
        executable,
        workspace,
        ["workflow", "graph", "inspect", workflow_id, "--json"],
        maximum=MAX_NATIVE_GRAPH_BYTES,
    )
    graph = response.get("graph")
    if (
        response.get("workflow_id") != workflow_id
        or not isinstance(graph, dict)
        or any(not isinstance(graph.get(key), list) for key in (
            "nodes", "edges", "transition_groups",
        ))
        or len(graph["nodes"]) > 4096
        or len(graph["edges"]) > 16384
        or len(graph["transition_groups"]) > 8192
    ):
        raise KitRuntimeError("native Workflow graph readback is incomplete")
    return response


def _node_id(row: Mapping[str, Any]) -> Any:
    return row.get("id", row.get("node_id", row.get("transition_group_id")))


def _graph_node(graph: Mapping[str, Any], node_id: str) -> dict[str, Any]:
    matches = [
        row for row in graph["nodes"]
        if isinstance(row, dict) and _node_id(row) == node_id
    ]
    if len(matches) != 1:
        raise KitRuntimeError("native current node is absent or ambiguous in its Workflow")
    return matches[0]


def _validate_script_owner(
    task: Mapping[str, Any],
    graph_response: Mapping[str, Any],
    workspace: Path,
) -> tuple[str, str | None]:
    node_id = task["current_node_id"]
    node = _graph_node(graph_response["graph"], node_id)
    if node.get("kind") != "script":
        raise KitRuntimeError("native Script context does not own a Script node")
    graph_path = node.get("script_path")
    current_path = task["current_script"].get("path")
    if not isinstance(graph_path, str) or not graph_path or not isinstance(current_path, str):
        raise KitRuntimeError("native current Script path is unavailable")
    graph_script = Path(graph_path)
    current_script = Path(current_path)
    if not graph_script.is_absolute():
        graph_script = workspace / graph_script
    if not current_script.is_absolute():
        current_script = workspace / current_script
    try:
        graph_script = graph_script.resolve(strict=True)
        current_script = current_script.resolve(strict=True)
    except (OSError, RuntimeError) as error:
        raise KitRuntimeError("native current Script path is unavailable") from error
    if graph_script != current_script:
        raise KitRuntimeError("native Workflow Script path differs from current execution")
    return node_id, task["transition_branch_key"]


def _validate_agent_owner(
    task: Mapping[str, Any],
    graph_response: Mapping[str, Any],
) -> str:
    node_id = _session_current_node(task)
    node = _graph_node(graph_response["graph"], node_id)
    if node.get("kind") != "agent":
        raise KitRuntimeError("current native Session does not own an Agent node")
    return node_id


def _closed_source_admission(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict) or value.get("schema") != "delivery-source-admission-v2":
        raise KitRuntimeError("native Script admission schema is unsupported")
    expected = {
        "schema", "receipt_ref", "primary_workspace_id", "primary_root",
        "kit_root", "source_ref", "kit_ref", "ci_helper", "helper_bindings",
        "binding", "ingress",
    }
    if set(value) != expected:
        raise KitRuntimeError("native Script admission fields are not closed")
    ref = value["receipt_ref"]
    binding = value["binding"]
    ingress = value["ingress"]
    helpers = value["helper_bindings"]
    binding_kind = binding.get("kind") if isinstance(binding, dict) else None
    if binding_kind == "plan-publication":
        binding_valid = set(binding) == {"kind"}
    elif binding_kind == "r2-installed":
        binding_valid = set(binding) in (
            {"kind", "installation_binding_sha256"},
            {
                "kind",
                "installation_binding_sha256",
                "receipt_workflow_id",
            },
        )
    else:
        binding_valid = False
    if (
        not isinstance(ref, dict) or set(ref) != {"path", "sha256"}
        or not isinstance(binding, dict)
        or not binding_valid
        or not isinstance(ingress, dict) or set(ingress) != {"kind", "edge_id"}
        or ingress.get("kind") not in {"agent-edge", "script-edge"}
        or not isinstance(ingress.get("edge_id"), str)
        or not KEY_RE.fullmatch(ingress["edge_id"])
        or not isinstance(helpers, list) or len(helpers) > 64
    ):
        raise KitRuntimeError("native Script admission identity is malformed")
    if binding.get("kind") == "r2-installed" and (
        not isinstance(binding.get("installation_binding_sha256"), str)
        or DIGEST_RE.fullmatch(binding["installation_binding_sha256"]) is None
    ):
        raise KitRuntimeError("native Script installation binding is malformed")
    if "receipt_workflow_id" in binding and (
        not isinstance(binding["receipt_workflow_id"], str)
        or UUID_RE.fullmatch(binding["receipt_workflow_id"]) is None
    ):
        raise KitRuntimeError("native Script receipt Workflow identity is malformed")
    if (
        not isinstance(ref.get("path"), str)
        or not isinstance(ref.get("sha256"), str)
        or DIGEST_RE.fullmatch(ref["sha256"]) is None
        or not isinstance(value.get("primary_workspace_id"), str)
        or not KEY_RE.fullmatch(value["primary_workspace_id"])
    ):
        raise KitRuntimeError("native Script source reference is malformed")
    for key in ("primary_root", "kit_root", "ci_helper"):
        path = value.get(key)
        if (
            not isinstance(path, str) or not path
            or not Path(path).is_absolute()
            or str(Path(path)) != path
            or ".." in Path(path).parts
            or "\x00" in path
        ):
            raise KitRuntimeError("native Script source path is malformed")
    for key in ("source_ref", "kit_ref"):
        ref_value = value.get(key)
        if (
            not isinstance(ref_value, str)
            or REF_RE.fullmatch(ref_value) is None
            or ref_value.endswith("/HEAD")
            or ref_value.endswith(".lock")
            or any(part in {"", ".", ".."} for part in ref_value.split("/"))
        ):
            raise KitRuntimeError("native Script publication ref is malformed")
    seen_helpers: set[str] = set()
    for helper in helpers:
        if (
            not isinstance(helper, dict) or set(helper) != {"link", "target"}
            or not isinstance(helper.get("link"), str)
            or not isinstance(helper.get("target"), str)
            or not Path(helper["link"]).is_absolute()
            or not Path(helper["target"]).is_absolute()
            or helper["link"] in seen_helpers
        ):
            raise KitRuntimeError("native Script helper binding is malformed")
        seen_helpers.add(helper["link"])
    return value


def _source_receipt_workflow_id(
    admission: Mapping[str, Any],
    workflow_id: str,
) -> str:
    binding = admission.get("binding")
    owner = (
        binding.get("receipt_workflow_id", workflow_id)
        if isinstance(binding, Mapping)
        else workflow_id
    )
    if not isinstance(owner, str) or UUID_RE.fullmatch(owner) is None:
        raise KitRuntimeError("native Script receipt Workflow identity is malformed")
    return owner


def _read_script_ingress_reference(description: Any) -> dict[str, Any]:
    if not isinstance(description, str) or len(description) > 1000:
        raise KitRuntimeError("native Script ingress reference exceeds its bound")
    try:
        reference = _parse_json(description.encode("utf-8"), "native Script ingress reference")
    except UnicodeError as error:
        raise KitRuntimeError("native Script ingress reference is invalid") from error
    if (
        set(reference) != {"schema", "path", "sha256"}
        or reference.get("schema") != SCRIPT_INGRESS_REF_SCHEMA
        or _canonical_json(reference).decode("utf-8") != description
        or not isinstance(reference.get("path"), str)
        or not isinstance(reference.get("sha256"), str)
        or DIGEST_RE.fullmatch(reference["sha256"]) is None
    ):
        raise KitRuntimeError("native Script ingress reference is malformed")
    path = Path(reference["path"])
    raw, _ = _read_regular_file(
        path,
        maximum=MAX_RECEIPT_BYTES,
        private=True,
        exact_mode=0o600,
    )
    if hashlib.sha256(raw).hexdigest() != reference["sha256"]:
        raise KitRuntimeError("native Script ingress document digest drifted")
    frame = _parse_json(raw, "native Script ingress document")
    if (
        set(frame) != {"project_id", "workflow_id", "source_admission"}
        or _canonical_json(frame) != raw
    ):
        raise KitRuntimeError("native Script ingress document is not canonical")
    frame["source_admission"] = _closed_source_admission(frame["source_admission"])
    return frame


def _validate_script_ingress_graph(
    graph_response: Mapping[str, Any],
    *,
    project_id: str,
    workflow_id: str,
) -> dict[str, Any]:
    graph = graph_response["graph"]
    compact = [
        row for row in graph["transition_groups"]
        if isinstance(row, dict)
        and SCRIPT_INGRESS_REF_SCHEMA in str(row.get("description", ""))
    ]
    prompt_markers = 0
    for collection in ("nodes", "edges", "node_groups", "transition_groups"):
        for row in graph.get(collection, []):
            if not isinstance(row, dict):
                raise KitRuntimeError("native Workflow graph row is malformed")
            for key in ("prompt_template", "prompt"):
                prompt = row.get(key)
                if isinstance(prompt, str):
                    prompt_markers += prompt.count(SOURCE_ADMISSION_BEGIN)
                    prompt_markers += prompt.count(SOURCE_ADMISSION_END)
    if len(compact) != 1 or prompt_markers:
        raise KitRuntimeError("native Workflow source admission carrier is ambiguous")
    group = compact[0]
    frame = _read_script_ingress_reference(group.get("description"))
    if (
        frame.get("project_id") != project_id
        or frame.get("workflow_id") != workflow_id
    ):
        raise KitRuntimeError("native Script ingress frame identity differs")
    admission = _closed_source_admission(frame["source_admission"])
    ingress = admission["ingress"]
    if ingress.get("kind") != "script-edge":
        raise KitRuntimeError("native source binding requires Script ingress")
    group_id = _node_id(group)
    if not isinstance(group_id, str):
        raise KitRuntimeError("native Script ingress group identity is malformed")
    edges = [
        row for row in graph["edges"]
        if isinstance(row, dict) and row.get("transition_group_id") == group_id
    ]
    if len(edges) != 1:
        raise KitRuntimeError("native Script ingress group must own one edge")
    edge = edges[0]
    if (
        edge.get("id") != ingress.get("edge_id")
        or edge.get("prompt_template") not in (None, "")
        or edge.get("prompt") not in (None, "")
    ):
        raise KitRuntimeError("native Script ingress edge differs from its reference")
    source_groups = {
        _node_id(row): row for row in graph["transition_groups"]
        if isinstance(row, dict)
    }
    source_group = source_groups.get(group_id)
    if source_group is None:
        raise KitRuntimeError("native Script ingress transition group is missing")
    source_node_id = source_group.get("source_node_id")
    source_node = _graph_node(graph, source_node_id) if isinstance(source_node_id, str) else None
    target_node_id = edge.get("target_node_id")
    target_node = _graph_node(graph, target_node_id) if isinstance(target_node_id, str) else None
    if (
        source_node is None or source_node.get("key") not in {"start", "backlog"}
        or target_node is None or target_node.get("kind") != "script"
    ):
        raise KitRuntimeError("native Script source-admission edge endpoints differ")
    outgoing = []
    for candidate in graph["edges"]:
        candidate_group = source_groups.get(candidate.get("transition_group_id")) if isinstance(candidate, dict) else None
        if isinstance(candidate_group, dict) and candidate_group.get("source_node_id") == source_node_id:
            outgoing.append(candidate)
    if len(outgoing) != 1 or outgoing[0].get("id") != ingress["edge_id"]:
        raise KitRuntimeError("native Workflow has a source-admission bypass route")
    return {
        "frame": frame,
        "admission": admission,
        "ingress_node_id": target_node_id,
    }


def _read_source_ingress_graph(
    graph_response: Mapping[str, Any], *, project_id: str, workflow_id: str,
) -> dict[str, Any]:
    """Pure closed carrier read shared by Agent bootstrap and offline capture.

    Compact references are safely read locally. No native call or Script owner
    is invented here; actual Script ownership remains a separate native gate.
    """
    graph = graph_response.get("graph")
    if not isinstance(graph, dict):
        raise KitRuntimeError("source ingress graph is malformed")
    frames = []
    references = []
    marker_count = [0, 0]
    for collection in ("nodes", "edges", "node_groups", "transition_groups"):
        rows = graph.get(collection, [])
        if not isinstance(rows, list):
            raise KitRuntimeError("source ingress graph collection is malformed")
        for row in rows:
            if not isinstance(row, dict):
                raise KitRuntimeError("source ingress graph row is malformed")
            if SCRIPT_INGRESS_REF_SCHEMA in str(row.get("description", "")):
                references.append((collection, row))
            for key in ("prompt_template", "prompt"):
                prompt = row.get(key)
                if not isinstance(prompt, str):
                    continue
                marker_count[0] += prompt.count(SOURCE_ADMISSION_BEGIN)
                marker_count[1] += prompt.count(SOURCE_ADMISSION_END)
                if SOURCE_ADMISSION_BEGIN in prompt:
                    lines = prompt.splitlines()
                    if not lines or lines[0] != SOURCE_ADMISSION_BEGIN or len(lines) < 3:
                        raise KitRuntimeError("source admission prefix is malformed")
                    frame = _parse_json(lines[1].encode("utf-8"), "source admission prefix")
                    if (set(frame) != {"project_id", "workflow_id", "source_admission"}
                            or _canonical_json(frame).decode("utf-8") != lines[1]):
                        raise KitRuntimeError("source admission prefix is not canonical")
                    admission = _closed_source_admission(frame["source_admission"])
                    if SOURCE_ADMISSION_END not in prompt:
                        raise KitRuntimeError("source admission prefix is incomplete")
                    expected = prompt.split(SOURCE_ADMISSION_END, 1)[0] + SOURCE_ADMISSION_END
                    suffix = prompt[len(expected):]
                    if suffix and not suffix.startswith("\n\n"):
                        raise KitRuntimeError("source admission prefix separator differs")
                    frames.append((collection, row, frame, expected))
    if references:
        if frames or marker_count != [0, 0] or len(references) != 1 or references[0][0] != "transition_groups":
            raise KitRuntimeError("source admission carrier is ambiguous")
        result = _validate_script_ingress_graph(
            graph_response, project_id=project_id, workflow_id=workflow_id,
        )
        result["carrier"] = {"kind": "script-edge", "reference": references[0][1]["description"]}
        return result
    if len(frames) != 1 or marker_count != [1, 1] or frames[0][0] != "edges":
        raise KitRuntimeError("source admission carrier is ambiguous")
    _, edge, frame, prefix = frames[0]
    if frame["project_id"] != project_id or frame["workflow_id"] != workflow_id:
        raise KitRuntimeError("source admission frame identity differs")
    admission = _closed_source_admission(frame["source_admission"])
    if admission.get("ingress") != {"kind": "agent-edge", "edge_id": edge.get("id")}:
        raise KitRuntimeError("source admission Agent ingress differs")
    groups = {_node_id(row): row for row in graph["transition_groups"]}
    group = groups.get(edge.get("transition_group_id"))
    source = _graph_node(graph, group.get("source_node_id")) if group else None
    target = _graph_node(graph, edge.get("target_node_id"))
    if (not source or source.get("key") not in {"start", "backlog"}
            or not target or target.get("kind") != "agent"):
        raise KitRuntimeError("source admission Agent endpoints differ")
    outgoing = [candidate for candidate in graph["edges"]
                if groups.get(candidate.get("transition_group_id"), {}).get("source_node_id") == _node_id(source)]
    if len(outgoing) != 1 or outgoing[0].get("id") != edge.get("id"):
        raise KitRuntimeError("source admission has a bypass route")
    return {"frame": frame, "admission": admission, "ingress_node_id": _node_id(target),
            "carrier": {"kind": "agent-edge", "prefix": prefix}}


def validate_runtime_v3_receipt(receipt, primary, kit_root):
    """Closed metadata validation before importing or invoking publication code.

    Kept identical in the inspector, bootstrap, adapter and operations readers.
    This validates declarations only; it is not source admission or authority.
    """
    def check(ok):
        if not ok:
            raise ValueError("invalid v3 publication metadata")

    def identity(value):
        check(isinstance(value, str) and re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,255}", value))

    def root(value):
        check(isinstance(value, str) and "\x00" not in value
              and Path(value).is_absolute() and str(Path(value)) == value
              and ".." not in Path(value).parts and str(Path(value).resolve()) == value)

    check(isinstance(receipt, dict) and set(receipt) == {
        "schema", "policy", "project_id", "workflow_id", "source_commit", "kit_commit",
        "runtime_roots", "files", "local_inputs", "project_memory",
    })
    check(receipt["schema"] == "delivery-runtime-publication-v3"
          and receipt["policy"] == "delivery_reliability_project_content_v1")
    identity(receipt["project_id"])
    identity(receipt["workflow_id"])
    for name in ("source_commit", "kit_commit"):
        check(isinstance(receipt[name], str) and re.fullmatch(r"[0-9a-f]{40}", receipt[name]))
    roots = receipt["runtime_roots"]
    check(isinstance(roots, list) and 0 < len(roots) <= 256)
    for value in roots:
        root(value)
    check(len(set(roots)) == len(roots) and str(primary) in roots and str(kit_root) in roots)
    files, local_inputs = receipt["files"], receipt["local_inputs"]
    check(isinstance(files, list) and isinstance(local_inputs, list)
          and 0 < len(files) and len(files) + len(local_inputs) <= 512)
    seen = set()
    total = 0
    for local, items in ((False, files), (True, local_inputs)):
        for item in items:
            check(isinstance(item, dict) and set(item) == {
                "root", "relative_path", "sha256", "byte_size", "mode",
            } | ({"kind"} if local else set()))
            name, source_root = item["relative_path"], item["root"]
            check(isinstance(source_root, str) and source_root in roots
                  and isinstance(name, str) and name and not name.startswith("/")
                  and "\\" not in name and "\x00" not in name
                  and all(part not in ("", ".", "..") for part in name.split("/")))
            check(not name.startswith((".kent/runtime/", ".kent/retained-evidence/", "build/")))
            check(isinstance(item["sha256"], str) and re.fullmatch(r"[0-9a-f]{64}", item["sha256"])
                  and type(item["byte_size"]) is int and 0 <= item["byte_size"] <= 8 * 1024 * 1024
                  and type(item["mode"]) is int and 0 <= item["mode"] <= 0o777
                  and not item["mode"] & 0o022)
            check(not any(other_root == source_root and (
                other == name or other.startswith(name + "/") or name.startswith(other + "/"))
                for other_root, other in seen))
            if local:
                check(item["kind"] == "local-instruction" and source_root in {str(primary), str(kit_root)}
                      and name.startswith(".kent/skills/") and name.endswith(".md")
                      and not any(c in name for c in "*?[]") and not item["mode"] & 0o111)
            seen.add((source_root, name))
            total += item["byte_size"]
            check(total <= 32 * 1024 * 1024)
    memory = receipt["project_memory"]
    check(isinstance(memory, dict) and set(memory) == {
        "root", "project_key", "project_uid", "legacy_task_ids",
    })
    root(memory["root"])
    identity(memory["project_key"])
    identity(memory["project_uid"])
    legacy = memory["legacy_task_ids"]
    check(isinstance(legacy, list) and len(legacy) <= 256)
    for value in legacy:
        identity(value)
    check(len(set(legacy)) == len(legacy))
    return len(files) + len(local_inputs)


def validate_source_v2_receipt(receipt, primary, kit_root):
    """Validate source-only content declarations, without granting R2 identity."""
    if (not isinstance(receipt, dict) or set(receipt) != {
        "schema", "policy", "project_id", "workflow_id", "source_commit", "kit_commit",
        "runtime_roots", "files", "local_inputs",
    } or receipt["schema"] != "delivery-source-publication-v2"
            or receipt["policy"] != "published_source_project_content_v1"):
        raise ValueError("invalid source-only v2 publication metadata")
    # Reuse the bounded structural checks only. No synthetic admission or native
    # identity is constructed, returned or used to access project memory.
    return validate_runtime_v3_receipt({
        **receipt, "schema": "delivery-runtime-publication-v3",
        "policy": "delivery_reliability_project_content_v1",
        "project_memory": {"root": str(primary), "project_key": "metadata-only",
                           "project_uid": "metadata-only", "legacy_task_ids": []},
    }, primary, kit_root)


def _read_source_receipt(
    admission: Mapping[str, Any],
    *,
    project_id: str,
    workflow_id: str,
) -> tuple[dict[str, Any], KitRuntimeBinding]:
    receipt_ref = admission["receipt_ref"]
    raw, _ = _read_regular_file(
        receipt_ref["path"],
        maximum=MAX_RECEIPT_BYTES,
        private=True,
    )
    if hashlib.sha256(raw).hexdigest() != receipt_ref["sha256"]:
        raise KitRuntimeError("native source receipt digest drifted")
    receipt = _parse_json(raw, "native source publication receipt")
    schema = receipt.get("schema")
    source_v2 = schema == "delivery-source-publication-v2"
    if source_v2:
        try:
            validate_source_v2_receipt(receipt, admission["primary_root"], admission["kit_root"])
        except ValueError as error:
            raise KitRuntimeError("invalid source-only v2 publication metadata") from error
    v3 = schema == "delivery-runtime-publication-v3"
    if v3:
        try:
            validate_runtime_v3_receipt(receipt, admission["primary_root"], admission["kit_root"])
        except ValueError as error:
            raise KitRuntimeError("invalid v3 publication metadata") from error
    expected_fields = {
        "delivery-runtime-publication-v3": {
            "schema", "project_id", "workflow_id", "source_commit", "kit_commit",
            "runtime_roots", "files", "policy", "project_memory", "local_inputs",
        },
        "delivery-runtime-publication-v1": {
            "schema", "project_id", "workflow_id", "source_commit", "kit_commit",
            "runtime_roots", "files", "policy", "project_memory",
        },
        "delivery-runtime-publication-v2": {
            "schema", "project_id", "workflow_id", "source_commit", "kit_commit",
            "runtime_roots", "files", "policy", "project_memory", "local_inputs",
        },
        "delivery-source-publication-v2": {
            "schema", "project_id", "workflow_id", "source_commit", "kit_commit",
            "runtime_roots", "files", "policy", "local_inputs",
        },
        "delivery-source-publication-v1": {
            "schema", "project_id", "workflow_id", "source_commit", "kit_commit",
            "runtime_roots", "files", "policy", "local_inputs",
        },
    }.get(schema)
    if expected_fields is None or set(receipt) != expected_fields:
        raise KitRuntimeError("native source receipt schema is not closed")
    expected_policy = (
        ("published_source_project_content_v1" if source_v2 else "published_source_v1")
        if schema in ("delivery-source-publication-v1", "delivery-source-publication-v2")
        else "delivery_reliability_project_content_v1" if v3 else "delivery_reliability_v1"
    )
    if (
        receipt.get("project_id") != project_id
        or receipt.get("workflow_id") != workflow_id
        or receipt.get("policy") != expected_policy
    ):
        raise KitRuntimeError("native source receipt identity differs")
    kit_commit = receipt.get("kit_commit")
    if not isinstance(kit_commit, str) or OID_RE.fullmatch(kit_commit) is None:
        raise KitRuntimeError("native source receipt Kit commit is malformed")
    roots = receipt.get("runtime_roots")
    if not isinstance(roots, list) or not roots or len(roots) > 256:
        raise KitRuntimeError("native source receipt roots are malformed")
    normalized_roots: list[str] = []
    for root in roots:
        if not isinstance(root, str):
            raise KitRuntimeError("native source receipt root is malformed")
        normalized = _parse_binding({
            "schema": KIT_RUNTIME_BINDING_SCHEMA,
            "root": root,
            "commit": kit_commit,
            "publication_ref": admission["kit_ref"],
        }).root
        if normalized in normalized_roots:
            raise KitRuntimeError("native source receipt repeats a runtime root")
        normalized_roots.append(normalized)
    kit_root = admission["kit_root"]
    if kit_root not in normalized_roots or admission["primary_root"] not in normalized_roots:
        raise KitRuntimeError("native source receipt roots differ from admission")
    files = receipt.get("files")
    if not isinstance(files, list) or not files or len(files) > 512:
        raise KitRuntimeError("native source receipt inventory is malformed")
    seen: set[tuple[str, str]] = set()
    bootstrap_entries: list[dict[str, Any]] = []
    for item in files:
        if (
            not isinstance(item, dict)
            or set(item) != {"root", "relative_path", "sha256", "byte_size", "mode"}
        ):
            raise KitRuntimeError("native source receipt entry is malformed")
        root = item.get("root")
        path = item.get("relative_path")
        digest = item.get("sha256")
        size = item.get("byte_size")
        mode = item.get("mode")
        if (
            root not in normalized_roots
            or not isinstance(path, str)
            or not path
            or path.startswith("/")
            or "\\" in path
            or any(part in {"", ".", ".."} for part in path.split("/"))
            or not isinstance(digest, str)
            or DIGEST_RE.fullmatch(digest) is None
            or type(size) is not int
            or not 0 <= size <= MAX_SOURCE_BYTES
            or type(mode) is not int
            or not 0 <= mode <= 0o777
            or mode & 0o022
            or (root, path) in seen
        ):
            raise KitRuntimeError("native source receipt entry fields are malformed")
        seen.add((root, path))
        if root == kit_root and path == "workflowkit/kit_runtime.py":
            bootstrap_entries.append(item)
    if len(bootstrap_entries) != 1:
        raise KitRuntimeError("native source receipt lacks one Kit bootstrap entry")
    entry = bootstrap_entries[0]
    published_bytes, published_mode = _git_blob(
        Path(kit_root), kit_commit, "workflowkit/kit_runtime.py",
    )
    if (
        len(published_bytes) != entry["byte_size"]
        or hashlib.sha256(published_bytes).hexdigest() != entry["sha256"]
        or bool(published_mode & 0o111) != bool(entry["mode"] & 0o111)
    ):
        raise KitRuntimeError("native source receipt does not bind the Kit bootstrap")
    checked_out_bytes, checked_out_mode = _read_regular_file(
        Path(kit_root) / "workflowkit" / "kit_runtime.py",
        maximum=MAX_SOURCE_BYTES,
    )
    if (
        checked_out_bytes != published_bytes
        or bool(checked_out_mode & 0o111) != bool(entry["mode"] & 0o111)
        or bool(checked_out_mode & 0o111) != bool(published_mode & 0o111)
    ):
        raise KitRuntimeError("native Kit bootstrap checkout differs from its receipt")
    return receipt, _parse_binding({
        "schema": KIT_RUNTIME_BINDING_SCHEMA,
        "root": kit_root,
        "commit": kit_commit,
        "publication_ref": admission["kit_ref"],
    })


def _git_blob(root: Path, commit: str, relative_path: str) -> tuple[bytes, int]:
    try:
        listing = _git(
            root, "ls-tree", "-z", commit, "--", relative_path,
            maximum=4096,
        )
        rows = listing.split(b"\0")
        if len(rows) != 2 or not rows[0]:
            raise KitRuntimeError("committed SDK source path is absent or ambiguous")
        metadata, raw_path = rows[0].split(b"\t", 1)
        mode_text, object_type, object_id = metadata.decode("ascii").split(" ")
        if (
            raw_path.decode("utf-8") != relative_path
            or object_type != "blob"
            or mode_text not in {"100644", "100755"}
            or OID_RE.fullmatch(object_id) is None
        ):
            raise KitRuntimeError("committed SDK source path is not a regular blob")
        raw = _git(root, "cat-file", "blob", object_id)
    except (UnicodeError, ValueError) as error:
        raise KitRuntimeError("committed SDK source path is invalid") from error
    return raw, (0o755 if mode_text == "100755" else 0o644)


def _sdk_initial_binding(
    *,
    project_root: Path,
    profile: Mapping[str, Any],
    task: Mapping[str, Any],
    session: Mapping[str, Any] | None,
    graph: Mapping[str, Any],
    native_script_context: Mapping[str, Any] | None = None,
) -> KitRuntimeBinding:
    detail = task.get("detail")
    if not isinstance(detail, dict):
        raise KitRuntimeError("native SDK Task detail is unavailable")
    script_context: dict[str, Any] | None = None
    if native_script_context is not None:
        if set(native_script_context) not in (
            {"task_id", "node_id"},
            {"task_id", "node_id", "transition_branch_key"},
        ):
            raise KitRuntimeError("native SDK Script context has an invalid shape")
        task_id = native_script_context.get("task_id")
        node_id = native_script_context.get("node_id")
        branch_key = native_script_context.get("transition_branch_key")
        if (
            not isinstance(task_id, str)
            or KEY_RE.fullmatch(task_id) is None
            or not isinstance(node_id, str)
            or KEY_RE.fullmatch(node_id) is None
            or (
                branch_key is not None
                and (
                    not isinstance(branch_key, str)
                    or KEY_RE.fullmatch(branch_key) is None
                )
            )
            or task.get("task_id") != task_id
            or task.get("current_node_id") != node_id
            or task.get("transition_branch_key") != branch_key
            or not isinstance(task.get("current_script"), dict)
        ):
            raise KitRuntimeError("native SDK Script context differs from current Task")
        script_context = dict(native_script_context)
    elif not isinstance(session, Mapping):
        raise KitRuntimeError("native SDK Agent Session identity is unavailable")
    target = detail.get("execution_target")
    if (
        not isinstance(target, dict)
        or target.get("provenance") != "resolved"
        or not isinstance(target.get("commit_oid"), str)
        or OID_RE.fullmatch(target["commit_oid"]) is None
    ):
        raise KitRuntimeError("native SDK initial source target is unresolved")
    initial_commit = target["commit_oid"]
    try:
        initial_tree = _git(
            project_root, "rev-parse", "--verify", f"{initial_commit}^{{tree}}",
        ).decode("ascii").strip()
    except (UnicodeError, KitRuntimeError) as error:
        raise KitRuntimeError("native SDK initial source tree is unavailable") from error
    if OID_RE.fullmatch(initial_tree) is None:
        raise KitRuntimeError("native SDK initial source tree is malformed")

    initial_profile_raw, _ = _git_blob(
        project_root, initial_commit, ".kent/workflow-profile.toml",
    )
    header_raw, _ = _git_blob(
        project_root, initial_commit, ".kent/workflows/sdk-initial-source-admission.txt",
    )
    try:
        initial_profile = tomllib.loads(initial_profile_raw.decode("utf-8"))
        header = header_raw.decode("utf-8")
    except (UnicodeError, tomllib.TOMLDecodeError) as error:
        raise KitRuntimeError("native SDK initial profile/header is invalid") from error
    # The committed SDK header is a template: C and its tree are rendered into
    # the native graph at ingress. Its adapter must compare that native
    # projection with this exact committed template and reject unresolved
    # placeholders in the rendered header; rejecting the committed template
    # here would make the C/tree binding self-referential.
    if not isinstance(initial_profile, dict):
        raise KitRuntimeError("native SDK initial profile is invalid")
    commands = initial_profile.get("commands")
    if (
        not isinstance(commands, dict)
        or commands.get(SDK_SOURCE_BINDING_COMMAND) != SDK_SOURCE_BINDING_PATH
    ):
        raise KitRuntimeError("native SDK initial profile does not pin its source adapter")
    initial_binding = _profile_table_binding(initial_profile.get("kit_runtime"))
    current_binding = _profile_table_binding(profile.get("kit_runtime"))
    if initial_binding is None or current_binding is None:
        raise KitRuntimeError("native SDK initial profile lacks a Kit source binding")
    if initial_binding != current_binding:
        raise KitRuntimeError("native SDK initial and current Kit bindings differ")

    adapter_raw, _adapter_mode = _git_blob(
        project_root, initial_commit, SDK_SOURCE_BINDING_PATH,
    )
    try:
        import ast

        parsed = ast.parse(adapter_raw.decode("utf-8"))
        for node in ast.walk(parsed):
            if isinstance(node, ast.Import):
                names = [alias.name for alias in node.names]
            elif isinstance(node, ast.ImportFrom):
                if node.level:
                    raise KitRuntimeError("native SDK source adapter uses a relative import")
                names = [node.module] if node.module else []
            else:
                continue
            if any(
                name.split(".", 1)[0] not in sys.stdlib_module_names
                for name in names
            ):
                raise KitRuntimeError("native SDK source adapter imports non-stdlib code")
        namespace: dict[str, Any] = {
            "__name__": "_kent_initial_sdk_source_binding",
            "__file__": f"<native-initial-source:{initial_commit}:{SDK_SOURCE_BINDING_PATH}>",
        }
        exec(compile(adapter_raw, namespace["__file__"], "exec"), namespace)
        validator = namespace.get("validate_source_binding")
        if not callable(validator):
            raise KitRuntimeError("native SDK source adapter has no validator")
        current_session = None
        if script_context is None:
            current_session = dict(session)
            current_session["session_id"] = (
                task.get("session_id") or os.environ.get("KENT_SESSION_ID")
            )
        arguments = dict(
            project_root=project_root,
            task=detail,
            session=current_session,
            graph=dict(graph),
            initial_commit=initial_commit,
            initial_tree=initial_tree,
            profile=initial_profile,
            header=header,
        )
        if script_context is not None:
            arguments["native_script_context"] = script_context
        result = validator(**arguments)
    except KitRuntimeError:
        raise
    except Exception as error:
        raise KitRuntimeError("native SDK source adapter rejected current identity") from error
    binding = _parse_binding(result)
    if binding != initial_binding:
        raise KitRuntimeError("native SDK source adapter returned another Kit binding")
    return binding


def _validate_loader_profile(
    project_root: Path,
    profile: Mapping[str, Any],
    binding: KitRuntimeBinding,
) -> None:
    commands = profile.get("commands")
    managed = profile.get("kit_managed_commands")
    versions = profile.get("command_versions")
    if (
        not isinstance(commands, dict)
        or commands.get("kit_runtime_loader") != ".kent/scripts/workflow_kit_runtime.py"
        or not isinstance(managed, list)
        or "kit_runtime_loader" not in managed
        or not isinstance(versions, dict)
        or versions.get("kit_runtime_loader") != "1.0.0"
    ):
        raise KitRuntimeError("project profile does not manage the Kit bootstrap")
    source_adapter = commands.get(SDK_SOURCE_BINDING_COMMAND)
    if source_adapter not in (None, "", SDK_SOURCE_BINDING_PATH):
        raise KitRuntimeError("project profile selects an unknown SDK binding adapter")
    configured = _profile_table_binding(profile.get("kit_runtime"))
    if configured != binding:
        raise KitRuntimeError("native source binding differs from the project profile")
    target = project_root / ".kent" / "scripts" / "workflow_kit_runtime.py"
    source_root = _canonical_directory(binding.root, "published Kit root")
    source_bytes, source_mode = _git_blob(
        source_root, binding.commit, "workflowkit/kit_runtime.py",
    )
    receipt_bytes, receipt_mode = _read_regular_file(
        source_root / "workflowkit" / "kit_runtime.py",
        maximum=MAX_SOURCE_BYTES,
    )
    if (
        source_bytes != receipt_bytes
        or bool(source_mode & 0o111) != bool(receipt_mode & 0o111)
    ):
        raise KitRuntimeError("published Kit bootstrap differs from its committed source")
    source_stat_mode = stat.S_IMODE(
        (source_root / "workflowkit" / "kit_runtime.py").stat().st_mode
    )
    if bool(source_stat_mode & 0o111) != bool(source_mode & 0o111):
        raise KitRuntimeError("published Kit bootstrap mode differs from its Git tree")
    copied_bytes, copied_mode = _read_regular_file(
        target,
        maximum=MAX_SOURCE_BYTES,
    )
    if copied_bytes != source_bytes or not copied_mode & 0o111:
        raise KitRuntimeError("managed project Kit bootstrap differs from the source cohort")


def qualified_kit_runtime(
    project_root: str | os.PathLike[str] | None = None,
    source_binding: Mapping[str, Any] | None = None,
) -> QualifiedKitRuntime | None:
    """Derive and qualify the Kit binding from current native authority."""
    if project_root is None:
        source_file = Path(__file__).resolve()
        if source_file.parent.name == "scripts" and source_file.parent.parent.name == ".kent":
            project_value = source_file.parents[2]
        elif source_file.parent.name == "workflowkit":
            project_value = source_file.parents[1]
        else:
            raise KitRuntimeError("project root cannot be derived from the bootstrap")
    else:
        project_value = project_root
    project = _canonical_directory(project_value, "native project root")
    try:
        profile, _profile_raw = _profile_data(project)
    except KitRuntimeError as error:
        # The checkpoint facade preserves the legacy global-loader path for
        # roots without an opt-in profile. Do not treat an unsafe or malformed
        # existing profile as an opt-out.
        if isinstance(error.__cause__, FileNotFoundError):
            return None
        raise
    profile_binding = _profile_table_binding(profile.get("kit_runtime"))
    if profile_binding is None:
        raise KitRuntimeError("project has not opted into a qualified Kit runtime")
    _validate_loader_profile(project, profile, profile_binding)

    commands = profile.get("commands")
    source_binding_command = (
        commands.get(SDK_SOURCE_BINDING_COMMAND)
        if isinstance(commands, dict)
        else None
    )
    executable = _native_executable()
    project_id = _native_project_id(executable, project)
    native_script_context: Mapping[str, Any] | None = None
    if source_binding is not None:
        if not isinstance(source_binding, Mapping):
            raise KitRuntimeError("native Script context is malformed")
        task = _task_from_script_context(
            executable, project, project_id, source_binding,
        )
        workflow_id = task["workflow_id"]
        graph_response = _native_graph(executable, project, workflow_id)
        _validate_script_owner(task, graph_response, project)
        session_status: dict[str, Any] | None = None
        if source_binding_command:
            # Native Script runners have Task/Node context, not Agent Sessions.
            native_script_context = dict(source_binding)
        native_context = source_binding
    else:
        session_id = os.environ.get("KENT_SESSION_ID", "")
        session_status = _validate_session_worktree(
            executable, project, session_id,
        )
        task = _current_session_task(
            executable, project, project_id, session_status,
        )
        workflow_id = task.get("workflow_id")
        if not isinstance(workflow_id, str):
            raise KitRuntimeError("native current Task Workflow identity is unavailable")
        graph_response = _native_graph(executable, project, workflow_id)
        _validate_agent_owner(task, graph_response)
        task["session_id"] = session_id
        native_context = None

    if source_binding_command:
        expected_binding = _sdk_initial_binding(
            project_root=project,
            profile=profile,
            task=task,
            session=(
                {
                    **session_status,
                    "raw": session_status.get("raw"),
                }
                if session_status is not None
                else None
            ),
            graph=graph_response,
            native_script_context=native_script_context,
        )
    else:
        ingress = _read_source_ingress_graph(
            graph_response,
            project_id=project_id,
            workflow_id=workflow_id,
        )
        admission = ingress["admission"]
        source_root = _canonical_directory(
            admission["primary_root"], "native project source root",
        )
        native_source = task.get("source_workspace")
        if not isinstance(native_source, dict):
            native_source = task.get("detail", {}).get("source_workspace", {})
        if (
            not isinstance(native_source, dict)
            or native_source.get("workspace_id") != admission["primary_workspace_id"]
            or not isinstance(native_source.get("root_path"), str)
            or str(_canonical_directory(
                native_source["root_path"], "native primary workspace",
            )) != str(source_root)
        ):
            raise KitRuntimeError("native source admission differs from the project workspace")
        receipt_workflow_id = _source_receipt_workflow_id(
            admission,
            workflow_id,
        )
        _receipt, expected_binding = _read_source_receipt(
            admission,
            project_id=project_id,
            workflow_id=receipt_workflow_id,
        )

    if expected_binding != profile_binding:
        raise KitRuntimeError("native Kit source binding differs from project profile")
    selected_root = _qualify_git_cohort(expected_binding)
    _validate_loader_profile(project, profile, expected_binding)

    if not source_binding_command and ingress.get("carrier", {}).get("kind") == "agent-edge":
        if _receipt["schema"] not in ("delivery-runtime-publication-v3", "delivery-source-publication-v2"):
            raise KitRuntimeError("Agent prefix bootstrap requires the explicit content policy")
        delivery = _import_kit_module("delivery", selected_root)
        expected_prefix = delivery.source_admission_gate(
            ingress["admission"], project_id=project_id, workflow_id=workflow_id,
            project_root=ingress["admission"]["primary_root"],
        )
        if ingress["carrier"]["prefix"] != expected_prefix:
            raise KitRuntimeError("source admission prefix differs from its closed frame")

    if not source_binding_command and (native_context is not None or
            _receipt["schema"] in ("delivery-runtime-publication-v3", "delivery-source-publication-v2")):
        operations = _import_kit_module("operations", selected_root)
        try:
            # Initial ingress admission is deliberately strict. Later native
            # Scripts reuse its graph-bound frame after owner/path validation.
            if native_context is not None and task["current_node_id"] == ingress["ingress_node_id"]:
                verified = operations.admit_script_source_ingress(
                    project,
                    native_context,
                    kent_path=executable,
                )
            else:
                verified = operations.admit_source_admission(
                    ingress["admission"],
                    project_id=project_id,
                    workflow_id=workflow_id,
                    project_root=ingress["admission"]["primary_root"],
                    workspace_path=project,
                    kent_path=executable,
                )
        except Exception as error:
            raise KitRuntimeError("native Script source admission failed") from error
        if (
            verified.project_id != project_id
            or verified.workflow_id != workflow_id
            or verified.primary_root != ingress["admission"]["primary_root"]
            or verified.kit_root != expected_binding.root
            or verified.value.get("kit_ref") != expected_binding.publication_ref
            or verified.value != ingress["admission"]
            or verified.receipt_sha256
            != ingress["admission"]["receipt_ref"]["sha256"]
        ):
            raise KitRuntimeError("verified native Script source admission differs")

    return QualifiedKitRuntime(
        project_root=project,
        source_root=selected_root,
        binding=expected_binding,
        native_authorized=True,
    )


@dataclass(frozen=True)
class VerifiedCiCommand:
    path: Path
    arguments: tuple[str, ...]

    def __reduce__(self):
        raise TypeError("qualified commands are invocation-local")


def admitted_ci_command(project_root, native_context, *, operation):
    """Qualify the entire serving archive cohort before dispatching its CLI."""
    if operation not in {None, "archive_script_ci_report_v2", "archive_script_ci_report", "archive_ci_report"}:
        raise KitRuntimeError("unsupported admitted CI operation")
    from . import ci_contract

    root = _canonical_directory(project_root, "CI project root")
    context_task = native_context.get("task_id") if isinstance(native_context, Mapping) else None
    if not isinstance(context_task, str):
        raise KitRuntimeError("native Script context is unavailable")
    executable = _native_executable()
    project_id = _native_project_id(executable, root)
    task = _task_from_script_context(executable, root, project_id, native_context)
    graph = _native_graph(executable, root, task["workflow_id"])
    _validate_script_owner(task, graph, root)
    node = _graph_node(graph["graph"], task["current_node_id"])
    if operation is None and node.get("key") != "ci_prepare":
        raise KitRuntimeError("prepare dispatch requires its native Script node")
    resolver = (
        ci_contract.resolve_script_ci_archive_readers_admitted
        if operation in {None, "archive_script_ci_report_v2"}
        else ci_contract.resolve_script_ci_archive_readers
    )
    resolver(
        root, task["short_id"], dict(native_context),
        native_read=ci_contract._native_read_adapter(root),
    )
    source_root = Path(__file__).resolve(strict=True).parents[1]
    # Loaded resolver dependencies must originate in the same qualified K;
    # the snapshot above checks their current bytes against its immutable Git.
    for name, module in tuple(sys.modules.items()):
        if name == "workflowkit" or name.startswith("workflowkit."):
            origin = getattr(module, "__file__", None)
            if not isinstance(origin, str) or not Path(origin).resolve().is_relative_to(source_root):
                raise KitRuntimeError("CI module escaped its qualified cohort")
    return VerifiedCiCommand(
        source_root / "scripts/prepare-github-ci",
        ("--admitted-reader-source",) if operation is None else (),
    )


def kit_module(
    name: str,
    project_root: str | os.PathLike[str] | None = None,
    qualified: QualifiedKitRuntime | None = None,
) -> ModuleType:
    """Import one first-party module from the qualified source cohort."""
    selected = qualified or qualified_kit_runtime(project_root)
    if selected is None:
        raise KitRuntimeError("project has no qualified Kit source binding")
    if not selected.native_authorized:
        raise KitRuntimeError("source-only Kit handles are not runtime authority")
    return selected.module(name)


def delivery_module(
    project_root: str | os.PathLike[str] | None = None,
    qualified: QualifiedKitRuntime | None = None,
) -> ModuleType:
    """Return delivery.py from the one qualified Kit cohort."""
    return kit_module("delivery", project_root, qualified)
