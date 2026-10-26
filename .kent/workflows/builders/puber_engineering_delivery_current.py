#!/usr/bin/env python3
"""Build the source-only Puber Engineering workflow candidate."""
from __future__ import annotations

import argparse
import copy
import dataclasses
import hashlib
import importlib
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tomllib

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[3]
KIT_ROOT = Path(
    "/Users/rovkinmax/.kent/worktrees/kent-engineering-kit/"
    "runtime-sdk-puber-30b908c9"
)
KIT_COMMIT = "30b908c9fc7097c0464ae67d58f891a1dd7103fb"
KIT_REF = "refs/remotes/origin/main"
WORKFLOW_ID = "7061fc98-aae5-4468-9d89-fa28bfe30c72"
NATIVE_VERSION = 13
PLAN_ID = "a0716188-a4c1-4506-98c8-39580fbbee07"
BACKLOG_ID = "ad05d64c-154f-4f89-a656-f9d08cc9419e"
START_EDGE_ID = "dec372ad-1ef3-490a-a0c5-fd10a8622e56"
START_GROUP_ID = "01ab6705-8314-46f0-a31d-bbe36acb70e4"
FIXTURE = ROOT / ".kent/scripts/tests/fixtures/puber-engineering-delivery-native-v13.json"
PROFILE = ROOT / ".kent/workflow-profile.toml"
BOOTSTRAP = ROOT / ".kent/scripts/workflow_kit_runtime.py"
GRAPH_PATH = ROOT / ".kent/workflows/puber-engineering-delivery-current.json"
FIXTURE_SHA256 = "883efe3b5abcd5d987106bdf427d6d1508dcc3bc6620527ea8b253827028f418"
EXPECTED_BOOTSTRAP_SHA256 = "13c596ff74172a103ce3f4c97a35b05b2090513917562dad914e1a1e39624d99"
UUID4_RE = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-"
    r"[89ab][0-9a-f]{3}-[0-9a-f]{12}$"
)

NEW_IDS = {
    "node": {"engineering_source_admission": "355ea30b-e4b5-464d-95ab-247ae684293c"},
    "groups": {
        "ci_monitor_preparation_needs_user_action": "81c3be58-cca6-4b39-9567-58dc54087a2f",
        "ci_monitor_retry_preparation": "552991a6-c9f6-4046-a076-6d7d0bcd72a8",
        "compliance_replan": "3b272191-e82a-4763-953e-8d60bcec6632",
        "gate_retry_verification": "291397b5-438e-49a8-aefb-ea2c0b7256a5",
        "plan_contract_revalidate": "37259cff-6ffc-4a9f-aaeb-aa04588c212f",
        "plan_revalidation_resume_continue": "670bf6de-4683-4c40-acf3-63a73cbe14cc",
        "plan_revalidation_resume_fix_continue": "c9e92247-4992-42c9-aba4-e74978130d61",
        "plan_revalidation_resume_verify": "9610a417-eafb-4205-aade-819551a50388",
        "plan_review_resume_continue": "4e2ea5e4-512f-4972-a706-b8e61974016e",
        "plan_review_resume_fix_continue": "60c5fb2c-8638-4986-b1ba-c5a26d6d3a85",
        "plan_review_resume_verify": "ba4ada7c-0461-4864-9d9c-83e41be5f587",
        "smoke_wont_do": "61d8b0b9-bf01-44fa-a9f6-19ad3a552a09",
        "engineering_source_admitted": "973dc9fe-3212-4894-a82a-401dfbd5b2d4",
    },
    "edges": {
        "ci_monitor_preparation_needs_user_action": "d872d159-2845-4fa0-a8d8-f59ad5c0a7f6",
        "ci_monitor_retry_preparation": "6ae4007d-4d0c-4c71-ba57-10f629c6ee1e",
        "compliance_replan": "a0b33ca5-1fd5-402c-a22b-f50ac1dce011",
        "gate_retry_verification": "0c528d69-0ea6-4b4e-a37d-1e3bada25cef",
        "plan_contract_revalidate": "8bf7c45a-0d1f-483b-96f1-50e698c89e21",
        "plan_revalidation_resume_continue": "938cf613-7f40-4cb5-a278-7afb91301cf4",
        "plan_revalidation_resume_fix_continue": "fb54e6bb-5f3a-467c-8f5f-537abbd03d86",
        "plan_revalidation_resume_verify": "951275a5-2340-4bcb-a622-39bab6443be4",
        "plan_review_resume_continue": "60318c85-402e-444a-8a2c-73d059da41c9",
        "plan_review_resume_fix_continue": "1f1c004e-5b8d-4313-b9eb-79a442ba447f",
        "plan_review_resume_verify": "a5ec99b7-8bd7-44ba-a544-2b88eae0bc96",
        "smoke_wont_do": "7cd02f2b-5d63-4c4e-a7e2-c12040765109",
        "engineering_source_admitted": "5c346b5c-2c15-4493-9340-4e24e441172e",
    },
}

