#!/usr/bin/env python3
"""Deterministic, source-only validator for the Puber Release graph."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import runpy
import stat
import subprocess
from pathlib import Path
import re
import sys
import tomllib
from typing import Any

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[3]
GRAPH = ROOT / ".kent/workflows/puber-release.json"
SPEC = ROOT / ".kent/workflows/specs/puber-release.toml"
MANIFEST = ROOT / ".kent/workflows/puber-release.manifest.json"
PROFILE = ROOT / ".kent/workflow-profile.toml"
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
    "profile_generation": ("f7d7db5c-7ae1-5d95-8d50-90c9b6dc4f72", ".kent/scripts/workflow-puber-release-profile-generation"),
}
AGENT_NODES = {
    "finalize_release": "b7ee7a42-7f1f-5e5b-a4e9-3a9c8c8a1f90",
}
SCRIPT_NODES = {
    "publish": ".kent/scripts/workflow-puber-release-publish",
    "monitor": ".kent/scripts/workflow-wait-github-release",
    "cleanup": ".kent/scripts/workflow-release-cleanup",
}
SCRIPT_NODES["profile_generation"] = NEW_NODES["profile_generation"][1]
RELEASE_IDENTITY_PARAMETERS = (
    "workspace_path", "operation_id", "task_short_id", "release_type",
    "release_version", "release_tag", "release_branch", "release_base_oid",
    "candidate_oid", "release_head_oid",
)
RELEASE_PROOF_PARAMETERS = (
    "profile_report", "profile_report_digest", "release_preparation_report",
    "release_preparation_report_digest", "verification_summary",
)
REVISIONED_RELEASE_CARRIER = RELEASE_IDENTITY_PARAMETERS + RELEASE_PROOF_PARAMETERS
SCRIPT_EDGE_PARAMETERS = {
    "start_release_intent_gate": ("release_intent_gate", ()),
    "release_intent_passed": (
        "prepare",
        (
            "workspace_path", "operation_id", "task_short_id", "release_type",
            "release_version", "release_branch",
        ),
    ),
    "prepare_profile_generation": (
        "profile_generation",
        (
            "workspace_path", "operation_id", "task_short_id", "release_type",
            "release_version", "release_tag", "release_branch",
            "release_base_oid", "candidate_oid",
        ),
    ),
    "profile_generation_passed": (
        "finalize_release",
        (
            "workspace_path", "operation_id", "task_short_id", "release_type",
            "release_version", "release_tag", "release_branch",
            "release_base_oid", "candidate_oid", "profile_report",
            "profile_report_digest",
        ),
    ),
    "profile_generation_needs_user_action": (
        "profile_generation",
        (
            "workspace_path", "operation_id", "task_short_id", "release_type",
            "release_version", "release_tag", "release_branch",
            "release_base_oid", "candidate_oid", "blocker_reason",
        ),
    ),
    "finalize_release_done": (
        "compliance",
        (
            "workspace_path", "operation_id", "task_short_id", "release_type",
            "release_version", "release_tag", "release_branch",
            "release_base_oid", "candidate_oid", "release_head_oid",
            "profile_report", "profile_report_digest",
            "release_preparation_report", "release_preparation_report_digest",
            "verification_summary",
        ),
    ),
    "finalize_release_needs_user_action": (
        "finalize_release",
        (
            "workspace_path", "operation_id", "task_short_id", "release_type",
            "release_version", "release_tag", "release_branch",
            "release_base_oid", "candidate_oid", "profile_report",
            "profile_report_digest", "blocker_reason",
        ),
    ),
    "compliance_ship_pr": (
        "ship_pr",
        (
            "workspace_path", "operation_id", "task_short_id", "release_type",
            "release_version", "release_tag", "release_branch",
            "release_base_oid", "candidate_oid", "release_head_oid",
            "profile_report", "profile_report_digest",
            "release_preparation_report", "release_preparation_report_digest",
            "verification_summary", "compliance_report",
        ),
    ),
    "release_intent_blocked": ("release_intent_gate", ("workspace_path", "operation_id", "task_short_id", "release_type", "release_version", "blocker_reason")),
    "release_intent_needs_user_action": ("release_intent_gate", ("workspace_path", "operation_id", "task_short_id", "release_type", "release_version", "blocker_reason")),
    "release_intent_invalid": ("release_intent_gate", ("workspace_path", "operation_id", "task_short_id", "release_type", "release_version", "blocker_reason")),
    "merge_watch_pr_merged": (
        "publish",
        (
            "workspace_path",
            "operation_id",
            "pr_url",
            "branch_name",
            "merge_strategy",
            "pr_head_oid",
            "pr_base_oid",
        ),
    ),
    "publish_needs_user_action": (
        "publish",
        (
            "blocker_reason",
            "workspace_path",
            "operation_id",
            "pr_url",
            "branch_name",
            "merge_strategy",
            "pr_head_oid",
            "pr_base_oid",
            "release_version",
            "release_tag",
            "target_commit",
            "release_notes_path",
            "publication_report",
            "publication_report_digest",
            "release_run",
        ),
    ),
    "monitor_release": (
        "monitor",
        (
            "release_version",
            "release_tag",
            "target_commit",
            "pr_url",
            "tag_push_status",
            "release_notes_path",
            "publication_report",
            "publication_report_digest",
            "release_run",
        ),
    ),
    "monitor_needs_user_action": (
        "monitor",
        (
            "blocker_reason",
            "workspace_path",
            "operation_id",
            "pr_url",
            "branch_name",
            "release_version",
            "release_tag",
            "target_commit",
            "tag_push_status",
            "release_notes_path",
            "publication_report",
            "publication_report_digest",
            "release_run",
        ),
    ),
    "release_release_published": (
        "cleanup",
        ("release_report", "publication_report", "publication_report_digest", "release_notes_path", "release_notes_digest", "release_run", "release_report_digest"),
    ),
    "cleanup_task_janitor": (
        "task_janitor",
        (
            "cleanup_report",
            "workspace_path",
            "branch_name",
            "pr_url",
            "cleanup_mode",
            "cleanup_session_id",
            "task_short_id",
            "publication_report",
            "publication_report_digest",
            "release_report",
            "release_report_digest",
            "release_notes_path",
            "release_notes_digest",
        ),
    ),
    "release_cancel_cleanup": ("cleanup", ("pr_url", "branch_name", "workspace_path", "cleanup_reason")),
    "merge_watch_close_without_merge": ("cleanup", ("workspace_path", "operation_id", "pr_url", "branch_name", "merge_strategy", "pr_head_oid", "pr_base_oid")),
    "task_janitor_blocked": ("cleanup", ("workspace_path", "operation_id", "branch_name", "cleanup_mode", "cleanup_report")),
    "task_janitor_needs_user_action": ("task_janitor", ("workspace_path", "operation_id", "branch_name", "cleanup_mode", "cleanup_report")),
    "task_janitor_retry": ("task_janitor", ("workspace_path", "operation_id", "branch_name", "cleanup_mode", "cleanup_report")),
}
REPAIR_EDGE_PARAMETERS = {
    "compliance_fix": ("prepare", REVISIONED_RELEASE_CARRIER + ("compliance_report",)),
    "ci_fix": ("prepare", REVISIONED_RELEASE_CARRIER + ("ci_report",)),
    "pr_fix": ("prepare", REVISIONED_RELEASE_CARRIER + ("blocker_reason",)),
}
REQUIRED_EDGE_PARAMETERS = {**SCRIPT_EDGE_PARAMETERS, **REPAIR_EDGE_PARAMETERS}
EXPECTED_NODES = set(BASE_NODE_IDS) | set(NEW_NODES) | set(AGENT_NODES)
EXPECTED_COMMANDS = {
    ":app:detektAll",
    ":app:testProdDebugUnitTest",
    ":app:assembleProdDebug",
}
PARAMETER_DESCRIPTIONS = {
    "blocker_reason": "Short human-readable blocker explanation in the task language when practical; include the missing access/input/check and the exact next user action needed.",
    "branch_name": "Name of the task or release branch associated with the pull request.",
    "candidate_oid": "Exact release candidate commit before profile generation.",
    "ci_report": "CI/check status report, including failures, skipped checks, or accepted absent checks.",
    "cleanup_mode": "Conservative cleanup mode.",
    "cleanup_reason": "Explicit user instruction or reason for cleanup without merge.",
    "cleanup_report": "Summary of cleanup performed or deliberately skipped.",
    "cleanup_session_id": "Session identity that owns task cleanup.",
    "closure_reason": "Explicit latest user instruction explaining why this task should be closed as not planned or canceled.",
    "compliance_report": "Compliance review report or summary.",
    "merge_report": "GitHub PR state, mergedAt, mergeCommit, and cleanup or release notes.",
    "merge_strategy": "Resolved merge strategy.",
    "operation_id": "Stable release operation identity.",
    "pr_base_oid": "Observed pull request base commit.",
    "pr_head_oid": "Observed pull request head commit.",
    "pr_report": "PR creation, review, conflict, or post-CI regression report.",
    "pr_url": "URL of the created, updated, or monitored pull request.",
    "profile_report": "Closed profile-generation report.",
    "profile_report_digest": "SHA-256 digest of the closed profile-generation report.",
    "publication_report": "Closed release-publication report.",
    "publication_report_digest": "Digest of the closed publication report carrier.",
    "release_base_oid": "Immutable release source commit.",
    "release_branch": "Exact release branch resolved from the release version.",
    "release_head_oid": "Exact finalized release branch head.",
    "release_notes_digest": "Digest of canonical notes bytes.",
    "release_notes_path": "Task-local path to the prepared non-empty Russian release notes file.",
    "release_preparation_report": "Closed release-preparation report.",
    "release_preparation_report_digest": "SHA-256 digest of the closed release-preparation report.",
    "release_report": "Release publication and automation monitoring report.",
    "release_report_digest": "Digest of the closed release report carrier.",
    "release_run": "Pinned release run carrier, or explicit not-selected state.",
    "release_tag": "Release tag to create or publish after PR merge.",
    "release_type": "Version bump type: minor by default, patch or major only when explicitly requested.",
    "release_version": "Release version prepared by this workflow.",
    "tag_push_status": "Result of local/remote tag creation and push.",
    "target_commit": "Master commit targeted by the release tag.",
    "task_short_id": "Stable human-readable task identity.",
    "verification_summary": "Commands run and their pass/fail results.",
    "waiting_reason": "Why the PR cannot advance yet and the exact user or external action needed.",
    "workspace_path": "Path to the task workspace or worktree relevant to this workflow transition.",
}


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain one JSON object")
    return value


def _provision_field(parameter: dict[str, Any]) -> dict[str, str]:
    return {
        "name": str(parameter.get("key", "")),
        "description": str(parameter.get("description", "")),
    }


def normalized_export(source: dict[str, Any]) -> dict[str, Any]:
    value = json.loads(json.dumps(source))
    for edge in value.get("edges", []):
        for parameter in edge.get("parameters", []):
            key = str(parameter.get("key", ""))
            if not parameter.get("description"):
                parameter["description"] = PARAMETER_DESCRIPTIONS.get(
                    key, f"Workflow transition field: {key}."
                )
            parameter.setdefault("purpose", "ordinary")
    value["derived_wiring"] = expected_derived_wiring(value)
    return value


def expected_derived_wiring(source: dict[str, Any]) -> dict[str, Any]:
    nodes = source.get("nodes", [])
    groups = source.get("transition_groups", [])
    edges = source.get("edges", [])
    node_by_id = {node.get("id"): node for node in nodes}
    edge_by_group = {edge.get("transition_group_id"): edge for edge in edges}

    node_fields: dict[str, list[dict[str, str]]] = {
        str(node.get("id")): [] for node in nodes
    }
    seen_node_fields: dict[str, set[str]] = {
        str(node.get("id")): set() for node in nodes
    }
    group_wiring: list[dict[str, Any]] = []
    for group in groups:
        group_id = str(group.get("id"))
        edge = edge_by_group.get(group_id, {})
        fields = [
            _provision_field(parameter)
            for parameter in edge.get("parameters", [])
        ]
        item: dict[str, Any] = {"transition_group_id": group_id}
        if fields:
            item["required_provision_fields"] = fields
        group_wiring.append(item)

        source_id = str(group.get("source_node_id"))
        for field in fields:
            if field["name"] not in seen_node_fields[source_id]:
                node_fields[source_id].append(field)
                seen_node_fields[source_id].add(field["name"])

    node_wiring: list[dict[str, Any]] = []
    for node in nodes:
        node_id = str(node.get("id"))
        item: dict[str, Any] = {"node_id": node_id}
        if node_fields[node_id]:
            item["possible_provision_fields"] = node_fields[node_id]
        node_wiring.append(item)

    edge_wiring: list[dict[str, Any]] = []
    for edge in edges:
        fields = [
            _provision_field(parameter)
            for parameter in edge.get("parameters", [])
        ]
        group = next(
            item for item in groups
            if item.get("id") == edge.get("transition_group_id")
        )
        source = node_by_id.get(group.get("source_node_id"), {})
        target = node_by_id.get(edge.get("target_node_id"), {})
        eligible = (
            source.get("kind") != "start"
            and target.get("kind") != "terminal"
        )
        applicability = {
            "available": eligible,
            "parameter_visible": eligible,
            "reason": "eligible" if eligible else "topology",
        }
        item = {"edge_id": str(edge.get("id"))}
        if fields:
            item["input_bindings"] = [
                {
                    "name": field["name"],
                    "source": "transition_output",
                    "field": field["name"],
                }
                for field in fields
            ]
            item["required_provision_fields"] = fields
        item["assignee_selection_applicability"] = applicability
        item["thinking_selection_applicability"] = dict(applicability)
        edge_wiring.append(item)

    return {
        "nodes": node_wiring,
        "transition_groups": group_wiring,
        "edges": edge_wiring,
    }


def check_graph(path: Path) -> list[str]:
    source = load_object(path)
    errors: list[str] = []
    workflow = source.get("workflow", {})
    if workflow != {
        "id": WORKFLOW_ID,
        "name": WORKFLOW_NAME,
        "description": workflow.get("description"),
        "version": 90,
        "execution_target_policy": {"mode": "head"},
        "schema_version": 4,
        "default": False,
        "source_revision": 90,
    }:
        errors.append("workflow identity, revision, schema, or default drifted")
    nodes = source.get("nodes", [])
    groups = source.get("transition_groups", [])
    edges = source.get("edges", [])
    if (len(nodes), len(groups), len(edges)) != (17, 46, 46):
        errors.append("graph counts must be exactly 17 nodes, 46 groups, 46 edges")
    by_key = {node.get("key"): node for node in nodes}
    if set(by_key) != EXPECTED_NODES:
        errors.append("graph node keys differ from the closed revision-90 set")
    for key, node_id in BASE_NODE_IDS.items():
        if by_key.get(key, {}).get("id") != node_id:
            errors.append(f"retained node id drifted: {key}")
    for key, (node_id, script) in NEW_NODES.items():
        node = by_key.get(key, {})
        if node.get("id") != node_id or node.get("kind") != "script" or node.get("script_path") != script:
            errors.append(f"new script node drifted: {key}")
    for key, node_id in AGENT_NODES.items():
        node = by_key.get(key, {})
        if (
            node.get("id") != node_id
            or node.get("kind") != "agent"
            or node.get("subagent_role") != "release-manager"
            or node.get("completion_mode") != "shell_command"
        ):
            errors.append(f"new agent node drifted: {key}")
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
        for parameter in edge.get("parameters", []):
            if (
                set(parameter) != {"key", "description", "purpose"}
                or not isinstance(parameter.get("description"), str)
                or not parameter["description"]
                or parameter.get("purpose") != "ordinary"
            ):
                errors.append(
                    f"transition parameter contract is incomplete: {edge.get('key')}:{parameter.get('key')}"
                )
        key = edge.get("key")
        if key in REQUIRED_EDGE_PARAMETERS:
            target_key, parameters = REQUIRED_EDGE_PARAMETERS[key]
            target_id = by_key.get(target_key, {}).get("id")
            if edge.get("target_node_id") != target_id:
                errors.append(f"required transition target drifted: {key}")
            actual = tuple(item.get("key") for item in edge.get("parameters", []))
            if actual != parameters or len(set(actual)) != len(actual):
                errors.append(f"required transition parameters drifted: {key}")
            if target_key in {"publish", "monitor"}:
                expected_approval = not (target_key == "monitor" and key == "monitor_release")
                if edge.get("requires_approval") is not expected_approval:
                    errors.append(f"script transition approval policy drifted: {key}")
        if edge.get("target_node_id") in {by_key.get(name, {}).get("id") for name in ("finalize_release", "compliance", "ship_pr")}:
            prompt = edge.get("prompt_template", "")
            for marker in ("puber_release_preparation_report_v2", "puber_release_profile_checkpoint_v2", "debug_validation", "non-publishable", ".kent/commands/release.md"):
                if marker not in prompt: errors.append("revision-90 signing/checkpoint prompt contract is absent")
        declared = {item.get("key") for item in edge.get("parameters", [])}
        prompt_references = set(
            re.findall(r"\{\{\s*\.Params\.([A-Za-z_][A-Za-z0-9_]*)", str(edge.get("prompt_template", "")))
        )
        missing = sorted(prompt_references - declared)
        if missing:
            errors.append(
                f"transition prompt references undeclared parameters: {key}: {', '.join(missing)}"
            )
    for key, (target_key, parameters) in REQUIRED_EDGE_PARAMETERS.items():
        matches = [edge for edge in edges if edge.get("key") == key]
        if len(matches) != 1:
            errors.append(f"required transition missing or duplicated: {key}")
    if len({edge.get("transition_group_id") for edge in edges}) != len(groups):
        errors.append("every transition group must have exactly one edge")
    if source.get("derived_wiring") != expected_derived_wiring(source):
        errors.append("derived_wiring does not match the complete current graph")
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


def check_signing_contract() -> list[str]:
    errors = []
    helper = runpy.run_path(str(ROOT / ".kent/scripts/workflow-puber-release-intent"))
    try:
        workflow = helper["_normalized_workflow"](ROOT, ".github/workflows/release.yml")
        raw = tomllib.loads(SPEC.read_text())
        jobs = raw["effect_jobs_v1"]["jobs"]
        if len(jobs) != 1 or len(workflow["jobs"]) != 1:
            return ["production effect job set is not exact"]
        job, source = jobs[0], workflow["jobs"][0]
        if job["credential_profile"] != "github-platform-contents-write-android-production-signing" or job["allowed_effects"] != ["artifact-upload", "github-release-create", "production-signing"]:
            errors.append("production credential profile or effects drifted")
        secrets = ["KEYALIAS", "RELEASE_KEYSTORE_BASE64", "STOREPASS"]
        if workflow["environment"] or job["effective_environment"] or source["effective_environment"] or job["secret_refs"] != secrets or source["secret_refs"] != secrets:
            errors.append("production secrets must be scoped only to two steps")
        normalized_steps = [dict(step, validation_required=False) for step in source["steps"]]
        if job["steps"] != normalized_steps:
            errors.append("release effect steps differ from executable YAML")
        names = [step["name"] for step in source["steps"]]
        if names != ["", "", "Assert GitHub-hosted runner", "Preflight production signing", "Build and prepare production artifacts", "", "Create GitHub Release with empty initial body"]:
            errors.append("production effect step order drifted")
        signing_env = {"PUBER_RELEASE_SIGNING_MODE": "production", **{key: "${{ secrets." + key + " }}" for key in secrets}}
        for index, step in enumerate(source["steps"]):
            expected_env = signing_env if index in (3, 4) else {"GH_TOKEN": "${{ github.token }}"} if index == 6 else {}
            if step["effective_environment"] != expected_env or step["secret_refs"] != (secrets if index in (3, 4) else []):
                errors.append("step signing or publication authority escaped its boundary")
        preflight, build = source["steps"][3]["run"], source["steps"][4]["run"]
        for key in secrets:
            if 'test -n "$' + key + '"' not in preflight or 'test -n "$' + key + '"' not in build:
                errors.append("production signing presence checks are incomplete")
        for token in ("trap cleanup_signing EXIT", "rm -f app/release.jks app/keystore.properties", "rm -rf app/build", "-storepass:env STOREPASS", "3e0ddb2c5d39953d278f8cce813ff07a6b74059f1f9caa8fd752602e2bb8b61a", 'test "$alias_sha" = "$pin"', 'test "$signer_sha" = "$pin"', 'test "${#apks[@]}" -eq 1'):
            if token not in build: errors.append("production signing cleanup or certificate proof is absent")
        ordering = [build.find(token) for token in ("trap cleanup_signing EXIT", "base64 --decode", 'test "$alias_sha" = "$pin"', "./gradlew --no-daemon :app:assembleProdRelease", 'test "$signer_sha" = "$pin"', 'cp "${apks[0]}"')]
        if -1 in ordering or ordering != sorted(ordering):
            errors.append("production signing/build/copy order drifted")
        if "KEYPASS" in (ROOT / ".github/workflows/release.yml").read_text():
            errors.append("independent production key password is forbidden")
        key = ROOT / "app/debug.jks"
        metadata = key.lstat()
        pins = helper["VALIDATION_SIGNING"]
        if not stat.S_ISREG(metadata.st_mode) or key.is_symlink() or metadata.st_uid != os.getuid() or digest(key) != pins["validation_signing_sha256"]:
            errors.append("tracked debug key content/type drifted")
        blob = subprocess.run(["git", "ls-files", "--stage", "--", "app/debug.jks"], cwd=ROOT, capture_output=True, text=True, check=True).stdout.strip()
        if blob != "100644 " + pins["validation_signing_blob_oid"] + " 0\tapp/debug.jks":
            errors.append("tracked debug key Git blob drifted")
        if stat.S_ISREG(metadata.st_mode) and not key.is_symlink():
            certificate = subprocess.run(["keytool", "-exportcert", "-keystore", str(key), "-storepass", "android", "-alias", "androiddebugkey"], capture_output=True, check=True).stdout
            if hashlib.sha256(certificate).hexdigest() != pins["validation_signer_certificate_sha256"]:
                errors.append("tracked debug key certificate drifted")
        gradle = (ROOT / "app/build.gradle.kts").read_text()
        for token in ("PUBER_RELEASE_SIGNING_MODE", "debug_validation", "production", "NOFOLLOW_LINKS", "requireSigningMode()", "storeFile = releaseKeyFile", "store.isKeyEntry(alias)", '"verify", "--print-certs"', "signers == listOf(expectedReleaseCertificate)", *[v for v in pins.values() if isinstance(v, str) and len(v) in (40, 64)]):
            if token not in gradle: errors.append("Gradle explicit signing admission or pin is absent")
    except (OSError, ValueError, KeyError, IndexError, subprocess.SubprocessError) as error:
        errors.append(f"production signing contract is malformed: {error}")
    return errors


def derived_manifest_paths() -> set[str]:
    profile = tomllib.loads(PROFILE.read_text(encoding="utf-8"))
    spec = tomllib.loads(SPEC.read_text(encoding="utf-8"))
    paths = {".kent/workflow-profile.toml", ".kent/project-contract.md"}

    def add(value: Any) -> None:
        if isinstance(value, str) and value:
            paths.add(value)

    for section in ("commands", "procedures", "context_manifests"):
        for value in profile.get(section, {}).values():
            add(value)
    for work_kind in profile.get("work_kinds", {}).values():
        add(work_kind.get("plan"))
        add(work_kind.get("implement"))
    adapters = profile.get("adapters", {})
    for key in profile.get("required_adapters", []):
        add(adapters.get(key))
    release = profile.get("release", {})
    for key in ("spec_path", "builder_path", "snapshot_path"):
        add(release.get(key))
    for table in (
        "required_jobs_v1",
        "qualification_jobs_v1",
        "effect_jobs_v1",
    ):
        for job in spec.get(table, {}).get("jobs", []):
            add(job.get("workflow_path"))
    for materialization in spec.get("approval_materializations", []):
        add(materialization.get("source_path"))
    add(spec.get("source_manifest", {}).get("path"))
    return paths


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
    if set(paths) & derived_manifest_paths():
        errors.append("manifest additional_paths may not repeat a derived path")
    for relative in paths:
        candidate = ROOT / relative
        if not candidate.is_file() or candidate.is_symlink():
            errors.append(f"manifest source is missing or unsafe: {relative}")
            continue
        try:
            candidate.read_text(encoding="utf-8")
        except UnicodeDecodeError:
            errors.append(
                f"manifest additional_path must be valid UTF-8: {relative}"
            )
    roots = value.get("external_roots", [])
    root_identities = [
        (item.get("kind"), item.get("key"))
        for item in roots
        if (
            isinstance(item, dict)
            and isinstance(item.get("kind"), str)
            and isinstance(item.get("key"), str)
        )
    ]
    if (
        len(root_identities) != len(roots)
        or root_identities != sorted(root_identities)
        or len(root_identities) != len(set(root_identities))
    ):
        errors.append(
            "manifest external_roots must be sorted and unique by kind and key"
        )
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
            if not (ROOT / relative).is_file() or digest(ROOT / relative) != expected_digest:
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
    publish = next((v for v in variants if v.get("key") == "publish_after_merge"), {})
    if publish.get("authority_transitions") != ["merge_watch_pr_merged", "publish_needs_user_action"]:
        errors.append("publish variant must bind the real publication entry transitions")
    materializations = raw.get("approval_materializations", [])
    if len(materializations) != 1:
        errors.append("release spec must contain one approval materialization")
    elif materializations[0].get("variant_key") != "publish_after_merge" or set(materializations[0].get("templates", {})) != {"merge_watch_pr_merged", "publish_needs_user_action"}:
        errors.append("approval materialization templates must bind both real publication transitions")
    return errors


def check_source(path: Path) -> list[str]:
    errors = check_graph(path) + check_manifest() + check_spec() + check_workflows() + check_signing_contract()
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", type=Path)
    parser.add_argument("--emit", type=Path)
    parser.add_argument("--refresh-derived", type=Path)
    args = parser.parse_args()
    if sum(bool(value) for value in (args.check, args.emit, args.refresh_derived)) != 1:
        parser.error("choose exactly one of --check, --emit, or --refresh-derived")
    if args.refresh_derived:
        source = normalized_export(load_object(args.refresh_derived))
        args.refresh_derived.write_text(
            json.dumps(source, indent=2) + "\n",
            encoding="utf-8",
        )
        return 0
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
