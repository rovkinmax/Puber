"""Owned native observations and explicitly selected ordinary mock fixtures.

Canonical lanes never use the mock factories or manufacture qualification.
"""
from __future__ import annotations

from contextlib import contextmanager
import hashlib
import json
import os
from pathlib import Path
import shutil
import stat
import subprocess
import sys
import tempfile
import uuid

sys.dont_write_bytecode = True
SOURCE_ROOT = Path(__file__).resolve().parents[3]
PROJECT_ID = "project-6c1b6c4e-c8e2-4170-b9f7-a7d9c4602176"
TASK_ID = "task-12345678-1234-4234-8234-123456789abc"
WORKSPACE_ID = "workspace-puber-native-fixture"
PUBLICATION_REF = "refs/remotes/origin/main"


KIT_CANDIDATE_PATHS = frozenset({
    "workflowkit/runtime.py", "workflowkit/revision.py", "workflowkit/revision_cli.py",
    "workflowkit/__init__.py", ".kent/scripts/workflow_runtime_contracts.py",
    "tests/test_runtime_contracts.py", "tests/test_revision.py", "tests/test_release_spec.py",
    "tests/test_source_capture_streaming.py", "contracts/runtime-source-capture.md", "README.md",
})
ARCHIVE_KIT_CANDIDATE_PATHS = frozenset({
    "workflowkit/operations.py", "workflowkit/kit_runtime.py", "workflowkit/ci_contract.py",
    "scripts/prepare-github-ci", "templates/project/workflow-checkpoint",
    "templates/project/workflow-prepare-github-ci", "templates/project/workflow-wait-github-ci",
    "templates/project/workflow-evidence-ledger", ".kent/scripts/workflow-checkpoint",
    ".kent/scripts/workflow-evidence-ledger", "tests/test_delivery_source_admission.py",
    "tests/test_kit_runtime.py", "tests/test_ci_audit_targets.py", "tests/test_ci_contract.py",
    "tests/test_workflow_runtime.py", "tests/test_canonical_workflow_lifecycle.py",
    "contracts/evidence-recovery.md",
})
ARCHIVE_KIT_BASE = "c4d89d9ccea33251af9ef78e0d97f78879da6176"
ARCHIVE_KIT_DONOR = Path(
    "/Users/rovkinmax/.kent/worktrees/kent-engineering-kit/archive-baseline-c4-456"
)


def candidate_freeze_argument(args):
    if not args:
        return None
    if len(args) != 2 or args[0] != "--kit-candidate-freeze":
        raise ValueError("expected only --kit-candidate-freeze PATH")
    return Path(args[1]).resolve(strict=True)


def prepare_candidate_kit(sandbox, directory, donor, freeze_path):
    """Commit an exact frozen byte snapshot only into an owned disposable clone."""
    freeze = json.loads(Path(freeze_path).read_text())
    if set(freeze) != {"source_root", "base_commit", "files"}:
        raise AssertionError("candidate freeze has an unapproved baseline or shape")
    records = freeze["files"]
    paths = {row["path"] for row in records}
    if freeze["base_commit"] == "a56c0495c1b932b18eb66377782dbf81f0b4188b":
        donor = Path("/Users/rovkinmax/.kent/worktrees/kent-engineering-kit/runtime-content-a56c0495")
        if len(records) != len(KIT_CANDIDATE_PATHS) or paths != KIT_CANDIDATE_PATHS:
            raise AssertionError("streaming freeze must contain exactly the approved eleven paths")
    elif freeze["base_commit"] == ARCHIVE_KIT_BASE:
        if not paths or len(records) != len(paths) or not paths <= ARCHIVE_KIT_CANDIDATE_PATHS:
            raise AssertionError("archive freeze exceeds the approved seventeen-path cohort")
        donor = ARCHIVE_KIT_DONOR
    else:
        raise AssertionError("candidate freeze has an unapproved baseline")
    source = Path(freeze["source_root"]).resolve(strict=True)
    if str(source) != freeze["source_root"]:
        raise AssertionError("candidate snapshot root must be canonical")
    donor = Path(donor).resolve(strict=True)
    sandbox.allow_read(donor)
    if sandbox.git_read(donor, "rev-parse", "HEAD") != freeze["base_commit"]:
        raise AssertionError("candidate fixture donor must be the exact published cohort baseline")
    if sandbox.git_read(donor, "status", "--porcelain=v1", "--untracked-files=all"):
        raise AssertionError("candidate fixture donor must remain clean")
    destination = Path(directory) / "candidate-kit"
    sandbox.git_owned(directory, "clone", "--shared", "--no-checkout", str(donor), str(destination))
    sandbox.git_owned(destination, "checkout", "--detach", freeze["base_commit"])
    for row in records:
        if set(row) != {"path", "sha256", "mode"}:
            raise AssertionError("candidate file record is not closed")
        original = source / row["path"]
        if (original.is_symlink() or not stat.S_ISREG(original.stat().st_mode)
                or not original.resolve(strict=True).is_relative_to(source)):
            raise AssertionError("candidate file escaped snapshot")
        raw = original.read_bytes()
        mode = int(row["mode"], 8)
        if mode not in {0o600, 0o644, 0o700, 0o744, 0o755} or stat.S_IMODE(original.stat().st_mode) != mode:
            raise AssertionError("candidate mode differs from frozen record")
        if hashlib.sha256(raw).hexdigest() != row["sha256"]:
            raise AssertionError("candidate bytes differ from frozen record")
        target = destination / row["path"]
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(raw)
        target.chmod(mode)
    sandbox.git_owned(destination, "config", "user.email", "fixture@example.invalid")
    sandbox.git_owned(destination, "config", "user.name", "Disposable candidate fixture")
    sandbox.git_owned(destination, "add", "--", *sorted(paths))
    sandbox.git_owned(destination, "commit", "-qm", "Frozen source cohort fixture")
    commit = sandbox.git_owned(destination, "rev-parse", "HEAD")
    sandbox.git_owned(destination, "update-ref", PUBLICATION_REF, commit)
    assert sandbox.git_owned(destination, "status", "--porcelain=v1", "--untracked-files=all") == ""
    return destination, commit