EXPECTED_NEW_TRANSITIONS = {
    "ci_monitor_preparation_needs_user_action",
    "ci_monitor_retry_preparation",
    "compliance_replan",
    "gate_retry_verification",
    "plan_contract_revalidate",
    "plan_revalidation_resume_continue",
    "plan_revalidation_resume_fix_continue",
    "plan_revalidation_resume_verify",
    "plan_review_resume_continue",
    "plan_review_resume_fix_continue",
    "plan_review_resume_verify",
    "smoke_wont_do",
}


def fail(message: str) -> None:
    raise ValueError(message)


def git(root: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", "--no-optional-locks", "-c", "core.fsmonitor=false",
         "-c", f"core.hooksPath={os.devnull}", "-C", str(root), *args],
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
        env={**os.environ, "GIT_OPTIONAL_LOCKS": "0", "GIT_TERMINAL_PROMPT": "0"},
    )
    if result.returncode:
        fail("pinned source cohort cannot be qualified")
    return result.stdout.decode("utf-8").strip()


def canonical_bytes(value: object) -> bytes:
    return json.dumps(
        value, ensure_ascii=False, sort_keys=True,
        separators=(",", ":"), allow_nan=False,
    ).encode("utf-8")


def prefix_digest(graph: dict) -> str:
    prefix = {
        "nodes": sorted(
            ({"id": row["id"], "key": row["key"], "kind": row["kind"]}
             for row in graph["nodes"]),
            key=lambda row: row["id"],
        ),
        "transition_groups": sorted(
            ({"id": row["id"], "source_node_id": row["source_node_id"],
              "transition_id": row["transition_id"]}
             for row in graph["transition_groups"]),
            key=lambda row: row["id"],
        ),
        "edges": sorted(
            ({"id": row["id"], "key": row["key"],
              "transition_group_id": row["transition_group_id"],
              "target_node_id": row["target_node_id"]}
             for row in graph["edges"]),
            key=lambda row: row["id"],
        ),
    }
    return hashlib.sha256(canonical_bytes(prefix)).hexdigest()


