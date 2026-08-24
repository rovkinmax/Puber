#!/usr/bin/env python3
"""Deterministic, source-only validator for the Puber Release graph."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import tomllib
from typing import Any

ROOT = Path(__file__).resolve().parents[3]
GRAPH = ROOT / ".kent/workflows/puber-release.json"
SPEC = ROOT / ".kent/workflows/specs/puber-release.toml"
MANIFEST = ROOT / ".kent/workflows/puber-release.manifest.json"
WORKFLOW_ID = "10d8adb2-c74c-4ef0-8b5c-311cb5cd0459"
WORKFLOW_NAME = "Puber Release"
BASE_NODE_IDS = {
    "backlog": "69c194f1-46fa-43f9-ba62-95e5332fe6e5",
    "prepare": "4f98b2c1-ef8c-4323-8040-b9a40e939910",
    "compliance": "e7444395-765d-422b-890e-5b2dd69f52e4",
    "ship_pr": "2456358a-92e6-44ca-b8f1-846517d88032",
    "ci_monitor": "bd9e337e-7189-412e-8735-3dbdb6dcf5b1",
    "publish": "7ab29a1a-9ab5-40c4-ae91-1c6f47c12941",
    "monitor": "ac6c3970-99e4-4b40-841b-9dc7da036159",
    "cleanup": "16a1dcd7-2737-45e9-88ba-93ebc8430b89",
    "wont_do": "dd3e4e97-a974-4e6a-bafe-da1d891bb5ce",
    "waiting_pr": "94e4c8ab-b0d7-45cf-b922-3a970a6e3eee",
    "done": "7dfc3956-53ec-4307-ae59-daf1a3dd1151",
}
NEW_NODES = {
    "release_intent_gate": ("0b0ccf3b-8ad1-5e3c-a982-b2b836432a5e", ".kent/scripts/workflow-puber-release-intent"),
    "ci_watch": ("598063c6-2cef-51ec-a613-043e5b6335db", ".kent/scripts/workflow-wait-github-ci"),
    "merge_watch": ("44390763-8581-53aa-8700-e912a67aaca4", ".kent/scripts/workflow-wait-github-pr"),
    "task_janitor": ("dfbd8b53-4c7c-56e7-bcd0-631389648090", ".kent/scripts/workflow-task-janitor"),
}
SCRIPT_NODES = {
    "publish": ".kent/scripts/workflow-puber-release-publish",
    "monitor": ".kent/scripts/workflow-wait-github-release",
    "cleanup": ".kent/scripts/workflow-release-cleanup",
}
EXPECTED_NODES = set(BASE_NODE_IDS) | set(NEW_NODES)
EXPECTED_COMMANDS = {
    ":app:detektAll",
    ":app:testProdDebugUnitTest",
    ":app:assembleProdDebug",
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain one JSON object")
    return value


def check_graph(path: Path) -> list[str]:
    source = load_object(path)
    errors: list[str] = []
    workflow = source.get("workflow", {})
    if workflow != {
        "id": WORKFLOW_ID,
        "name": WORKFLOW_NAME,
        "description": workflow.get("description"),
        "version": 88,
        "execution_target_policy": {"mode": "head"},
        "schema_version": 4,
        "default": False,
        "source_revision": 88,
    }:
        errors.append("workflow identity, revision, schema, or default drifted")
    nodes = source.get("nodes", [])
    groups = source.get("transition_groups", [])
    edges = source.get("edges", [])
    if (len(nodes), len(groups), len(edges)) != (15, 42, 42):
        errors.append("graph counts must be exactly 15 nodes, 42 groups, 42 edges")
    by_key = {node.get("key"): node for node in nodes}
    if set(by_key) != EXPECTED_NODES:
        errors.append("graph node keys differ from the closed revision-88 set")
    for key, node_id in BASE_NODE_IDS.items():
        if by_key.get(key, {}).get("id") != node_id:
            errors.append(f"retained node id drifted: {key}")
    for key, (node_id, script) in NEW_NODES.items():
        node = by_key.get(key, {})
        if node.get("id") != node_id or node.get("kind") != "script" or node.get("script_path") != script:
            errors.append(f"new script node drifted: {key}")
    for key, script in SCRIPT_NODES.items():
        node = by_key.get(key, {})
        if node.get("kind") != "script" or node.get("script_path") != script:
            errors.append(f"publication node is not the required script: {key}")
        if "subagent_role" in node or "completion_mode" in node:
            errors.append(f"script node retains agent-only fields: {key}")
    publish_id = BASE_NODE_IDS["publish"]
    incoming = [edge for edge in edges if edge.get("target_node_id") == publish_id]
    if not incoming or any(edge.get("requires_approval") is not True for edge in incoming):
        errors.append("every publication entry must require approval")
    for node in nodes:
        if node.get("workflow_id") != WORKFLOW_ID:
            errors.append(f"node workflow identity drifted: {node.get('key')}")
    for item in groups:
        if item.get("workflow_id") != WORKFLOW_ID:
            errors.append("transition group workflow identity drifted")
    for edge in edges:
        if edge.get("workflow_id") != WORKFLOW_ID:
            errors.append("edge workflow identity drifted")
    return errors


def check_workflows() -> list[str]:
    errors: list[str] = []
    pr = (ROOT / ".github/workflows/pr-checks.yml").read_text(encoding="utf-8")
    release = (ROOT / ".github/workflows/release.yml").read_text(encoding="utf-8")
    if "permissions:\n  contents: read" not in pr:
        errors.append("PR Checks must declare read-only contents permission")
    if "pull_request:" not in pr or "workflow_dispatch:" in pr:
        errors.append("PR Checks event source drifted")
    for job in ("detekt:", "unit-tests:", "build:"):
        if pr.count("\n  " + job) != 1:
            errors.append(f"PR Checks missing exact job {job[:-1]}")
    if any(token in pr for token in ("secrets.", "RELEASE_", "gh release", "upload-artifact", "push:", "workflow_run")):
        errors.append("PR Checks contains a release or credential effect")
    for command in EXPECTED_COMMANDS:
        if command not in pr:
            errors.append(f"PR Checks missing exact command {command}")
    if "java-version: '21'" not in pr and 'java-version: "21"' not in pr:
        errors.append("PR Checks must use Java 21")
    if "tags:" not in release or "workflow_dispatch:" in release:
        errors.append("release workflow must be tag-push only")
    if "--generate-notes=false" not in release or '--notes ""' not in release:
        errors.append("release workflow must create an empty non-generated initial body")
    if "sha256sum" not in release or "assembleProdRelease" not in release:
        errors.append("release workflow must build and checksum the tagged APK")
    return errors


def check_manifest() -> list[str]:
    errors: list[str] = []
    value = load_object(MANIFEST)
    required = {"schema", "closure_algorithm", "project_name", "repository", "topology_kind", "additional_paths", "additional_trees", "declared_prompt_references", "external_roots", "runtime_attested"}
    if set(value) != required:
        errors.append("manifest has unknown or missing closed fields")
    if value.get("schema") != "release_source_manifest_v1" or value.get("closure_algorithm") != "project-instruction-closure-v1":
        errors.append("manifest schema or closure algorithm drifted")
    if value.get("project_name") != "Puber" or value.get("repository") != "rovkinmax/Puber" or value.get("topology_kind") != "puber-release":
        errors.append("manifest project identity drifted")
    paths = value.get("additional_paths", [])
    if paths != sorted(set(paths)):
        errors.append("manifest additional_paths must be sorted and unique")
    for relative in paths:
        candidate = ROOT / relative
        if not candidate.is_file() or candidate.is_symlink():
            errors.append(f"manifest source is missing or unsafe: {relative}")
    roots = value.get("external_roots", [])
    expected = {item.get("key"): item for item in roots if isinstance(item, dict)}
    builder_key = f"builder-sha256={digest(Path(__file__))}"
    if not any(item.get("kind") == "builder-sha256" and item.get("key") == digest(Path(__file__)) for item in roots if isinstance(item, dict)):
        errors.append("manifest does not bind the executable builder digest")
    for item in roots:
        if not isinstance(item, dict) or item.get("runtime_digest_required") is not True:
            errors.append("manifest external roots must require runtime digests")
        if isinstance(item, dict) and item.get("kind") == "source-sha256":
            raw = str(item.get("key", ""))
            if "=" not in raw:
                errors.append("source digest binding is malformed")
                continue
            relative, expected_digest = raw.split("=", 1)
            if relative not in paths or not (ROOT / relative).is_file() or digest(ROOT / relative) != expected_digest:
                errors.append(f"source digest binding drifted: {relative}")
    return errors


def check_spec() -> list[str]:
    errors: list[str] = []
    try:
        raw = tomllib.loads(SPEC.read_text(encoding="utf-8"))
    except Exception as exc:
        return [f"release spec is not TOML: {exc}"]
    if raw.get("schema_version") != 2 or raw.get("spec_kind") != "release":
        errors.append("release spec must use schema 2 release contract")
    if raw.get("topology_kind") != "puber-release" or raw.get("adoption_mode") != "managed-in-place":
        errors.append("release spec topology drifted")
    if raw.get("repository") != "rovkinmax/Puber" or raw.get("project_name") != "Puber":
        errors.append("release spec identity drifted")
    intent = raw.get("workflow_source_intent", {})
    if intent.get("id") != WORKFLOW_ID or intent.get("name") != WORKFLOW_NAME or intent.get("expected_project_default") is not False:
        errors.append("release spec workflow source intent drifted")
    required = raw.get("required_jobs_v1", {}).get("jobs", [])
    if {row.get("job_key") for row in required} != {"detekt", "unit-tests", "build"}:
        errors.append("release spec required job closure is not exact")
    if raw.get("qualification_jobs_v1", {}).get("jobs") != []:
        errors.append("release spec must not invent qualification jobs")
    variants = raw.get("operation_variants", [])
    if not any(v.get("key") == "publish_after_merge" and v.get("approval_required") is True for v in variants):
        errors.append("release spec lacks approval-required publication variant")
    if not any(v.get("authority_kind", {}).get("kind") == "github_run_template" for v in variants):
        errors.append("release spec lacks exact github_run operation variant")
    return errors


def check_source(path: Path) -> list[str]:
    errors = check_graph(path) + check_manifest() + check_spec() + check_workflows()
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", type=Path)
    parser.add_argument("--emit", type=Path)
    args = parser.parse_args()
    if bool(args.check) == bool(args.emit):
        parser.error("choose exactly one of --check or --emit")
    if args.emit:
        args.emit.write_text(GRAPH.read_text(encoding="utf-8"), encoding="utf-8")
        return 0
    errors = check_source(args.check)
    if errors:
        for error in errors:
            print(f"builder: {error}", file=sys.stderr)
        return 1
    print("puber-release: deterministic source check passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