def install_fixture_kit(project, kit, commit):
    import tomllib
    profile = Path(project) / ".kent/workflow-profile.toml"
    text = profile.read_text()
    binding = tomllib.loads(text)["kit_runtime"]
    text = text.replace(f'root = "{binding["root"]}"', f'root = "{kit}"')
    text = text.replace(f'commit = "{binding["commit"]}"', f'commit = "{commit}"')
    profile.write_text(text)
    for target, source in (
        ("workflow-checkpoint", "templates/project/workflow-checkpoint"),
        ("workflow-prepare-github-ci", "templates/project/workflow-prepare-github-ci"),
        ("workflow-evidence-ledger", "templates/project/workflow-evidence-ledger"),
        ("workflow-wait-github-ci", "templates/project/workflow-wait-github-ci"),
        ("workflow_kit_runtime.py", "workflowkit/kit_runtime.py"),
    ):
        destination = Path(project) / ".kent/scripts" / target
        mode = stat.S_IMODE(destination.stat().st_mode)
        destination.write_bytes((Path(kit) / source).read_bytes())
        destination.chmod(mode)
    runtime = Path(project) / ".kent/scripts/workflow_runtime_contracts.py"
    shutil.copy2(Path(kit) / ".kent/scripts/workflow_runtime_contracts.py", runtime)
    assert runtime.read_bytes() == (Path(kit) / ".kent/scripts/workflow_runtime_contracts.py").read_bytes()
    assert bool(runtime.stat().st_mode & 0o111) == bool((Path(kit) / ".kent/scripts/workflow_runtime_contracts.py").stat().st_mode & 0o111)


def verify_kit_binding(project, *, sandbox=None, publication="exact"):
    """Verify the current test profile without selecting or installing a runtime."""
    import tomllib
    binding = tomllib.loads((Path(project) / ".kent/workflow-profile.toml").read_text())["kit_runtime"]
    if (not isinstance(binding, dict)
        or set(binding) != {"schema_version", "root", "commit", "publication_ref"}
        or binding["schema_version"] != 1
        or binding["commit"] != "2708c3391905ae10dc9e402d4f4ec0b4de020c9c"
        or binding["publication_ref"] != PUBLICATION_REF):
        raise AssertionError("test requires exact reviewed current Kit binding")
    kit = Path(binding["root"]).resolve(strict=True)
    if str(kit) != binding["root"]:
        raise AssertionError("test Kit root must be canonical")
    override = os.environ.get("PUBER_STAGE_A_KIT_ROOT")
    if override and Path(override).resolve(strict=True) != kit:
        raise AssertionError("test Kit override differs from profile")
    if publication not in {"exact", "ancestor"}:
        raise ValueError("unknown publication verification policy")
    if sandbox is not None:
        sandbox.allow_read(kit)
    def read(*args):
        if sandbox is not None:
            return sandbox.git_read(kit, *args)
        env = safe_environment(Path(os.environ["HOME"]))
        return subprocess.check_output(["git", "--no-replace-objects", "-c",
            f"core.hooksPath={os.devnull}", "-c", "core.fsmonitor=false", "-C", str(kit), *args],
            env=env, text=True).strip()
    if read("rev-parse", "HEAD") != binding["commit"]:
        raise AssertionError("test Kit HEAD differs from profile")
    if publication == "exact":
        if read("rev-parse", "--verify", PUBLICATION_REF) != binding["commit"]:
            raise AssertionError("test Kit publication ref differs from profile")
    else:
        read("merge-base", "--is-ancestor", binding["commit"], PUBLICATION_REF)
    if read("status", "--porcelain=v1", "--untracked-files=all"):
        raise AssertionError("test Kit must remain clean")
    return kit, binding


class MockCheckpointFacade:
    """Explicit mock-only runtime; never used by the actual D13 handle adapter."""

    def __init__(self, kit, *, variant, commit=None, default_root=None):
        if variant not in {"intent", "publisher", "intent-shim", "publisher-shim"}:
            raise ValueError("unknown mock facade variant")
        self.kit = Path(kit).resolve(strict=True)
        self.variant, self.commit, self.default_root = variant, commit, default_root
        self.handles = {}

    def qualified_kit_runtime(self, project_root=None, source_binding=None):
        from types import SimpleNamespace
        root = Path(project_root if project_root is not None else self.default_root).resolve(strict=False)
        if source_binding is not None and (
            not isinstance(source_binding, dict)
            or set(source_binding) != {"task_id", "node_id"}
            or (self.variant != "publisher" and any(
                not isinstance(value, str) or not value for value in source_binding.values()))
        ):
            raise ValueError("invalid test-only native Script fixture")
        if root not in self.handles:
            handle = SimpleNamespace(project_root=root, source_root=self.kit, native_authorized=True)
            if self.variant in {"publisher", "publisher-shim"}:
                handle.module = lambda name: self.kit_module(name, project_root=root, qualified=handle)
            if self.variant == "publisher-shim":
                handle.binding = SimpleNamespace(commit=self.commit)
            self.handles[root] = handle
        return self.handles[root]

    def kit_module(self, name, project_root=None, qualified=None):
        import importlib
        if project_root is None and self.variant in {"publisher", "publisher-shim"} and qualified is not None:
            project_root = qualified.project_root
        root = Path(project_root).resolve(strict=False)
        if self.handles.get(root) is not qualified:
            raise ValueError("foreign test-only native runtime handle")
        if str(self.kit) not in sys.path:
            sys.path.insert(0, str(self.kit))
        selected = name if name == "workflowkit" or name.startswith("workflowkit.") else "workflowkit." + name
        module = importlib.import_module(selected)
        if not Path(module.__file__).resolve().is_relative_to(self.kit):
            raise ValueError("test Kit module escaped selected fixture root")
        return module

    def delivery_module(self, project_root=None, qualified=None):
        root = Path(project_root if project_root is not None else self.default_root).resolve(strict=False)
        selected = qualified or self.qualified_kit_runtime(project_root=root, source_binding=None)
        module = self.kit_module("delivery", project_root=root, qualified=selected)
        return MockDeliveryProxy(module) if self.variant.endswith("-shim") else module


class MockDeliveryProxy:
    def __init__(self, module):
        self._module = module

    def __getattr__(self, name):
        return getattr(self._module, name)

    def admit_script_source_ingress(self, root, native_context, *, kent_path):
        from types import SimpleNamespace
        if set(native_context) != {"task_id", "node_id"}:
            raise ValueError("invalid fixture native context")
        return SimpleNamespace(project_id=PROJECT_ID,
            workflow_id="10d8adb2-c74c-4ef0-8b5c-311cb5cd0459",
            source_admission_sha256="f" * 64, receipt_sha256="e" * 64, gate="test-only")


def mock_checkpoint_source(kit, *, variant, commit=None):
    if variant not in {"intent-shim", "publisher-shim"}:
        raise ValueError("generated checkpoint requires explicit mock shim variant")
    return f"""
from pathlib import Path as _Path
import sys as _sys
_sys.path.insert(0, str(_Path(__file__).resolve().parent / 'tests'))
from puber_native_fixture import MockCheckpointFacade as _MockCheckpointFacade
_mock_checkpoint = _MockCheckpointFacade({str(kit)!r}, variant={variant!r},
    commit={commit!r}, default_root=_Path(__file__).resolve().parents[2])
qualified_kit_runtime = _mock_checkpoint.qualified_kit_runtime
kit_module = _mock_checkpoint.kit_module
delivery_module = _mock_checkpoint.delivery_module
"""