def qualify_inputs() -> None:
    try:
        profile = tomllib.loads(PROFILE.read_text(encoding="utf-8"))
        binding = profile["kit_runtime"]
        bootstrap = BOOTSTRAP.read_bytes()
    except (OSError, KeyError, tomllib.TOMLDecodeError) as error:
        raise ValueError("Puber source binding is unavailable") from error
    if binding != {
        "schema_version": 1,
        "root": str(KIT_ROOT),
        "commit": KIT_COMMIT,
        "publication_ref": KIT_REF,
    }:
        fail("Puber profile differs from the reviewed Kit source binding")
    if hashlib.sha256(bootstrap).hexdigest() != EXPECTED_BOOTSTRAP_SHA256:
        fail("Puber Kit bootstrap differs from the reviewed source")
    if stat.S_IMODE(BOOTSTRAP.stat().st_mode) != 0o700:
        fail("Puber Kit bootstrap mode differs from the reviewed source")
    kit_bootstrap = KIT_ROOT / "workflowkit/kit_runtime.py"
    if (
        kit_bootstrap.read_bytes() != bootstrap
        or stat.S_IMODE(kit_bootstrap.stat().st_mode) != 0o600
    ):
        fail("published Kit bootstrap bytes or mode differ")
    if (
        git(KIT_ROOT, "rev-parse", "--show-toplevel") != str(KIT_ROOT)
        or git(KIT_ROOT, "rev-parse", "--verify", "HEAD^{commit}") != KIT_COMMIT
        or git(KIT_ROOT, "rev-parse", "--verify", f"{KIT_REF}^{{commit}}") != KIT_COMMIT
        or git(KIT_ROOT, "status", "--porcelain=v1", "-z",
               "--untracked-files=all", "--ignore-submodules=none")
    ):
        fail("retained Kit source cohort is dirty or has moved")
    if hashlib.sha256(FIXTURE.read_bytes()).hexdigest() != FIXTURE_SHA256:
        fail("frozen native Workflow fixture digest differs")
    for name, module in tuple(sys.modules.items()):
        if name != "workflowkit" and not name.startswith("workflowkit."):
            continue
        origin = getattr(module, "__file__", None)
        if not isinstance(origin, str):
            fail("preloaded workflowkit module has no source identity")
        try:
            if not Path(origin).resolve(strict=True).is_relative_to(KIT_ROOT):
                fail("foreign workflowkit module was loaded before qualification")
        except OSError as error:
            raise ValueError("preloaded workflowkit module source is unavailable") from error


