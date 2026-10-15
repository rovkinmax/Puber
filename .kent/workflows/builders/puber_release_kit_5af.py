#!/usr/bin/env python3
"""Build the private, non-attested Puber Release Script-route proposal."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import runpy
import stat
import sys
import tomllib
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[3]
BASE_BUILDER = ROOT / ".kent/workflows/builders/puber_release.py"
BASE_SPEC = ROOT / ".kent/workflows/specs/puber-release.toml"
BASE_GRAPH = ROOT / ".kent/workflows/puber-release.json"
BASE_MANIFEST = ROOT / ".kent/workflows/puber-release.manifest.json"
BASE_ENGINEERING_SPEC = ROOT / ".kent/workflows/specs/puber-engineering-delivery-v24.json"
ENGINEERING_SPEC = ROOT / ".kent/workflows/specs/puber-engineering-delivery-v25.json"
SPEC = ROOT / ".kent/workflows/specs/puber-release-kit-5af.toml"
GRAPH = ROOT / ".kent/workflows/puber-release-kit-5af.json"
MANIFEST = ROOT / ".kent/workflows/puber-release-kit-5af.manifest.json"
PROFILE = ROOT / ".kent/workflow-profile.toml"
WORKFLOW_ID = "10d8adb2-c74c-4ef0-8b5c-311cb5cd0459"
ROUTED_TRANSITIONS = (
    "prepare_profile_generation",
    "ship_pr_ci_watch",
    "ci_waiting_pr",
    "monitor_release",
    "publish_needs_user_action",
    "monitor_needs_user_action",
    "waiting_pr_merge_watch",
    "release_intent_blocked",
    "release_intent_needs_user_action",
    "release_intent_invalid",
    "ci_watch_pr_merged",
    "ci_watch_needs_user_action",
    "merge_watch_still_waiting",
    "merge_watch_pr_merged",
    "merge_watch_needs_user_action",
    "profile_generation_needs_user_action",
    "ci_prepare_ready_initial",
    "ci_prepare_ready_retry",
    "ci_prepare_needs_user_action",
    "ci_prepare_already_merged",
    "waiting_pr_ci_recheck",
)
ROUTE_PARAMETER = {
    "key": "entry_transition",
    "description": (
        "Business route hint; the Script validates it against the exact "
        "incoming graph edge. It is not native identity or approval."
    ),
    "purpose": "ordinary",
}
BINARY_SOURCE_ROOTS = {"app/debug.jks"}
DYNAMIC_RELEASE_PATHS = {"app/build.gradle.kts"}
CLOSED_PATHS = (
    ".kent/subagents/implementation-worker.md",
    ".kent/subagents/fix-worker.md",
    ".kent/commands/feature-implement.md",
    ".kent/commands/cleanup-task.md",
    ".kent/scripts/workflow-task-janitor",
    ".kent/adapters/mobile/android-apk-install-preserve",
    ".kent/adapters/mobile/emulator-resource-lock.sh",
    ".kent/scripts/workflow-puber-release-intent",
    ".kent/scripts/workflow-puber-release-ci",
    ".kent/scripts/workflow-puber-release-profile-generation",
    ".kent/scripts/workflow-release-cleanup",
    ".kent/commands/release.md",
    ".kent/workflow-profile.toml",
    ".kent/project-contract.md",
    ".kent/workflows/README.md",
    ".kent/workflows/specs/puber-engineering-delivery-v25.json",
    ".kent/workflows/specs/puber-release-kit-5af.toml",
    ".kent/workflows/builders/puber_release_kit_5af.py",
    ".kent/workflows/puber-release-kit-5af.json",
    ".kent/workflows/puber-release-kit-5af.manifest.json",
    ".kent/scripts/tests/test-puber-kit-5af-cohort",
    ".kent/scripts/tests/test-delivery-continuity",
    ".kent/scripts/tests/test-runtime-v2-command-closure",
    ".kent/scripts/tests/test-puber-release-graph",
    ".kent/scripts/tests/test-puber-release-session-roles",
    ".kent/scripts/tests/test-workflow-puber-release-intent",
    ".kent/scripts/tests/test-workflow-puber-release-profile-generation",
    ".kent/scripts/tests/test-workflow-puber-release-publish",
    ".kent/scripts/tests/test-workflow-release-cleanup",
    ".kent/adapters/mobile/puber-script-resource-lock",
    ".kent/scripts/tests/test-puber-script-resource-lock",
    ".kent/scripts/tests/test-workflow-wait-github-release",
    ".kent/scripts/tests/test-workflow-task-janitor-native",
    ".kent/scripts/tests/test-writer-preservation-authority",
    "tools/test-smoke-lease-procedure",
    ".kent/context/delivery.md",
)
CLEANUP_EDGES = frozenset({
    "prepare_pr_no_pr", "ci_prepare_merged", "ci_watch_merged",
    "ci_monitor_merged", "fix_pr_merged_cleanup", "waiting_pr_cleanup",
    "merge_watch_cleanup", "waiting_pr_close_without_merge",
    "task_janitor_blocked",
})
LEGACY_CLEANUP_CONTEXT = """Read `.kent/context/delivery.md` first and stay inside its required and
conditionally triggered sources.