def canonical(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()


def safe_environment(home, inherited=None):
    source = os.environ if inherited is None else inherited
    allowed = {"PATH", "USER", "LOGNAME", "TMPDIR", "TMP", "TEMP", "TERM", "LANG", "LC_ALL",
               "JAVA_HOME", "ANDROID_HOME", "ANDROID_SDK_ROOT", "GRADLE_USER_HOME", "KOTLIN_HOME", "CI"}
    result = {key: value for key, value in source.items() if key in allowed}
    result.update(HOME=str(home), PYTHONDONTWRITEBYTECODE="1", GIT_CONFIG_NOSYSTEM="1",
                  GIT_CONFIG_SYSTEM=os.devnull, GIT_CONFIG_GLOBAL=os.devnull,
                  GIT_NO_REPLACE_OBJECTS="1", GIT_NO_LAZY_FETCH="1", GIT_TERMINAL_PROMPT="0",
                  GIT_OPTIONAL_LOCKS="0")
    return result


class FixtureSandbox:
    """An explicit owner, including Git routing inside fixture child processes."""

    @classmethod
    @contextmanager
    def create(cls):
        with tempfile.TemporaryDirectory(prefix="puber-owned-") as directory:
            sandbox = cls(Path(directory).resolve())
            try:
                yield sandbox
            finally:
                sandbox.closed = True

    def __init__(self, root):
        self.root = root
        self.closed = False
        self.home = root / "home"
        self.home.mkdir(mode=0o700)
        self.bin = self.home / ".kent/bin"
        self.bin.mkdir(parents=True, mode=0o700)
        self.real_git = shutil.which("git", path=os.defpath)
        if not self.real_git:
            raise AssertionError("Git is unavailable")
        self.state = root / "git-ownership.json"
        self.state.write_bytes(canonical({"root": str(root), "containers": [], "repositories": [],
                                         "read_only": [str(SOURCE_ROOT)], "git": self.real_git}))
        self.state.chmod(0o600)
        proxy = self.bin / "git"
        proxy.write_text(f"#!{sys.executable}\nimport os\nos.execv({sys.executable!r}, "
                         f"[{sys.executable!r}, '-I', '-B', {str(Path(__file__).resolve())!r}, "
                         f"'--git-proxy', {str(self.state)!r}, *os.sys.argv[1:]])\n")
        proxy.chmod(0o700)
        for name in ("gh", "kent"):
            denied = self.bin / name
            denied.write_text("#!/bin/sh\nprintf '%s\n' 'undeclared fixture provider' >&2\nexit 99\n")
            denied.chmod(0o700)
        self.environment = safe_environment(self.home)
        self.environment["PATH"] = str(self.bin) + os.pathsep + os.environ.get("PATH", "")

    def allow_read(self, path):
        state = json.loads(self.state.read_text())
        path = str(Path(path).resolve(strict=True))
        if path not in state["read_only"]:
            state["read_only"].append(path)
        self.state.write_bytes(canonical(state))

    @contextmanager
    def temporary_directory(self, **_kwargs):
        if self.closed:
            raise AssertionError("fixture sandbox has ended")
        with tempfile.TemporaryDirectory(dir=self.root, prefix="case-") as directory:
            path = Path(directory).resolve()
            state = json.loads(self.state.read_text())
            state["containers"].append(str(path))
            self.state.write_bytes(canonical(state))
            try:
                yield str(path)
            finally:
                state = json.loads(self.state.read_text())
                state["containers"].remove(str(path))
                state["repositories"] = [p for p in state["repositories"]
                                         if not Path(p).is_relative_to(path)]
                self.state.write_bytes(canonical(state))

    def run_child(self, argv, *, cwd=None, env=None, **kwargs):
        environment = dict(self.environment)
        if env:
            environment.update({key: value for key, value in env.items()
                                if not key.startswith("GIT_") and key not in {"HOME", "PYTHONPATH", "PATH"}})
            # Scenario PATH may prepend only executables in this owned sandbox.
            requested_path = env.get("PATH", environment["PATH"])
            original_entries = set(self.environment["PATH"].split(os.pathsep))
            for entry in requested_path.split(os.pathsep):
                if entry not in original_entries and not Path(entry).resolve().is_relative_to(self.root):
                    raise AssertionError("child executable PATH escaped fixture ownership")
            environment["PATH"] = str(self.bin) + os.pathsep + requested_path
            if "GIT_INDEX_FILE" in env:
                index_path = Path(env["GIT_INDEX_FILE"]).resolve()
                state = json.loads(self.state.read_text())
                if not any(index_path.is_relative_to(Path(p)) for p in state["containers"]):
                    raise AssertionError("scratch index is not sandbox-owned")
                environment["PUBER_FIXTURE_INDEX"] = str(index_path)
        return subprocess.run(argv, cwd=cwd, env=environment, **kwargs)

    def git_read(self, root, *args):
        self.allow_read(root)
        return self.git_owned(root, *args)

    def git_owned(self, root, *args):
        result = self.run_child(["git", "-C", str(root), *args], cwd=root,
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        if result.returncode:
            raise AssertionError(f"fixture Git rejected: {args}: {result.stderr}")
        return result.stdout.strip()


def _git_proxy(state_path, arguments):
    state = json.loads(Path(state_path).read_text())
    root = Path(state["root"]).resolve()
    containers = [Path(p) for p in state["containers"]]
    repositories = {Path(p) for p in state["repositories"]}
    read_only = [Path(p) for p in state["read_only"]]
    cwd = Path.cwd().resolve()
    args = list(arguments)
    explicit_git = None
    index = 0
    while index < len(args):
        argument = args[index]
        if argument == "-C":
            cwd = (cwd / args[index + 1]).resolve()
            index += 2
        elif argument in {"--git-dir", "--work-tree"}:
            value = (cwd / args[index + 1]).resolve()
            if argument == "--git-dir":
                explicit_git = value
            else:
                cwd = value
            index += 2
        elif argument.startswith("--git-dir="):
            explicit_git = (cwd / argument.split("=", 1)[1]).resolve()
            index += 1
        elif argument == "-c":
            # Test callers cannot override the owner's hooks/config/transport.
            if not (args[index + 1].startswith(("user.name=", "user.email="))
                    or args[index + 1] in {"core.fsmonitor=false", "core.hooksPath=" + os.devnull, "maintenance.auto=false", "gc.auto=0"}):
                raise AssertionError("unowned Git configuration override")
            index += 2
        elif argument in {"--no-optional-locks", "--no-replace-objects", "--no-lazy-fetch", "--literal-pathspecs"}:
            index += 1
        elif argument.startswith("-"):
            raise AssertionError("unrecognized fixture Git global option: " + argument)
        else:
            break
    if index == len(args):
        raise AssertionError("fixture Git command is absent")
    command, tail = args[index], args[index + 1:]
    if any(value.startswith(("--output", "--exec", "--upload-pack", "--receive-pack",
                             "--git-dir", "--work-tree", "--separate-git-dir", "--template")) for value in tail):
        raise AssertionError("fixture Git option can escape repository boundary")
    reading = command in {"rev-parse", "show", "ls-tree", "ls-files", "status", "diff", "log",
                          "rev-list", "merge-base", "cat-file", "for-each-ref", "show-ref", "check-ref-format", "check-ignore", "ls-remote"}
    reading = reading or (command == "symbolic-ref" and len([value for value in tail if not value.startswith("-")]) == 1)
    reading = reading or (command == "config" and any(value in tail for value in
        ("--get", "--get-all", "--get-regexp", "--list", "-l")))

    def owned(path):
        canonical_path = path.resolve()
        return (canonical_path != root and any(canonical_path.is_relative_to(p) for p in containers)
                and not any(canonical_path.is_relative_to(p) for p in read_only))

    def real(*command_args, check=True):
        env = safe_environment(root / "home")
        scratch_index = os.environ.get("PUBER_FIXTURE_INDEX")
        if scratch_index:
            index_path = Path(scratch_index).resolve()
            if not owned(index_path):
                raise AssertionError("scratch index escaped fixture ownership")
            env["GIT_INDEX_FILE"] = str(index_path)
        result = subprocess.run([state["git"], "--no-replace-objects", "-c", "core.fsmonitor=false",
                                 "-c", f"core.hooksPath={os.devnull}", "-c", "gc.auto=0",
                                 "-c", "protocol.allow=never", "-c", "protocol.file.allow=always",
                                 *command_args], cwd=cwd, env=env, stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE)
        if check and result.returncode:
            raise AssertionError(result.stderr.decode("utf-8", "replace"))
        return result

    def repository(path):
        if path in repositories:
            return path
        raise AssertionError("Git mutation target is not a registered fixture repository")

    def register(path):
        path = path.resolve()
        if not owned(path) or (path / ".git").is_symlink():
            raise AssertionError("Git destination is outside the owned fixture")
        repositories.add(path)
        state["repositories"] = sorted(str(p) for p in repositories)
        Path(state_path).write_bytes(canonical(state))

    if command in {"init", "clone"}:
        positional = []
        skip = False
        for value in tail:
            if skip:
                skip = False
                continue
            if value in {"-b", "--initial-branch", "--template", "--object-format"}:
                skip = True
            elif not value.startswith("-"):
                positional.append(value)
        destination = cwd if command == "init" and not positional else (cwd / positional[-1]).resolve()
        if not owned(destination) or (destination / ".git").exists():
            raise AssertionError("fixture initialization requires a new owned destination")
        if command == "clone":
            source = (cwd / tail[-2]).resolve()
            if not any(source == p or source.is_relative_to(p) for p in read_only) and source not in repositories:
                raise AssertionError("fixture clone donor is unqualified")
        register(destination)
    elif command == "ls-remote":
        repository(cwd)
        remote = next((value for value in tail if not value.startswith("-")), "origin")
        location = real("remote", "get-url", remote).stdout.decode().strip()
        if (cwd / location).resolve() not in repositories:
            raise AssertionError("fixture observation transport must be owned/local")
    elif not reading:
        if command not in {"add", "branch", "checkout", "commit", "update-ref", "read-tree", "write-tree",
                           "hash-object", "commit-tree", "merge", "tag", "rm", "reset", "cherry-pick", "update-index",
                           "switch", "worktree", "config", "remote", "push", "fetch", "pull", "rebase"}:
            raise AssertionError("unrecognized fixture Git mutation: " + command)
        if not owned(cwd) and explicit_git is None:
            raise AssertionError("Git write attempted outside fixture ownership")
        if explicit_git is not None:
            repository(explicit_git)
        elif cwd in repositories and (cwd / "HEAD").is_file() and (cwd / "objects").is_dir():
            repository(cwd)
        else:
            top = Path(real("rev-parse", "--show-toplevel").stdout.decode().strip()).resolve()
            repository(top)
            git_dir = Path(real("rev-parse", "--absolute-git-dir").stdout.decode().strip()).resolve()
            common_raw = real("rev-parse", "--git-common-dir").stdout.decode().strip()
            common = (cwd / common_raw).resolve()
            if not owned(git_dir) or not any(common == p / ".git" for p in repositories):
                raise AssertionError("Git/common directory escaped owned repositories")
        if command == "worktree" and tail and tail[0] == "add":
            destination = (cwd / tail[-2]).resolve() if tail[-1] == "HEAD" else (cwd / tail[-1]).resolve()
            register(destination)
        if command == "config" and any(p in {"--global", "--system", "--file", "-f"} or p.startswith(("--file=", "-f")) for p in tail):
            raise AssertionError("fixture configuration must stay repository-local")
        if command == "config":
            keys = [value for value in tail if not value.startswith("-")]
            if not keys or keys[0] not in {"user.name", "user.email", "remote.origin.url", "remote.origin.pushurl"}:
                raise AssertionError("unapproved fixture repository configuration key")
            if keys[0].startswith("remote.") and len(keys) > 1:
                if not owned((cwd / keys[1]).resolve()):
                    raise AssertionError("fixture remote configuration escaped ownership")
        if command == "remote" and tail[0] != "get-url":
            if tail[0] != "add" or (cwd / tail[-1]).resolve() not in repositories:
                raise AssertionError("fixture remote is not an owned local repository")
        if command in {"push", "fetch", "pull"}:
            remote = next((p for p in tail if not p.startswith("-")), "origin")
            location = real("remote", "get-url", remote).stdout.decode().strip()
            if (cwd / location).resolve() not in repositories:
                raise AssertionError("fixture transport is not owned/local")
    elif not owned(cwd) and not any(cwd == p or cwd.is_relative_to(p) for p in read_only):
        raise AssertionError("Git read target is not declared")
    result = real(*args, check=False)
    sys.stdout.buffer.write(result.stdout)
    sys.stderr.buffer.write(result.stderr)
    return result.returncode


@contextmanager
def observe_real_source_inspection(kit, primary, project):
    """Observe one real SDK inspector completion; never change its response."""
    from unittest.mock import patch
    expected = str(Path(kit).resolve(strict=True) / 'scripts/inspect-delivery-source')
    original = subprocess.Popen
    observations = []

    class Observed(original):
        def __init__(self, args, *a, **kw):
            self.matched = (isinstance(args, (list, tuple)) and len(args) > 6
                and args[0] == sys.executable and list(args[1:4]) == ['-I', '-S', '-c']
                and args[5] == expected)
            self.stream = kw.get('stdout')
            self.recorded = False
            if self.matched:
                tail = list(args[6:])
                assert len(tail) % 2 == 0
                selectors = {}
                for key, value in zip(tail[::2], tail[1::2]):
                    assert key not in selectors, 'duplicate inspector selector'
                    selectors[key] = value
                assert selectors['--project-root'] == str(primary)
                assert selectors['--workspace'] == str(project)
                assert selectors['--kit-root'] == str(kit)
            super().__init__(args, *a, **kw)

        def wait(self, *a, **kw):
            rc = super().wait(*a, **kw)
            if self.matched and not self.recorded:
                self.recorded = True
                offset = self.stream.tell()
                try:
                    self.stream.seek(0)
                    raw = self.stream.read(4097)
                finally:
                    self.stream.seek(offset)
                assert len(raw) <= 4096, 'inspector observation exceeds bound'
                parsed = json.loads(raw)
                assert isinstance(parsed, dict)
                assert not observations, 'multiple inspectors in drift invocation'
                observations.append({'rc': rc, 'schema': parsed.get('schema'),
                    'status': parsed.get('status'), 'reason': parsed.get('reason')})
            return rc

    with patch.object(subprocess, 'Popen', Observed):
        yield observations


def require_ship_source_drift(error, observations, chain_types):
    expected_messages = ('native Kit runtime qualification failed',
        'native Script source admission failed', 'published source inspection was not admitted')
    current = error
    for kind, message in zip(chain_types, expected_messages):
        assert type(current) is kind, 'unexpected drift exception cause'
        assert str(current) == message, 'unexpected drift exception message'
        current = current.__cause__
    assert len(chain_types) == 3
    assert len(observations) == 1, 'drift did not observe exactly one real inspector'
    row = observations[0]
    assert set(row) == {'rc', 'schema', 'status', 'reason'}
    assert row['rc'] != 0
    assert row['schema'] == 'delivery-source-inspection-v1'
    assert row['status'] == 'blocked'
    assert row['reason'] == 'runtime_source_differs'


def check_ship_contract_drift(runner, carrier, *, native, helper, stage_error_type,
                              github_state, github_log):
    """Specific local/mock oracle and canonical real-admission oracle."""
    contract = runner.work / '.kent/commands/ship-pr.md'
    original_bytes = contract.read_bytes()

    def effects():
        remote = subprocess.run(['git', '--git-dir', str(runner.bare), 'rev-parse',
            'refs/heads/release/99.0.0'], cwd=runner.work, env=runner.env,
            check=True, capture_output=True).stdout
        return (tuple(runner.stage_log), json.dumps(runner.stage_inputs, sort_keys=True),
                github_state.read_bytes(),
                github_log.read_bytes(), remote)

    runner._bound_source(".kent/commands/ship-pr.md")
    before = effects()
    if native is None:
        contract.write_bytes(original_bytes + b'\ncontract drift\n')
        try:
            try:
                runner.execute('ship_pr', 'compliance_ship_pr', carrier)
            except stage_error_type as error:
                assert str(error) == 'stage contract digest drifted: .kent/commands/ship-pr.md'
            else:
                raise AssertionError('unbound Ship command drift was accepted')
        finally:
            contract.write_bytes(original_bytes)
        assert effects() == before
        runner._admit('ship_pr', 'compliance_ship_pr', carrier)
        return

    def bind():
        helper._reset_runtime_invocation()
        helper._bind_qualified_runtime(runner.work, None)

    bind()
    def attempt(action):
        with observe_real_source_inspection(native.kit, native.primary, runner.work) as rows:
            try:
                action()
            except Exception as error:
                return error, rows
        return None, rows

    def must_not_qualify(error, rows):
        try:
            require_ship_source_drift(error, rows, chain_types)
        except AssertionError:
            return
        raise AssertionError('unrelated failure satisfied Ship drift oracle')

    contract.write_bytes(original_bytes + b'\ncontract drift\n')
    try:
        error, rows = attempt(lambda: runner.execute('ship_pr', 'compliance_ship_pr', carrier))
        # Read classes from the FAILED invocation, without reload/reset/bind.
        checkpoint = helper._QUALIFIED_CHECKPOINT_MODULE
        assert checkpoint is not None
        modules = [entry[0] for entry in checkpoint._KIT_RUNTIME_BOOTSTRAPS.values()
                   if Path(entry[0].__file__).resolve() ==
                   (runner.work / '.kent/scripts/workflow_kit_runtime.py').resolve()]
        assert len(modules) == 1
        operations = sys.modules['workflowkit.operations']
        assert Path(operations.__file__).resolve() == (native.kit / 'workflowkit/operations.py').resolve()
        chain_types = (helper.ContractError, modules[0].KitRuntimeError, operations.PlanValidationError)
        require_ship_source_drift(error, rows, chain_types)
        # Reuse REAL observations only as negative-validator inputs, not authority.
        must_not_qualify(helper.ContractError('runtime facade unavailable'), rows)
        must_not_qualify(TypeError('loader programming error'), rows)
        must_not_qualify(error, [])
        must_not_qualify(error, rows + rows)
        for field, value in (('reason', 'receipt_digest_differs'),
                             ('schema', 'foreign-schema'), ('status', 'admitted'), ('rc', 0)):
            must_not_qualify(error, [{**rows[0], field: value}])

    finally:
        contract.write_bytes(original_bytes)
    assert effects() == before
    bind()
    assert effects() == before

    # Removing the mutation must NOT qualify, even with a healthy native bind.
    error, rows = attempt(bind)
    assert error is None
    must_not_qualify(error, rows)
    assert effects() == before

def infrastructure_path(path, *, kit=False):
    if "__pycache__" in Path(path).parts:
        return False
    if kit and path.startswith(("scripts/", "workflowkit/", "templates/", "agents/", "global/",
                                "prompts/", "skills/", "adapters/", "contracts/")):
        return True
    return path in {"AGENTS.md", ".gitignore", ".kent/workflow-profile.toml", ".kent/project-contract.md"} or path.startswith(
        (".kent/scripts/", ".kent/commands/", ".kent/context/", ".kent/skills/", ".kent/workflows/"))


def inventory(sandbox, root, *, kit=False):
    files = sandbox.git_owned(root, "ls-files", "-z").split("\0")
    result = []
    for relative in files:
        if not relative or not infrastructure_path(relative, kit=kit):
            continue
        path = root / relative
        raw = path.read_bytes()
        result.append({"root": str(root), "relative_path": relative, "sha256": hashlib.sha256(raw).hexdigest(),
                       "byte_size": len(raw), "mode": stat.S_IMODE(path.stat().st_mode)})
    return result


class NativeFixture:
    def __init__(self, sandbox, directory, donor, *, prepared_kit=None):
        self.sandbox = sandbox
        self.directory = Path(directory).resolve()
        if prepared_kit is not None:
            self.kit, self.kit_commit = prepared_kit
            state = json.loads(sandbox.state.read_text())
            assert str(self.kit) in state["repositories"]
            assert sandbox.git_owned(self.kit, "rev-parse", "HEAD") == self.kit_commit
            assert sandbox.git_owned(self.kit, "status", "--porcelain=v1", "--untracked-files=all") == ""
        else:
            self.kit = self.directory / "native-kit"
            sandbox.allow_read(donor)
            sandbox.git_owned(self.directory, "clone", "--shared", "--no-checkout", str(donor), str(self.kit))
            self.kit_commit = sandbox.git_read(donor, "rev-parse", "HEAD")
            sandbox.git_owned(self.kit, "checkout", "--detach", self.kit_commit)
            sandbox.git_owned(self.kit, "update-ref", PUBLICATION_REF, self.kit_commit)
        self.private = self.directory / "native-inputs"
        self.private.mkdir(mode=0o700)
        self.state_path = self.private / "state.json"
        self.graph_path = self.private / "graph.json"
        self.task_path = self.private / "task.json"
        self.status_path = self.private / "worktree-status.json"
        self.native = self.private / "kent"
        self.native.write_text(f"#!{sys.executable}\nimport os\nos.execv({sys.executable!r}, "
                               f"[{sys.executable!r}, '-I', '-B', {str(Path(__file__).resolve())!r}, "
                               f"'--native', {str(self.state_path)!r}, *os.sys.argv[1:]])\n")
        self.native.chmod(0o700)
        (sandbox.bin / "kent").unlink()
        (sandbox.bin / "kent").symlink_to(self.native)
        self.ci_helper = sandbox.bin / "kent-prepare-github-ci"
        self.ci_helper.symlink_to(self.kit / "scripts/prepare-github-ci")
        self.env = {**sandbox.environment, "PUBER_KENT_BIN": str(self.native),
                    "KENT_PREPARE_GITHUB_CI_BIN": str(self.ci_helper)}

    def install_profile(self, project):
        install_fixture_kit(project, self.kit, self.kit_commit)

    def bind_native_fixture(self, primary, project, task, graph):
        self.primary, self.project = primary.resolve(), project.resolve()
        self.graph = {"workflow_id": graph["workflow"]["id"], "expected_version": graph["workflow"]["version"],
                      "graph": {key: json.loads(json.dumps(graph[key])) for key in ("nodes", "transition_groups", "edges")}}
        self.task = task
        self.baseline_commit = task["execution_target"]["commit_oid"]
        self.source_commit = self.baseline_commit
        self.sandbox.git_owned(self.primary, "merge", "--ff-only", self.baseline_commit)
        if ARCHIVE_KIT_BASE in self.sandbox.git_owned(self.kit, "log", "-1", "--format=%P").split():
            # C publishes infrastructure without rewriting native product A.
            # H remains on A; C is deliberately not an ancestor of A/H.
            self.sandbox.git_owned(self.primary, "-c", "user.name=Disposable source fixture",
                                   "-c", "user.email=fixture@example.invalid",
                                   "commit", "--allow-empty", "-qm",
                                   "Disposable infrastructure publication C")
            self.source_commit = self.sandbox.git_owned(self.primary, "rev-parse", "HEAD")
            if self.primary == self.project:
                self.sandbox.git_owned(self.primary, "update-ref", "HEAD",
                                       self.baseline_commit, self.source_commit)
            assert self.source_commit != self.baseline_commit
            assert self.sandbox.git_owned(project, "rev-parse", "HEAD") == self.baseline_commit
            assert self.source_commit not in self.sandbox.git_owned(
                project, "rev-list", "HEAD").splitlines()
        self.sandbox.git_owned(project, "update-ref", PUBLICATION_REF, self.source_commit)
        task["project"] = {"default_workspace_id": WORKSPACE_ID}
        task["source_workspace"] = {"is_primary": True, "workspace_id": WORKSPACE_ID, "root_path": str(primary)}
        project_roots = list(dict.fromkeys([primary, project]))
        receipt = {"schema": "delivery-source-publication-v1", "project_id": PROJECT_ID,
                   "workflow_id": self.graph["workflow_id"], "source_commit": self.source_commit,
                   "kit_commit": self.kit_commit, "runtime_roots": [str(path) for path in [*project_roots, self.kit]],
                   "files": [item for path in project_roots for item in inventory(self.sandbox, path)]
                            + inventory(self.sandbox, self.kit, kit=True),
                   "policy": "published_source_v1", "local_inputs": []}
        if self.source_commit != self.baseline_commit:
            receipt.update(schema="delivery-source-publication-v2",
                           policy="published_source_project_content_v1")
        self.receipt_path = self.private / "source-receipt.json"
        raw = canonical(receipt)
        self.receipt_path.write_bytes(raw)
        self.receipt_path.chmod(0o600)
        nodes = {node["key"]: node for node in self.graph["graph"]["nodes"]}
        ingress = next(edge for edge in self.graph["graph"]["edges"]
                       if edge["target_node_id"] == nodes["release_intent_gate"]["id"])
        admission = {"schema": "delivery-source-admission-v2", "receipt_ref": {
            "path": str(self.receipt_path), "sha256": hashlib.sha256(raw).hexdigest()},
            "primary_workspace_id": WORKSPACE_ID, "primary_root": str(primary), "kit_root": str(self.kit),
            "source_ref": PUBLICATION_REF, "kit_ref": PUBLICATION_REF, "ci_helper": str(self.ci_helper),
            "helper_bindings": [], "binding": {"kind": "plan-publication"},
            "ingress": {"kind": "script-edge", "edge_id": ingress["id"]}}
        self.frame_path = self.private / "admission.json"
        frame = canonical({"project_id": PROJECT_ID, "workflow_id": self.graph["workflow_id"],
                           "source_admission": admission})
        self.frame_path.write_bytes(frame)
        self.frame_path.chmod(0o600)
        next(group for group in self.graph["graph"]["transition_groups"]
             if group["id"] == ingress["transition_group_id"])["description"] = canonical({
                 "schema": "delivery-script-ingress-ref-v1", "path": str(self.frame_path),
                 "sha256": hashlib.sha256(frame).hexdigest()}).decode()
        self.graph_path.write_bytes(canonical(self.graph))
        self.comments = self.private / "comments.json"
        self.comments.write_text("[]")
        self.state = {"primary": str(primary), "project": str(project), "project_id": PROJECT_ID,
                      "workspace_id": WORKSPACE_ID, "workflow_id": self.graph["workflow_id"],
                      "task": str(self.task_path), "graph": str(self.graph_path), "comments": str(self.comments),
                      "sessions": [], "left": [], "status": str(self.status_path)}
        self.sync()

    def refresh(self):
        self.state.update(json.loads(self.state_path.read_text()))
        self.task.update(json.loads(self.task_path.read_text()))

    def restore_owner_attachment(self, owner):
        self.refresh()
        assert any(row["session_id"] == owner for row in self.state["sessions"])
        self.state["left"] = [value for value in self.state["left"] if value != owner]
        self.sync()

    def sync(self):
        self.task_path.write_bytes(canonical(self.task))
        self.state_path.write_bytes(canonical(self.state))
        owner = next((row.get("session_id") for row in self.task["current_nodes"]
                      if row.get("session_id")), None)
        self.status_path.write_bytes(canonical(native_worktree_status(self.state, owner, camel=True)))

    def enter_script(self, key):
        self.refresh()
        self.env.pop("KENT_SESSION_ID", None)
        self.env.pop("KENT_STEP_ID", None)
        node = next(row for row in self.graph["graph"]["nodes"] if row["key"] == key)
        assert node["kind"] == "script"
        self.task.update(current_nodes=[{"node_id": node["id"]}], current_scripts=[{
            "path": str(self.project / node["script_path"]), "current_node": {"node_id": node["id"]}}],
            status={"node_ids": [node["id"]]}, live_sessions=[])
        self.sync()
        return {"task_id": self.task["summary"]["id"], "node_id": node["id"]}

    def enter_agent(self, key, *, session_id=None, history=5):
        self.refresh()
        node = next(row for row in self.graph["graph"]["nodes"] if row["key"] == key)
        assert node["kind"] != "script"
        owner = session_id or str(uuid.uuid5(uuid.NAMESPACE_URL, "puber-native:" + key))
        sessions = [{"id": owner, "session_id": owner, "name": key, "state": "idle", "archived": False,
                     "status": "idle", "root": str(self.project)}]
        sessions += [{"id": str(uuid.uuid5(uuid.NAMESPACE_URL, f"puber-idle:{i}")),
                      "session_id": str(uuid.uuid5(uuid.NAMESPACE_URL, f"puber-idle:{i}")),
                      "name": "History", "state": "idle", "status": "idle", "archived": False,
                      "root": str(self.project)} for i in range(history)]
        self.state["sessions"] = sessions
        self.task.update(current_nodes=[{"node_id": node["id"], "session_id": owner,
                                         "effective_assignee": "delivery-operator"}], current_scripts=[],
                         live_sessions=[{"session_id": owner, "session_name": key}],
                         retained_session_count=len(sessions), status={"node_ids": [node["id"]]})
        self.env.update(KENT_SESSION_ID=owner, KENT_STEP_ID=str(uuid.uuid5(uuid.NAMESPACE_URL, "step:" + key)))
        self.sync()
        return owner


def native_worktree_status(state, owner, *, camel=False):
    left = owner in state["left"]
    effective = state["primary"] if left else state["project"]
    worktree = None if left else {"id": "fixture-linked", "name": "H",
        "root": state["project"], "availability": "available"}
    roots = {"recordedRoot": effective, "observedRoot": effective}
    snake_roots = {"recorded_root": effective, "observed_root": effective}
    if not left:
        branch = subprocess.run(
            ["git", "-C", state["project"], "symbolic-ref", "HEAD"],
            check=True, text=True, capture_output=True,
        ).stdout.strip()
        roots.update(displayName=worktree["name"], recordedBranchRef=branch)
        snake_roots.update(display_name=worktree["name"], recorded_branch_ref=branch)
    if camel:
        return {"target": {"workspaceId": state["workspace_id"], "workspaceName": "Puber",
            "workspaceRoot": state["primary"], "workspaceAvailability": "available",
            "worktree": worktree, "cwdRelpath": ".", "effectiveWorkdir": effective},
            "worktree": roots, "problems": None}
    return {"target": {"workspace_id": state["workspace_id"], "workspace_root": state["primary"],
        "workspace_availability": "available", "worktree": worktree,
        "cwd_relpath": ".", "effective_workdir": effective},
        "worktree": snake_roots, "problems": []}


def native_dispatch(state_path, args):
    state = json.loads(Path(state_path).read_text())
    task = json.loads(Path(state["task"]).read_text())
    project_id = state["project_id"]
    def emit(value):
        print(json.dumps(value))
        return 0
    if args == ["project", "list"]:
        print(f"{project_id}\tPuber\t{state['primary']}")
        return 0
    if args == ["project", state["project"]] or args == ["project", state["primary"]]:
        print(project_id)
        return 0
    if args == ["project", "workspaces", "--project", project_id, "--offset", "0", "--limit", "100", "--json"]:
        return emit({"project_id": project_id, "offset": 0, "workspaces": [{
            "workspace_id": state["workspace_id"], "root_path": state["primary"], "is_default": True}]})
    if args in (["task", "show", task["summary"]["id"], "--project", project_id, "--json"],
                ["task", "show", task["summary"]["short_id"], "--project", project_id, "--json"]):
        return emit(task)
    if args == ["workflow", "graph", "inspect", state["workflow_id"], "--json"]:
        return emit(json.loads(Path(state["graph"]).read_text()))
    if args == ["task", "list", "--project", project_id, "--sort", "short-id:asc", "--limit", "100", "--offset", "0", "--json"]:
        return emit({"scope": {"project_id": project_id}, "tasks": [{
            "task_id": task["summary"]["id"], "short_id": task["summary"]["short_id"], "status": {"kind": "active"}}]})
    # The worktree adapter and native selector consume the same owner state.
    adapter = bool(args and args[0] in {"status", "leave", "delete"})
    operation = args[0] if adapter else args[1] if args[:1] == ["worktree"] and len(args) > 1 else None
    normalized = args if adapter else args[1:]
    if operation in {"status", "leave", "delete"}:
        expected_tail = ["--json"] if operation == "status" else []
        if len(normalized) != 3 + len(expected_tail) or normalized[1] != "--session" or normalized[3:] != expected_tail:
            return 2
        owner = normalized[2]
        if not any(row["session_id"] == owner for row in state["sessions"]):
            return 2
        if operation == "delete":
            print("native fixture admission blocks active foreign work", file=sys.stderr)
            return 1
        if operation == "leave":
            current = [row.get("session_id") for row in task["current_nodes"]]
            if owner not in current:
                return 2
            if owner not in state["left"]:
                state["left"].append(owner)
            Path(state_path).write_bytes(canonical(state))
            Path(state["status"]).write_bytes(canonical(native_worktree_status(state, owner, camel=True)))
            return emit({})
        return emit(native_worktree_status(state, owner, camel=adapter))
    if args[:2] == ["task", "sessions"] and args[2] == task["summary"]["id"]:
        return emit({"task_id": task["summary"]["id"], "items": state["sessions"], "total_count": len(state["sessions"])})
    if args[:3] == ["task", "comment", "add"] and args[3] == task["summary"]["id"]:
        if args[4:6] != ["--project", project_id] or args[6] != "--body":
            return 2
        owners = [row.get("session_id") for row in task["current_nodes"]]
        if args[8:] != ["--author", "agent", "--author-id", owners[0]]:
            return 2
        comments = json.loads(Path(state["comments"]).read_text())
        comments.append(args[7])
        Path(state["comments"]).write_text(json.dumps(comments))
        return emit({})
    if args[:2] == ["task", "search"]:
        comments = json.loads(Path(state["comments"]).read_text())
        text = args[2]
        hits = [{"literal": {"after": "", "before": "", "left_truncated": False,
                             "match": text, "right_truncated": False}, "ordinal": 0,
                 "source": {"kind": "comment", "comment_id": "fixture-retention"}}
                for comment in comments if comment == text]
        return emit({"groups": [] if not hits else [{"hits": hits, "project_id": project_id,
                     "project_key": "PUB", "short_id": task["summary"]["short_id"], "status": "active",
                     "task_id": task["summary"]["id"], "title": "fixture", "total_hit_count": len(hits),
                     "workflow_id": state["workflow_id"]}], "mode": "literal"})
    return 2


def test_sandbox_boundaries():
    """Negative probes use foreign disposable sentinels, never real source roots."""
    with FixtureSandbox.create() as foreign, FixtureSandbox.create() as sandbox:
        with foreign.temporary_directory() as victim_dir, sandbox.temporary_directory() as own_dir:
            victim, own = Path(victim_dir), Path(own_dir)
            for owner, repository in ((foreign, victim), (sandbox, own)):
                owner.git_owned(repository, "init", "-q")
                owner.git_owned(repository, "config", "user.email", "fixture@example.invalid")
                owner.git_owned(repository, "config", "user.name", "Fixture")
                (repository / "sentinel").write_text("untouched")
                owner.git_owned(repository, "add", ".")
                owner.git_owned(repository, "commit", "-qm", "Initial sentinel")
            before = ((victim / "sentinel").read_bytes(), (victim / ".git/config").read_bytes(),
                      foreign.git_owned(victim, "show-ref"), (victim / ".git/index").read_bytes())
            hook = own / ".git/hooks/pre-commit"
            hook.write_text("#!/bin/sh\nprintf corrupted > " + str(victim / "sentinel") + "\n")
            hook.chmod(0o700)
            poison = {**sandbox.environment, "GIT_DIR": str(victim / ".git"),
                "GIT_WORK_TREE": str(victim), "GIT_CONFIG_COUNT": "1",
                "GIT_CONFIG_KEY_0": "core.hooksPath", "GIT_CONFIG_VALUE_0": str(victim),
                "HOME": str(victim)}
            result = sandbox.run_child(["git", "-C", str(own), "rev-parse", "--show-toplevel"],
                env=poison, capture_output=True, text=True)
            assert result.returncode == 0 and result.stdout.strip() == str(own), result.stderr
            sandbox.git_owned(own, "commit", "--allow-empty", "-qm", "Hooks stay disabled")
            for args in (
                ["git", "-C", str(victim), "update-ref", "refs/heads/evil", "HEAD"],
                ["git", "-C", str(own), "config", "--file=" + str(victim / "config"), "user.name", "attack"],
                ["git", "-C", str(own), "diff", "--output=" + str(victim / "sentinel")],
                ["git", "-C", str(own), "remote", "add", "foreign", str(victim)],
                ["git", "-C", str(own), "push", str(victim), "HEAD:refs/heads/evil"],
            ):
                result = sandbox.run_child(args, capture_output=True, text=True)
                assert result.returncode != 0, (args, result.stdout)
            try:
                sandbox.run_child(["git", "status"], cwd=own,
                    env={**poison, "GIT_INDEX_FILE": str(victim / ".git/index")})
            except AssertionError:
                pass
            else:
                raise AssertionError("foreign scratch index accepted")
            result = sandbox.run_child([sys.executable, "-I", "-B", "-c",
                "import os,subprocess; assert 'GIT_DIR' not in os.environ; "
                "assert os.environ['HOME']==os.sys.argv[1]; "
                "assert subprocess.check_output(['git','rev-parse','--show-toplevel'],text=True).strip()==os.getcwd()",
                str(sandbox.home)], cwd=own, env=poison, capture_output=True, text=True)
            assert result.returncode == 0, result.stderr
            # Registered worktree name alone is insufficient: its common Git
            # directory must also remain inside this owner's repository set.
            (own / ".git").rename(own / "saved-git")
            (own / ".git").symlink_to(victim / ".git", target_is_directory=True)
            result = sandbox.run_child(["git", "-C", str(own), "update-ref", "refs/heads/escaped", "HEAD"],
                capture_output=True, text=True)
            assert result.returncode != 0
            after = ((victim / "sentinel").read_bytes(), (victim / ".git/config").read_bytes(),
                     foreign.git_owned(victim, "show-ref"), (victim / ".git/index").read_bytes())
            assert before == after, "foreign disposable files/refs/config/index changed"
    print("sandbox routing/HOME/config/index/hooks/common-dir/child/foreign no-effects: passed")


if __name__ == "__main__":
    try:
        if sys.argv[1:] == ["--test-boundaries"]:
            test_sandbox_boundaries()
            raise SystemExit(0)
        if sys.argv[1] == "--git-proxy":
            raise SystemExit(_git_proxy(sys.argv[2], sys.argv[3:]))
        if sys.argv[1] == "--native":
            raise SystemExit(native_dispatch(sys.argv[2], sys.argv[3:]))
        raise AssertionError("unknown fixture helper entrypoint")
    except (AssertionError, OSError, ValueError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(2)