def deterministic_graph(fixture: dict) -> tuple[dict, str]:
    qualify_inputs()
    source = str(KIT_ROOT)
    sys.path.insert(0, source)
    try:
        profile_module = importlib.import_module("workflowkit.profile")
        delivery_module = importlib.import_module("workflowkit.delivery")
        model_module = importlib.import_module("workflowkit.model")
        graph_module = importlib.import_module("workflowkit.graph")
        for module in (profile_module, delivery_module, model_module, graph_module):
            origin = Path(module.__file__).resolve(strict=True)
            if not origin.is_relative_to(KIT_ROOT):
                fail("Kit import escaped the retained source root")
        profile = profile_module.ProjectProfile.from_toml(
            ROOT, PROFILE.read_text(encoding="utf-8"),
        )
        spec = delivery_module.build_delivery_workflow(profile, NATIVE_VERSION)
        native = fixture["graph"]
        native_edges = {edge["key"]: edge for edge in native["edges"]}
        native_nodes = {node["key"]: node for node in native["nodes"]}
        if (
            fixture.get("workflow_id") != WORKFLOW_ID
            or native_nodes["backlog"]["id"] != BACKLOG_ID
            or native_nodes["plan"]["id"] != PLAN_ID
            or native_edges["start_plan"]["id"] != START_EDGE_ID
            or native_edges["start_plan"]["transition_group_id"] != START_GROUP_ID
        ):
            fail("native Workflow entry identities differ from the approved baseline")

        # Native v13 approval state is authoritative for every existing route.
        edges = []
        for edge in spec.edges:
            if edge.key in native_edges:
                edges.append(dataclasses.replace(
                    edge, requires_approval=native_edges[edge.key]["requires_approval"],
                ))
            else:
                edges.append(edge)
        start = next(edge for edge in edges if edge.key == "start_plan")
        plan_prompt = start.prompt
        edges = [
            dataclasses.replace(
                edge, target="engineering_source_admission", prompt=None,
            ) if edge.key == "start_plan" else edge
            for edge in edges
        ]
        edges.append(model_module.EdgeSpec(
            key="engineering_source_admitted",
            source="engineering_source_admission",
            transition="engineering_source_admitted",
            target="plan",
            context="new_session",
            context_source="immediate_source",
            prompt=plan_prompt,
            transition_description=(
                "Published source admission succeeded; begin the existing Plan "
                "body in a fresh Session."
            ),
            requires_approval=False,
        ))
        nodes = spec.nodes + (model_module.NodeSpec(
            key="engineering_source_admission",
            kind="script",
            display_name="Admit Engineering Source",
            script_path=".kent/scripts/workflow-puber-engineering-admission",
        ),)
        spec = model_module.WorkflowSpec(
            name=spec.name,
            description=spec.description,
            execution_target=spec.execution_target,
            nodes=nodes,
            edges=tuple(edges),
        )
        spec.validate()
        inspected = {
            "workflow_id": WORKFLOW_ID,
            "expected_version": NATIVE_VERSION,
            "graph": copy.deepcopy(native),
        }
        plan = graph_module.plan_workflow_graph(spec, inspected, metadata_changed=False)
        if plan.destructive:
            fail("Kit graph projection removes existing Workflow content")
        if set(plan.added_nodes) != {"engineering_source_admission"}:
            fail("Kit graph projection changed the approved node delta")
        expected_added_edges = EXPECTED_NEW_TRANSITIONS | {"engineering_source_admitted"}
        if set(plan.added_edges) != expected_added_edges:
            fail("Kit graph projection changed the approved transition delta")
        result = plan.document["graph"]
        result["workflow_id"] = WORKFLOW_ID
        original_group_by_id = {
            group["id"]: group for group in result["transition_groups"]
        }
        for key, identifier in NEW_IDS["node"].items():
            row = next(item for item in result["nodes"] if item["key"] == key)
            previous = row["id"]
            row["id"] = identifier
            for group in result["transition_groups"]:
                if group["source_node_id"] == previous:
                    group["source_node_id"] = identifier
            for edge in result["edges"]:
                if edge["target_node_id"] == previous:
                    edge["target_node_id"] = identifier
        edge_by_key = {edge["key"]: edge for edge in result["edges"]}
        for route, identifier in NEW_IDS["groups"].items():
            if route not in edge_by_key:
                fail(f"projected graph is missing transition group route {route}")
            original_group = original_group_by_id[
                edge_by_key[route]["transition_group_id"]
            ]
            original_group["id"] = identifier
        for key, identifier in NEW_IDS["edges"].items():
            if key not in edge_by_key:
                fail(f"projected graph is missing transition {key}")
            edge_by_key[key]["id"] = identifier
        node_ids = {row["key"]: row["id"] for row in result["nodes"]}
        group_ids = {
            (row["source_node_id"], row["transition_id"]): row["id"]
            for row in result["transition_groups"]
        }
        group_by_id = {row["id"]: row for row in result["transition_groups"]}
        node_key_by_id = {row["id"]: row["key"] for row in result["nodes"]}
        for edge in result["edges"]:
            edge["workflow_id"] = WORKFLOW_ID
            group = original_group_by_id[edge["transition_group_id"]]
            source_key = node_key_by_id[group["source_node_id"]]
            edge["transition_group_id"] = group_ids[
                (node_ids[source_key], group["transition_id"])
            ]
        for collection in ("nodes", "transition_groups"):
            for row in result[collection]:
                row["workflow_id"] = WORKFLOW_ID
        projected_nodes = {row["key"]: row for row in result["nodes"]}
        projected_groups = {row["id"]: row for row in result["transition_groups"]}
        projected_edges = {row["key"]: row for row in result["edges"]}
        projected_group_ids = {row["id"] for row in result["transition_groups"]}
        if len(projected_nodes) != len(result["nodes"]) or len(projected_edges) != len(result["edges"]):
            fail("projected graph contains duplicate node or edge keys")
        for node in native["nodes"]:
            projected = projected_nodes.get(node["key"])
            if projected is None or (projected["id"], projected["kind"]) != (
                node["id"], node["kind"],
            ):
                fail(f"native node identity or kind changed: {node['key']}")
        for group in native["transition_groups"]:
            if group["id"] not in projected_group_ids:
                fail("native transition-group identity changed")
        for edge in native["edges"]:
            projected = projected_edges.get(edge["key"])
            if projected is None or projected["id"] != edge["id"]:
                fail(f"native transition identity changed: {edge['key']}")
            if projected.get("requires_approval", False) != edge.get("requires_approval", False):
                fail(f"native approval policy changed: {edge['key']}")
        if any(edge["transition_group_id"] not in projected_groups for edge in result["edges"]):
            fail("projected edge references an absent transition group")
        if (
            len(result["nodes"]) != 32
            or len(result["edges"]) != 105
            or len(result["transition_groups"]) != 103
        ):
            fail("projected graph has unexpected source counts")
        if edge_by_key["start_plan"]["target_node_id"] != node_ids["engineering_source_admission"]:
            fail("Backlog does not enter through the source-admission Script")
        direct = [
            edge for edge in result["edges"]
            if edge["target_node_id"] == PLAN_ID
            and group_by_id[edge["transition_group_id"]]["source_node_id"] == BACKLOG_ID
        ]
        if direct:
            fail("Backlog retains a direct Plan bypass")
        if edge_by_key["start_plan"].get("prompt_template") or "parameters" in edge_by_key["start_plan"]:
            fail("initial Script edge contains an old prompt or source-admission carrier")
        if edge_by_key["engineering_source_admitted"].get("requires_approval") is not False:
            fail("successful initial admission adds an approval gate")
        new_ids = (
            set(NEW_IDS["node"].values())
            | set(NEW_IDS["groups"].values())
            | set(NEW_IDS["edges"].values())
        )
        if not all(UUID4_RE.fullmatch(value) for value in new_ids):
            fail("frozen new Workflow identities are not canonical UUIDv4 values")
        baseline_ids = (
            {row["id"] for row in native["nodes"]}
            | {row["id"] for row in native["transition_groups"]}
            | {row["id"] for row in native["edges"]}
        )
        if baseline_ids & new_ids:
            fail("frozen new identity collides with the native baseline")
        return result, prefix_digest(native)
    finally:
        sys.path.remove(source)