Before transition, pipe one non-empty JSON object to
`.kent/scripts/workflow-evidence-ledger append --task {{.TaskShortId}} --workspace
<workspace>`. Set `node_key` to `cleanup`, `evidence_type` to
`delivery`, and `context.manifest_path` to `.kent/context/delivery.md`.
`context.files_read` lists other project instruction files in actual read
order; do not repeat the manifest there. Record repeated questions and
verification loops, use null for unavailable model/compaction counters, and
exclude secrets or broad raw evidence. Append is idempotent for the current
Kent run; on recovery, reuse the returned sequence/hash and continue."""


def cleanup_context() -> str:
    runtime = runpy.run_path(str(ROOT / ".kent/scripts/workflow-puber-release-intent"))
    modules = runtime["_kit_modules"]()
    from workflowkit.delivery import cleanup_without_preparation_instruction

    profile = modules["ProjectProfile"].from_toml(
        ROOT, PROFILE.read_text(encoding="utf-8"), check_files=False,
    )
    return cleanup_without_preparation_instruction(
        profile, ".kent/context/delivery.md", "delivery",
    )


def with_cleanup_context(edge: dict[str, Any], instruction: str) -> dict[str, Any]:
    result = copy.deepcopy(edge)
    if edge["key"] in CLEANUP_EDGES:
        if edge.get("target") != "cleanup" or edge["prompt"].count(LEGACY_CLEANUP_CONTEXT) != 1:
            raise ValueError(f"historical Cleanup context drifted: {edge['key']}")
        result["prompt"] = edge["prompt"].replace(LEGACY_CLEANUP_CONTEXT, instruction)
    return result


REPLAN_PARAMETERS = [
    {
        "key": "workspace_path",
        "description": "Repository or managed-worktree root; never an artifact directory.",
        "purpose": "ordinary",
    },
    {
        "key": "plan_path",
        "description": "Path to the accepted implementation plan, or the literal not-applicable.",
        "purpose": "ordinary",
    },
    {
        "key": "work_kind",
        "description": "Stable project-profile work kind selected during Plan.",
        "purpose": "ordinary",
    },
    {
        "key": "plan_route",
        "description": "Post-review route: start, continue, verify, or fix_continue.",
        "purpose": "ordinary",
    },
    {
        "key": "plan_route_context",
        "description": "Route-specific context; use not-applicable unless continuing a Fix bundle.",
        "purpose": "ordinary",
    },
    {
        "key": "plan_change_report",
        "description": "Evidence-bound summary of the substantive accepted-plan discrepancy.",
        "purpose": "ordinary",
    },
    {
        "key": "review_context",
        "description": "Current bounded writer evidence and unresolved work.",
        "purpose": "ordinary",
    },
    {
        "key": "task_short_id",
        "description": "Stable human-readable Kent task short ID.",
        "purpose": "ordinary",
    },
    {
        "key": "delivery_context",
        "description": "Closed workflow-delivery-context-v1 JSON string with only produced PR/CI packets.",
        "purpose": "ordinary",
    },
]


def _replan_prompt(node_key: str) -> str:
    return (
        f"Revalidate the accepted plan after the {node_key} writer found a "
        "substantive plan discrepancy, before any further production edits.\n\n"
        "Read `.kent/context/plan.md` first and stay inside its required and "
        "conditionally triggered sources.\n\n"
        "Before transition, append the required non-empty evidence event for "
        "`plan_revalidation` through `.kent/scripts/workflow-evidence-ledger`.\n\n"
        "Delivery context (preserve the exact JSON string): "
        "{{.Params.delivery_context}}\n"
        "Workspace: {{.Params.workspace_path}}\n"
        "Accepted plan: {{.Params.plan_path}}\n"
        "Work kind: {{.Params.work_kind}}\n"
        "Substantive plan discrepancy:\n{{.Params.plan_change_report}}\n"
        "Writer evidence:\n{{.Params.review_context}}\n\n"
        "Reconcile only the current accepted Task scope. Continue through "
        "the existing independent Plan Review and Plan Contract gates before "
        "any writer resumes."
    )


def resolve_replan_parameters(
    base: dict[str, Any],
    requested: list[dict[str, Any]] | None = None,
) -> list[dict[str, Any]]:
    requested_parameters = REPLAN_PARAMETERS if requested is None else requested
    requested_by_identity: dict[tuple[str, str], dict[str, Any]] = {}
    for parameter in requested_parameters:
        if not isinstance(parameter, dict):
            raise ValueError("requested Engineering parameter is not an object")
        key = parameter.get("key")
        purpose = parameter.get("purpose", "ordinary")
        if (
            not isinstance(key, str)
            or not key
            or not isinstance(purpose, str)
            or not purpose
        ):
            raise ValueError("requested Engineering parameter has no identity")
        identity = (key, purpose)
        if identity in requested_by_identity:
            raise ValueError(f"requested Engineering parameter repeats: {identity}")
        requested_by_identity[identity] = parameter

    shared_definitions: dict[tuple[str, str], dict[str, Any]] = {}
    for edge in base.get("edges", []):
        if not isinstance(edge, dict):
            raise ValueError("historical Engineering edge is not an object")
        parameters = edge.get("parameters", [])
        if not isinstance(parameters, list):
            raise ValueError("historical Engineering edge parameters are not a list")
        for parameter in parameters:
            if not isinstance(parameter, dict):
                raise ValueError("historical Engineering parameter is not an object")
            identity = (
                parameter.get("key"),
                parameter.get("purpose", "ordinary"),
            )
            if identity not in requested_by_identity:
                continue
            prior = shared_definitions.get(identity)
            if prior is not None and prior != parameter:
                raise ValueError(
                    "historical shared parameter definitions disagree: "
                    f"{identity}"
                )
            shared_definitions[identity] = parameter

    return [
        copy.deepcopy(
            shared_definitions.get(identity, requested_parameter)
        )
        for identity, requested_parameter in requested_by_identity.items()
    ]


def build_engineering_spec() -> dict[str, Any]:
    base = json.loads(BASE_ENGINEERING_SPEC.read_text(encoding="utf-8"))
    if (
        base.get("name") != "Puber Engineering Delivery v24"
        or len(base.get("nodes", [])) != 31
        or len(base.get("edges", [])) != 90
        or len({edge.get("key") for edge in base.get("edges", [])}) != 90
    ):
        raise ValueError("historical Engineering v24 source is not the reviewed base")
    replan_parameters = resolve_replan_parameters(base)
    value = copy.deepcopy(base)
    value["name"] = "Puber Engineering Delivery v25"
    instruction = cleanup_context()
    if not CLEANUP_EDGES <= {edge["key"] for edge in base["edges"]}:
        raise ValueError("historical Cleanup edge set drifted")
    value["edges"] = [with_cleanup_context(edge, instruction) for edge in base["edges"]]
    for key, source, transition, node_key in (
        ("implement_replan", "implement", "implement_replan", "Implement"),
        ("fix_replan", "fix", "fix_replan", "Fix"),
    ):
        value["edges"].append({
            "key": key,
            "source": source,
            "transition": transition,
            "target": "plan_revalidation",
            "context": "continue_session",
            "context_source": "node:plan",
            "prompt": _replan_prompt(node_key),
            "transition_description": (
                "Return a substantive accepted-plan discrepancy to "
                "Plan Revalidation before any further production edits."
            ),
            "parameters": copy.deepcopy(replan_parameters),
            "assignee_selection": "configured",
            "thinking_selection": "configured",
            "requires_approval": False,
        })
    validate_engineering_spec(value, base)
    return value


def validate_engineering_spec(
    value: dict[str, Any],
    base: dict[str, Any] | None = None,
) -> None:
    if base is None:
        base = json.loads(BASE_ENGINEERING_SPEC.read_text(encoding="utf-8"))
    replan_parameters = resolve_replan_parameters(base)
    if (
        value.get("name") != "Puber Engineering Delivery v25"
        or len(value.get("nodes", [])) != 31
        or len(value.get("edges", [])) != 92
        or len({edge.get("key") for edge in value.get("edges", [])}) != 92
    ):
        raise ValueError("Engineering v25 must preserve 31 nodes and add two routes")
    edges = {edge["key"]: edge for edge in value["edges"]}
    for key, source, transition in (
        ("implement_replan", "implement", "implement_replan"),
        ("fix_replan", "fix", "fix_replan"),
    ):
        edge = edges.get(key, {})
        if (
            edge.get("source") != source
            or edge.get("transition") != transition
            or edge.get("target") != "plan_revalidation"
            or edge.get("context") != "continue_session"
            or edge.get("context_source") != "node:plan"
            or edge.get("parameters") != replan_parameters
            or edge.get("requires_approval") is not False
            or not isinstance(edge.get("prompt"), str)
            or not edge["prompt"].strip()
        ):
            raise ValueError(f"Engineering replan edge is not exact: {key}")
        if "before any further production edits" not in edge["prompt"]:
            raise ValueError(f"Engineering replan prompt lost its writer stop: {key}")
    if base is not None:
        if value["nodes"] != base["nodes"]:
            raise ValueError("Engineering v25 changed historical node identities")
        instruction = cleanup_context()
        expected = [with_cleanup_context(edge, instruction) for edge in base["edges"]]
        if value["edges"][: len(base["edges"])] != expected:
            raise ValueError("Engineering v25 changed fields beyond qualified Cleanup prompts")


def _sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _write_text(path: Path, value: str, mode: int = 0o644) -> None:
    path.write_text(value, encoding="utf-8")
    path.chmod(mode)


def _safe_file(relative: str) -> Path:
    path = ROOT / relative
    try:
        metadata = path.lstat()
    except OSError as error:
        raise ValueError(f"cohort source is missing: {relative}") from error
    if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISREG(metadata.st_mode):
        raise ValueError(f"cohort source is not a regular file: {relative}")
    return path


def _legacy_builder() -> dict[str, Any]:
    return runpy.run_path(str(BASE_BUILDER))


def build_graph() -> dict[str, Any]:
    legacy = _legacy_builder()
    errors = legacy["check_graph"](BASE_GRAPH)
    if errors:
        raise ValueError("historical Release v90 graph is not the expected base: " + "; ".join(errors))
    value = json.loads(BASE_GRAPH.read_text(encoding="utf-8"))
    original = copy.deepcopy(value)
    edge_by_key = {edge["key"]: edge for edge in value["edges"]}
    if len(edge_by_key) != len(value["edges"]):
        raise ValueError("historical graph has duplicate transition keys")
    if set(ROUTED_TRANSITIONS) - set(edge_by_key):
        raise ValueError("one or more approved Script routes are absent")
    nodes = {node["id"]: node for node in value["nodes"]}
    groups = {group["id"]: group for group in value["transition_groups"]}
    for key in ROUTED_TRANSITIONS:
        edge = edge_by_key[key]
        target = nodes.get(edge.get("target_node_id"), {})
        source_group = groups.get(edge.get("transition_group_id"), {})
        source = nodes.get(source_group.get("source_node_id"), {})
        if target.get("kind") != "script" or target.get("key") == "task_janitor":
            raise ValueError(f"route does not target an eligible project Script: {key}")
        if source.get("kind") == "start":
            raise ValueError(f"Start route must remain the explicit no-carrier variant: {key}")
        if any(item.get("key") == "entry_transition" for item in edge.get("parameters", [])):
            raise ValueError(f"base graph unexpectedly contains route carrier: {key}")
        edge["parameters"].append(copy.deepcopy(ROUTE_PARAMETER))

    # Agent producers cannot use the Script output helper. Their own incoming
    # prompts carry the exact route choices they must emit.
    script_targets = {
        edge["transition_group_id"]
        for edge in value["edges"]
        if edge["key"] in ROUTED_TRANSITIONS
        and nodes[edge["target_node_id"]].get("kind") == "script"
        and nodes[groups[edge["transition_group_id"]]["source_node_id"]].get("kind") == "agent"
    }
    producer_nodes = {
        groups[group_id]["source_node_id"]
        for group_id in script_targets
    }
    producer_routes: dict[str, list[str]] = {}
    for key in ROUTED_TRANSITIONS:
        edge = edge_by_key[key]
        source = nodes[groups[edge["transition_group_id"]]["source_node_id"]]
        if source.get("kind") == "agent":
            producer_routes.setdefault(source["id"], []).append(key)
    if len(producer_nodes) != 4 or sorted(map(len, producer_routes.values())) != [1, 1, 1, 2]:
        raise ValueError("the five fixed Agent-to-Script producer routes drifted")
    incoming = {
        node_id: [
            edge for edge in value["edges"]
            if edge.get("target_node_id") == node_id
        ]
        for node_id in producer_nodes
    }
    for node_id, routes in producer_routes.items():
        instruction = (
            "\n\nWhen selecting a Script transition, include the exact "
            "`entry_transition` value from this fixed set: "
            + ", ".join(sorted(routes))
            + ". This is a business route hint only; it is not native "
            "identity or approval."
        )
        for edge in incoming.get(node_id, []):
            prompt = edge.get("prompt_template")
            if not isinstance(prompt, str) or not prompt.strip():
                raise ValueError(
                    f"Agent producer has no incoming prompt for route instructions: {edge.get('key')}"
                )
            if "entry_transition` value from this fixed set" not in prompt:
                edge["prompt_template"] = prompt.rstrip() + instruction

    value["workflow"]["version"] = 95
    value["workflow"]["source_revision"] = 91
    value = legacy["normalized_export"](value)
    validate_graph(value, original)
    return value


def validate_graph(value: dict[str, Any], original: dict[str, Any] | None = None) -> None:
    workflow = value.get("workflow")
    if not isinstance(workflow, dict) or workflow.get("id") != WORKFLOW_ID:
        raise ValueError("cohort Workflow UUID drifted")
    if workflow.get("version") != 95 or workflow.get("source_revision") != 91:
        raise ValueError("cohort source identity must propose native 95/source 91")
    if (
        len(value.get("nodes", [])),
        len(value.get("transition_groups", [])),
        len(value.get("edges", [])),
    ) != (18, 51, 51):
        raise ValueError("cohort must preserve 18 nodes, 51 groups, and 51 edges")
    edges = {edge.get("key"): edge for edge in value["edges"]}
    if len(edges) != 51 or set(ROUTED_TRANSITIONS) - set(edges):
        raise ValueError("cohort route set is incomplete or duplicated")
    nodes = {node["id"]: node for node in value["nodes"]}
    groups = {group["id"]: group for group in value["transition_groups"]}
    expected_routes = {
        edge["key"]
        for edge in value["edges"]
        if nodes[edge["target_node_id"]].get("kind") == "script"
        and nodes[edge["target_node_id"]].get("key") != "task_janitor"
        and nodes[
            groups[edge["transition_group_id"]]["source_node_id"]
        ].get("kind") != "start"
    }
    if expected_routes != set(ROUTED_TRANSITIONS):
        raise ValueError("route carrier set does not cover every eligible Script entrance")
    initial = edges["start_release_intent_gate"]
    if "entry_transition" in [
        item.get("key") for item in initial.get("parameters", [])
    ]:
        raise ValueError("Start -> release-intent must remain the no-carrier variant")
    janitor_id = next(
        node["id"] for node in value["nodes"] if node.get("key") == "task_janitor"
    )
    if any(
        edge.get("target_node_id") == janitor_id
        and "entry_transition" in [
            item.get("key") for item in edge.get("parameters", [])
        ]
        for edge in value["edges"]
    ):
        raise ValueError("generic Janitor must not receive the project route carrier")
    for key, edge in edges.items():
        params = [item.get("key") for item in edge.get("parameters", [])]
        if key in ROUTED_TRANSITIONS:
            if not params or params[-1] != "entry_transition" or params.count("entry_transition") != 1:
                raise ValueError(f"route carrier is not the final exact parameter: {key}")
        elif "entry_transition" in params:
            raise ValueError(f"route carrier escaped the reviewed 21-edge set: {key}")
    for key in ("merge_watch_pr_merged", "publish_needs_user_action"):
        if edges[key].get("requires_approval") is not True:
            raise ValueError(f"publication approval was weakened: {key}")
    if original is not None:
        old_edges = {edge["key"]: edge for edge in original["edges"]}
        old_nodes = {node["key"]: node for node in original["nodes"]}
        new_nodes = {node["key"]: node for node in value["nodes"]}
        if list(new_nodes) != list(old_nodes):
            raise ValueError("node order or key set changed")
        if any(new_nodes[key] != old_nodes[key] for key in old_nodes):
            raise ValueError("node identities, kinds, or configuration changed")
        old_groups = {group["id"]: group for group in original["transition_groups"]}
        if value["transition_groups"] != original["transition_groups"]:
            raise ValueError("transition group identities or ordering changed")
        if list(edges) != list(old_edges):
            raise ValueError("edge ordering or key set changed")
        for key, edge in edges.items():
            old = old_edges[key]
            for field in ("id", "workflow_id", "transition_group_id", "target_node_id", "requires_approval", "context_mode", "context_source"):
                if edge.get(field) != old.get(field):
                    raise ValueError(f"edge identity/approval/context changed: {key}:{field}")
            old_keys = [item["key"] for item in old.get("parameters", [])]
            new_keys = [item["key"] for item in edge.get("parameters", [])]
            expected = old_keys + (["entry_transition"] if key in ROUTED_TRANSITIONS else [])
            if new_keys != expected:
                raise ValueError(f"edge business parameter contract drifted: {key}")


def write_snapshot() -> None:
    _write_text(
        GRAPH,
        json.dumps(build_graph(), ensure_ascii=False, indent=2) + "\n",
    )


def write_spec() -> None:
    raw = BASE_SPEC.read_text(encoding="utf-8")
    old_path = 'path = ".kent/workflows/puber-release.manifest.json"'
    if raw.count(old_path) != 1:
        raise ValueError("historical source-manifest path is not unique")
    raw = raw.replace(
        old_path,
        'path = ".kent/workflows/puber-release-kit-5af.manifest.json"',
        1,
    )
    note_start = "# Revision 90 preparation is closed by the deterministic builder as\n"
    note_end = "\n[source_manifest]"
    if raw.count(note_start) != 1 or raw.count(note_end) != 1:
        raise ValueError("historical preparation note is not unique")
    start = raw.index(note_start)
    end = raw.index(note_end, start)
    raw = (
        raw[:start]
        + "# Private source revision 91 preserves the Script-based preparation\n"
        + "# sequence and adds only exact business-route carriers to Script inputs.\n"
        + raw[end + 1 :]
    )
    note = (
        "# Private Kit5af-named proposal only: future qualified Kit K and "
        "source commit C remain unbound.\n"
    )
    _write_text(SPEC, note + raw)


def write_engineering_spec() -> None:
    _write_text(
        ENGINEERING_SPEC,
        json.dumps(build_engineering_spec(), ensure_ascii=False, indent=2) + "\n",
    )


def _derived_paths() -> set[str]:
    profile = tomllib.loads(PROFILE.read_text(encoding="utf-8"))
    spec = tomllib.loads(SPEC.read_text(encoding="utf-8"))
    result = {".kent/workflow-profile.toml", ".kent/project-contract.md"}

    def add(value: Any) -> None:
        if isinstance(value, str) and value:
            result.add(value)

    for section in ("commands", "procedures", "context_manifests"):
        for path in profile.get(section, {}).values():
            add(path)
    for kind in profile.get("work_kinds", {}).values():
        add(kind.get("plan"))
        add(kind.get("implement"))
    adapters = profile.get("adapters", {})
    for key in profile.get("required_adapters", []):
        add(adapters.get(key))
    release = profile.get("release", {})
    for key in ("spec_path", "builder_path", "snapshot_path"):
        add(release.get(key))
    for table in ("required_jobs_v1", "qualification_jobs_v1", "effect_jobs_v1"):
        for job in spec.get(table, {}).get("jobs", []):
            add(job.get("workflow_path"))
    for item in spec.get("approval_materializations", []):
        add(item.get("source_path"))
    add(spec.get("source_manifest", {}).get("path"))
    return result


def write_manifest() -> None:
    old = json.loads(BASE_MANIFEST.read_text(encoding="utf-8"))
    previous_sources = {
        item["key"].split("=", 1)[0]
        for item in old.get("external_roots", [])
        if isinstance(item, dict)
        and item.get("kind") == "source-sha256"
        and isinstance(item.get("key"), str)
        and "=" in item["key"]
    }
    derived = _derived_paths()
    all_sources = previous_sources | set(old.get("additional_paths", [])) | set(CLOSED_PATHS) | derived
    all_sources.discard(".kent/workflows/puber-release-kit-5af.manifest.json")
    all_sources -= DYNAMIC_RELEASE_PATHS
    additional = sorted(all_sources - derived - BINARY_SOURCE_ROOTS - DYNAMIC_RELEASE_PATHS)
    source_entries = []
    for relative in sorted(all_sources):
        path = _safe_file(relative)
        source_entries.append(
            {
                "kind": "source-sha256",
                "key": f"{relative}={_sha(path)}",
                "runtime_digest_required": True,
            }
        )
    builder = _safe_file(".kent/workflows/builders/puber_release_kit_5af.py")
    roots = [
        {
            "kind": "builder-sha256",
            "key": _sha(builder),
            "runtime_digest_required": True,
        },
        *source_entries,
    ]
    roots.sort(key=lambda item: (item["kind"], item["key"]))
    value = {
        "schema": "release_source_manifest_v1",
        "closure_algorithm": "project-instruction-closure-v1",
        "project_name": "Puber",
        "repository": "rovkinmax/Puber",
        "topology_kind": "puber-release",
        "additional_paths": additional,
        "additional_trees": [],
        "declared_prompt_references": [],
        "external_roots": roots,
        "runtime_attested": False,
    }
    _write_text(
        MANIFEST,
        json.dumps(value, ensure_ascii=False, indent=2) + "\n",
    )


def check() -> list[str]:
    errors: list[str] = []
    try:
        expected = build_graph()
        actual = json.loads(GRAPH.read_text(encoding="utf-8"))
        if actual != expected:
            errors.append("Kit5af-named graph differs from deterministic proposal")
        spec = tomllib.loads(SPEC.read_text(encoding="utf-8"))
        if (
            spec.get("schema_version") != 2
            or spec.get("workflow_source_intent", {}).get("id") != WORKFLOW_ID
            or spec.get("runtime_attested") is not False
            or spec.get("source_manifest", {}).get("runtime_attested") is not False
            or spec.get("source_manifest", {}).get("path") != str(MANIFEST.relative_to(ROOT))
        ):
            errors.append("cohort spec identity or non-attested state drifted")
        manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
        if manifest.get("runtime_attested") is not False:
            errors.append("private proposal manifest must remain non-attested")
        if set(manifest.get("additional_paths", [])) & BINARY_SOURCE_ROOTS:
            errors.append("binary source roots must not be UTF-8 additional paths")
        if (
            set(manifest.get("additional_paths", []))
            | {
                item.get("key", "").split("=", 1)[0]
                for item in manifest.get("external_roots", [])
                if item.get("kind") == "source-sha256"
            }
        ) & DYNAMIC_RELEASE_PATHS:
            errors.append("mutable release source paths must not be static source roots")
        if not manifest.get("external_roots") or any(
            not item.get("runtime_digest_required")
            for item in manifest.get("external_roots", [])
        ):
            errors.append("source closure bindings are incomplete")
        for item in manifest.get("external_roots", []):
            if item.get("kind") == "source-sha256":
                relative, expected_hash = item["key"].split("=", 1)
                if _sha(_safe_file(relative)) != expected_hash:
                    errors.append(f"cohort source digest drifted: {relative}")
        if not any(
            item.get("kind") == "builder-sha256"
            and item.get("key") == _sha(Path(__file__))
            for item in manifest.get("external_roots", [])
        ):
            errors.append("cohort manifest does not bind its builder")
        validate_graph(actual)
        engineering = json.loads(ENGINEERING_SPEC.read_text(encoding="utf-8"))
        if engineering != build_engineering_spec():
            errors.append("Engineering v25 differs from its deterministic proposal")
        validate_engineering_spec(engineering)
        profile = tomllib.loads(PROFILE.read_text(encoding="utf-8"))
        release = profile.get("release", {})
        if release != {
            **release,
            "spec_path": str(SPEC.relative_to(ROOT)),
            "builder_path": str(Path(__file__).resolve().relative_to(ROOT)),
            "snapshot_path": str(GRAPH.relative_to(ROOT)),
        }:
            errors.append("Puber profile does not select the proposal cohort")
        if "mobile_script_resource_lock" not in profile.get("required_adapters", []):
            errors.append("project-owned Script lease wrapper is not required by profile")
        if "mobile_script_resource_lock" in profile.get("kit_managed_adapters", []):
            errors.append("project-owned Script wrapper is incorrectly Kit-managed")
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        errors.append(str(error))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--write", action="store_true")
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.write == args.check:
        parser.error("choose exactly one of --write or --check")
    if args.write:
        write_snapshot()
        write_spec()
        write_engineering_spec()
        write_manifest()
        return 0
    errors = check()
    if errors:
        print("\n".join(f"puber-release-kit-5af: {error}" for error in errors), file=sys.stderr)
        return 1
    print("puber-release-kit-5af: deterministic non-attested proposal passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