def candidate_bytes() -> bytes:
    fixture_raw = FIXTURE.read_bytes()
    fixture = json.loads(fixture_raw)
    graph, prefix_sha256 = deterministic_graph(fixture)
    return json.dumps(
        {
            "schema": "puber-engineering-current-candidate-v1",
            "activation_status": "UNACTIVATED",
            "workflow_id": WORKFLOW_ID,
            "expected_version": NATIVE_VERSION,
            "baseline_fixture_sha256": hashlib.sha256(fixture_raw).hexdigest(),
            "baseline_prefix_sha256": prefix_sha256,
            "graph": graph,
        },
        ensure_ascii=False,
        sort_keys=True,
        indent=2,
    ).encode("utf-8") + b"\n"


def main() -> int:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--check", action="store_true")
    arguments = parser.parse_args()
    try:
        expected = candidate_bytes()
        if arguments.write:
            if GRAPH_PATH.exists():
                if GRAPH_PATH.read_bytes() != expected:
                    fail("candidate already exists with different bytes")
            else:
                GRAPH_PATH.write_bytes(expected)
                GRAPH_PATH.chmod(0o644)
        elif not GRAPH_PATH.is_file() or GRAPH_PATH.read_bytes() != expected:
            fail("checked candidate differs from deterministic projection")
    except (OSError, ValueError, KeyError, TypeError, ImportError) as error:
        print(f"Engineering source candidate check failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
