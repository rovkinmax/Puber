#!/usr/bin/env python3
"""Build the Release-only Puber v97/source-92 successor proposal."""
from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import runpy
import stat
import sys
import tomllib
from pathlib import Path
from typing import Any

sys.dont_write_bytecode = True

ROOT = Path(__file__).resolve().parents[3]
BASE_GRAPH = ROOT / ".kent/workflows/puber-release-kit-5af.json"
BASE_MANIFEST = ROOT / ".kent/workflows/puber-release-kit-5af.manifest.json"
BASE_SPEC = ROOT / ".kent/workflows/specs/puber-release-kit-5af.toml"
RELEASE_BUILDER = ROOT / ".kent/workflows/builders/puber_release.py"
SPEC = ROOT / ".kent/workflows/specs/puber-release-current.toml"
GRAPH = ROOT / ".kent/workflows/puber-release-current.json"
MANIFEST = ROOT / ".kent/workflows/puber-release-current.manifest.json"
PROFILE = ROOT / ".kent/workflow-profile.toml"
WORKFLOW_ID = "10d8adb2-c74c-4ef0-8b5c-311cb5cd0459"
WORKFLOW_VERSION = 97
SOURCE_REVISION = 92
SOURCE_92_NATIVE_IDS = {
    "ci_monitor_retry_preparation": {
        "group_id": "f346e6e8-e7c2-459e-9b33-5056a179b64a",
        "edge_id": "971dc100-1294-44fe-814a-5a81e3c9e6c8",
    },
    "ci_monitor_preparation_needs_user_action": {
        "group_id": "ce992c18-54dc-4da2-b6fe-efe8c1e27425",
        "edge_id": "07039a95-b19d-4852-a766-b20545c8d06c",
    },
}
CANONICAL_UUID4 = re.compile(
    r"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-"
    r"[89ab][0-9a-f]{3}-[0-9a-f]{12}"
)
ENTRY_TRANSITION = "entry_transition"
JANITOR_SCRIPT = ".kent/scripts/workflow-puber-release-janitor"

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
    "ci_prepare_already_merged",
    "waiting_pr_ci_recheck",
)
RENAMED_TRANSITIONS = {
    "ci_prepare_failed": "ci_prepare_needs_user_action",
}
CI_PREPARATION_FAILURE_FIELDS = (
    "workspace_path", "task_short_id", "ci_preparation_diagnostic",
)
CI_PREPARATION_RETRY_FIELDS = (
    "workspace_path", "task_short_id", "delivery_context",
)
CI_PREPARATION_USER_ACTION_FIELDS = (
    "workspace_path", "task_short_id", "ci_preparation_diagnostic",
    "blocker_reason",
)
PR_IDENTITY_FIELDS = frozenset({
    "pr_url", "branch_name", "merge_strategy", "pr_head_oid", "pr_base_oid",
    "ci_contract", "ci_report", "pr_feedback_cursor",
})
PR_BASE_PARAMETER = {
    "key": "pr_base_branch",
    "description": (
        "Expected pull-request target branch from the existing release policy, "
        "verified against the actual baseRefName; never inferred from an OID."
    ),
    "purpose": "ordinary",
}
CANONICAL_PARAMETERS = {
    "operation_id": {
        "key": "operation_id",
        "description": "Stable release operation identity.",
        "purpose": "ordinary",
    },
    "target_commit": {
        "key": "target_commit",
        "description": "Exact merge commit T observed for the merged pull request.",
        "purpose": "ordinary",
    },
}
ROUTE_PARAMETER = {
    "key": ENTRY_TRANSITION,
    "description": (
        "Business route hint; the Script validates it against the exact "
        "incoming graph edge. It is not native identity or approval."
    ),
    "purpose": "ordinary",
}
BINARY_SOURCE_ROOTS = {"app/debug.jks"}
DYNAMIC_RELEASE_PATHS = {"app/build.gradle.kts"}
ENGINEERING_SOURCE = re.compile(r"(?:^|/)puber-engineering-delivery(?:-|\.)")


def _validate_source_92_native_ids() -> None:
    route_keys = {
        "ci_monitor_retry_preparation",
        "ci_monitor_preparation_needs_user_action",
    }
    if set(SOURCE_92_NATIVE_IDS) != route_keys:
        raise ValueError("source-92 native ID map differs from the approved routes")
    identifiers: list[str] = []
    for key, mapping in SOURCE_92_NATIVE_IDS.items():
        if set(mapping) != {"group_id", "edge_id"}:
            raise ValueError(f"source-92 native ID map is incomplete: {key}")
        for identity in mapping.values():
            if not isinstance(identity, str) or not CANONICAL_UUID4.fullmatch(identity):
                raise ValueError(f"source-92 native ID is not canonical lowercase UUIDv4: {key}")
            identifiers.append(identity)
    if len(set(identifiers)) != len(identifiers):
        raise ValueError("source-92 native ID map contains duplicate identities")


# Current source closure. Kit qualification and runtime attestation remain
# separate gates and are intentionally not implied by this proposal.
CLOSED_PATHS = (
    ".kent/commands/cleanup-task.md",
    ".kent/commands/release.md",
    ".kent/context/delivery.md",
    ".kent/project-contract.md",
    ".kent/scripts/tests/test-puber-release-current",
    ".kent/scripts/tests/test-puber-qualified-runtime",
    ".kent/scripts/tests/test-puber-qualified-runtime-integration",
    ".kent/scripts/tests/test-workflow-puber-release-ci",
    ".kent/scripts/tests/test-workflow-puber-release-intent",
    ".kent/scripts/tests/test-workflow-puber-release-profile-generation",
    ".kent/scripts/tests/test-workflow-puber-release-publish",
    ".kent/scripts/tests/test-workflow-release-cleanup",
    ".kent/scripts/tests/test-workflow-wait-github-release",
    ".kent/scripts/workflow-puber-release-ci",
    ".kent/scripts/workflow-puber-release-intent",
    ".kent/scripts/workflow-puber-release-janitor",
    ".kent/scripts/workflow-puber-release-publish",
    ".kent/scripts/workflow-release-cleanup",
    ".kent/scripts/workflow-wait-github-release",
    ".kent/subagents/release-manager.md",
    ".kent/workflow-profile.toml",
    ".kent/workflows/README.md",
    ".kent/workflows/builders/puber_release.py",
    ".kent/workflows/builders/puber_release_current.py",
    ".kent/workflows/puber-release-current.json",
    ".kent/workflows/puber-release-kit-5af.json",
    ".kent/workflows/puber-release-kit-5af.manifest.json",
    ".kent/workflows/specs/puber-release-current.toml",
    ".kent/workflows/specs/puber-release-kit-5af.toml",
)
CLEANUP_AGENT_EDGES = (
    "release_release_published",
    "release_cancel_cleanup",
    "merge_watch_close_without_merge",
    "task_janitor_blocked",
)
CLEANUP_PROVIDER_GUIDANCE = """\
Provider-read provenance for the current Release source:
When `delivery_reliability = "v1"` is enabled, include the exact native
`commentary` supplied to this current Cleanup Agent as the top-level
`commentary` field in the helper's JSON input. Do not fabricate `_kent`, use
another Session's identity, or recover commentary from a different Task
packet. Preserve the commentary unchanged in the Janitor handoff. If the
current native commentary is missing or malformed, preserve resources and
report the blocker; the helper must not perform a GitHub API read or cleanup
effect without authenticated current producer ancestry."""

FULL_PR_FEEDBACK_FIELDS = (
    "workspace_path", "operation_id", "task_short_id", "release_type",
    "release_version", "release_tag", "release_branch", "release_base_oid",
    "candidate_oid", "release_head_oid", "profile_report", "profile_report_digest",
    "release_preparation_report", "release_preparation_report_digest",
    "verification_summary", "pr_url", "branch_name", "merge_strategy",
    "pr_head_oid", "pr_base_oid", "pr_base_branch", "pr_report", "ci_contract",
    "ci_report", "pr_feedback_cursor",
)
CI_PREPARATION_FAILURE_PROMPT = """Classify the failed Puber CI preparation for {{.TaskShortId}}.

Read `.kent/context/delivery.md` first. This route carries only `workspace_path`,
`task_short_id`, and `ci_preparation_diagnostic`; it contains no CI report,
contract, flat PR identity, or release carrier. Do not invent or reconstruct
omitted packets.

Validate the closed `workflow-ci-preparation-diagnostic-v1`. Use the qualified
producer locator in the native commentary and reconstruct only its bounded
producer ancestry. Require the exact final `puber-release-ci` failure and its
`prepare-ci` child; bind `input_strings_sha256` to the child's original string
inputs, not to wrapper-remapped values. Restore the full release carrier only
from the original wrapper producer and its validated ancestry. Compare
operation, release identity, completed profile checkpoint and report digests;
preserve report strings and `verification_summary` byte-for-byte. Reject
missing, ambiguous, conflicting, foreign, or changed evidence.

Retry only for a transient failure with its exact validated resume context and
existing applicable authority, or for a resolved archive blocker after checking
the authorized owner's actual evidence. Pack the exact
`ci_monitor_retry_preparation` transition using fresh native Task/graph
readbacks, `delivery_context`, and producer references. Do not synthesize
`entry_transition`, scan for a latest producer, reset history, infer green CI,
or treat a diagnostic as a CI packet. For invalid input, identity conflict,
missing context, or an unresolved blocker, use
`ci_monitor_preparation_needs_user_action` with the unchanged diagnostic and a
precise `blocker_reason`; existing user-action approval still applies.

Only the fresh `ci_prepare` Script may recheck PR identity and CI state. A retry
does not itself prove readiness, create CI packets, authorize merge, or permit
publication."""
CI_PREPARATION_USER_ACTION_PROMPT = """Resolve the CI preparation blocker for {{.TaskShortId}}.

Read `.kent/context/delivery.md` and inspect the exact original diagnostic:
{{.Params.ci_preparation_diagnostic}}

Workspace: {{.Params.workspace_path}}
Task: {{.Params.task_short_id}}
Blocker: {{.Params.blocker_reason}}

Re-read the referenced producer ancestry and authorized decision/evidence.
Preserve the diagnostic bytes and original release/PR context; do not invent
packets, discard invalid supplied values, reset retry history, or infer green
CI. Retry only through `ci_monitor_retry_preparation` after its eligibility,
producer closure, completed checkpoint, and current native transition all
validate. Otherwise remain blocked with the precise external or authority gap.
Approval authorizes only a fresh re-check; it does not repair contradictory
evidence or grant publication authority."""


def _sha(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        while chunk := stream.read(1024 * 1024):
            digest.update(chunk)
    return digest.hexdigest()


def _safe_file(relative: str) -> Path:
    path = ROOT / relative
    try:
        metadata = path.lstat()
    except OSError as error:
        raise ValueError(f"source is missing: {relative}") from error
    if stat.S_ISLNK(metadata.st_mode) or not stat.S_ISREG(metadata.st_mode):
        raise ValueError(f"source is not a regular file: {relative}")
    return path


def _load_object(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain one JSON object")
    return value


def _historical_base() -> dict[str, Any]:
    graph = _load_object(BASE_GRAPH)
    manifest = _load_object(BASE_MANIFEST)
    graph_path = str(BASE_GRAPH.relative_to(ROOT))
    recorded = {
        item["key"].split("=", 1)[1]
        for item in manifest.get("external_roots", [])
        if item.get("kind") == "source-sha256"
        and isinstance(item.get("key"), str)
        and item["key"].split("=", 1)[0] == graph_path
    }
    if recorded != {_sha(BASE_GRAPH)}:
        raise ValueError("frozen 5af graph differs from its historical manifest")
    workflow = graph.get("workflow", {})
    if (
        workflow.get("id") != WORKFLOW_ID
        or workflow.get("name") != "Puber Release"
        or workflow.get("version") != 95
        or workflow.get("source_revision") != 91
        or workflow.get("schema_version") != 4
        or workflow.get("default") is not False
    ):
        raise ValueError("frozen 5af Release identity is not the expected base")
    if (len(graph.get("nodes", [])), len(graph.get("transition_groups", [])),
            len(graph.get("edges", []))) != (18, 51, 51):
        raise ValueError("frozen 5af topology is not 18/51/51")
    edges = {edge.get("key"): edge for edge in graph.get("edges", [])}
    historical_routes = set(ROUTED_TRANSITIONS) | {
        RENAMED_TRANSITIONS["ci_prepare_failed"],
    }
    if len(edges) != 51 or historical_routes - set(edges):
        raise ValueError("frozen 5af routes are incomplete or duplicated")
    routed = {
        edge["key"] for edge in graph["edges"]
        if any(parameter.get("key") == ENTRY_TRANSITION
               for parameter in edge.get("parameters", []))
    }
    if routed != historical_routes:
        raise ValueError("frozen 5af route-carrier set is not the reviewed 21 edges")
    for key in (
        "merge_watch_pr_merged", "publish_needs_user_action",
        "profile_generation_needs_user_action",
    ):
        if edges[key].get("requires_approval") is not True:
            raise ValueError(f"historical approval gate drifted: {key}")
    return graph


def _source_node(graph: dict[str, Any], edge: dict[str, Any]) -> str:
    group = next(
        (item for item in graph["transition_groups"]
         if item.get("id") == edge.get("transition_group_id")),
        None,
    )
    if group is None:
        raise ValueError(f"edge has no exact transition group: {edge.get('key')}")
    return group["source_node_id"]


def _parameter_for_source_node(
    graph: dict[str, Any], source_node_id: str, key: str,
) -> dict[str, Any]:
    candidates = [
        copy.deepcopy(parameter)
        for candidate in graph["edges"]
        if _source_node(graph, candidate) == source_node_id
        for parameter in candidate.get("parameters", [])
        if parameter.get("key") == key
    ]
    if key == "pr_base_branch":
        return copy.deepcopy(PR_BASE_PARAMETER)
    if key in CANONICAL_PARAMETERS:
        return copy.deepcopy(CANONICAL_PARAMETERS[key])
    if key == "ci_preparation_diagnostic":
        return {
            "key": key,
            "description": (
                "Closed typed CI-preparation diagnostic bound to the exact "
                "original input-string digest."
            ),
            "purpose": "ordinary",
        }
    if key == "delivery_context":
        return {
            "key": key,
            "description": (
                "Validated original post-PR CI context for bounded preparation "
                "retry; not a report or readiness proof."
            ),
            "purpose": "ordinary",
        }
    if candidates:
        if any(candidate != candidates[0] for candidate in candidates[1:]):
            raise ValueError(f"source-node parameter definitions conflict: {key}")
        return candidates[0]
    candidates = [
        copy.deepcopy(parameter)
        for candidate in graph["edges"]
        for parameter in candidate.get("parameters", [])
        if parameter.get("key") == key
    ]
    if not candidates:
        raise ValueError(f"no canonical {key} parameter in graph")
    return candidates[0]


def _parameter_for_source(
    graph: dict[str, Any], edge: dict[str, Any], key: str,
) -> dict[str, Any]:
    return _parameter_for_source_node(graph, _source_node(graph, edge), key)


def _insert_after(
    graph: dict[str, Any], edge: dict[str, Any], key: str, anchor: str,
) -> None:
    parameters = edge.get("parameters")
    if not isinstance(parameters, list):
        raise ValueError(f"edge parameters are invalid: {edge.get('key')}")
    keys = [item.get("key") for item in parameters]
    if key in keys:
        return
    if anchor not in keys:
        raise ValueError(f"missing {anchor} before adding {key}: {edge.get('key')}")
    parameters.insert(
        keys.index(anchor) + 1,
        _parameter_for_source(graph, edge, key),
    )


def _replace_parameters(
    graph: dict[str, Any], edge: dict[str, Any], keys: tuple[str, ...],
) -> None:
    edge["parameters"] = [
        _parameter_for_source(graph, edge, key) for key in keys
    ]


def _add_missing_prompt_bindings(
    graph: dict[str, Any], original: dict[str, Any], modified: set[str],
) -> None:
    nodes = {node["id"]: node for node in graph["nodes"]}
    before = {edge["key"]: edge for edge in original["edges"]}
    for edge in graph["edges"]:
        if edge["key"] not in modified:
            continue
        target = nodes[edge["target_node_id"]]
        if target.get("kind") != "agent":
            continue
        prompt = edge.get("prompt_template")
        if not isinstance(prompt, str) or not prompt.strip():
            raise ValueError(f"modified Agent edge has no prompt: {edge['key']}")
        previous_key = RENAMED_TRANSITIONS.get(edge["key"], edge["key"])
        previous_edge = before.get(previous_key, {})
        old_keys = {
            parameter.get("key")
            for parameter in previous_edge.get("parameters", [])
        }
        added = [
            parameter["key"] for parameter in edge["parameters"]
            if parameter.get("key") not in old_keys
        ]
        placeholders = set(re.findall(
            r"\{\{\.Params\.([A-Za-z_][A-Za-z0-9_]*)\}\}", prompt,
        ))
        missing = [key for key in added if key not in placeholders]
        if missing:
            prompt = prompt.rstrip() + "\n\nAdditional release carrier fields:\n" + "\n".join(
                f"{key}: {{{{.Params.{key}}}}}" for key in missing
            )
        if "pr_base_branch" in added:
            prompt += (
                "\nVerify the exact expected target branch against actual "
                "baseRefName and preserve it unchanged; never infer it from an "
                "OID or silently retarget."
            )
        edge["prompt_template"] = prompt


def _transform_graph(value: dict[str, Any], original: dict[str, Any]) -> None:
    _validate_source_92_native_ids()
    value["workflow"]["version"] = WORKFLOW_VERSION
    value["workflow"]["source_revision"] = SOURCE_REVISION
    edges = {edge["key"]: edge for edge in value["edges"]}
    modified: set[str] = set()

    # Replace only the approved inherited operation/target descriptions;
    # keep parameter keys and route payloads unchanged.
    canonical_route_parameters = {
        "publish_needs_user_action": {"operation_id", "target_commit"},
        "monitor_needs_user_action": {"operation_id"},
    }
    for edge_key, parameter_keys in canonical_route_parameters.items():
        for parameter in edges[edge_key]["parameters"]:
            if parameter.get("key") not in parameter_keys:
                continue
            canonical = CANONICAL_PARAMETERS.get(parameter["key"])
            if canonical is not None:
                parameter["description"] = canonical["description"]

    # S4 turns the old direct preparation retry route into a typed failure
    # handoff to the existing CI Agent. The Agent's retry route has only the
    # three approved native fields and no synthetic entry_transition.
    failure = edges.pop("ci_prepare_needs_user_action")
    failure["key"] = "ci_prepare_failed"
    failure["target_node_id"] = next(
        node["id"] for node in value["nodes"] if node.get("key") == "ci_monitor"
    )
    failure["requires_approval"] = False
    failure["context_mode"] = "new_session"
    failure["context_source"] = {"kind": "immediate_source"}
    _replace_parameters(value, failure, CI_PREPARATION_FAILURE_FIELDS)
    failure["prompt_template"] = CI_PREPARATION_FAILURE_PROMPT
    edges["ci_prepare_failed"] = failure
    group = next(
        item for item in value["transition_groups"]
        if item.get("id") == failure["transition_group_id"]
    )
    group["transition_id"] = "ci_prepare_failed"
    modified.add("ci_prepare_failed")

    monitor_id = next(
        node["id"] for node in value["nodes"] if node.get("key") == "ci_monitor"
    )
    prepare_id = next(
        node["id"] for node in value["nodes"] if node.get("key") == "ci_prepare"
    )

    def new_route(key: str, target: str, fields: tuple[str, ...], *,
                  approval: bool, context_mode: str,
                  prompt: str | None = None) -> tuple[dict[str, Any], dict[str, Any]]:
        group_id = SOURCE_92_NATIVE_IDS[key]["group_id"]
        edge_id = SOURCE_92_NATIVE_IDS[key]["edge_id"]
        transition_group = {
            "id": group_id,
            "workflow_id": WORKFLOW_ID,
            "source_node_id": monitor_id,
            "transition_id": key,
            "display_name": " ".join(part.capitalize() for part in key.split("_")),
            "description": (
                "Typed CI preparation recovery preserves the validated original "
                "release and producer context."
            ),
        }
        edge = {
            "id": edge_id,
            "workflow_id": WORKFLOW_ID,
            "transition_group_id": group_id,
            "key": key,
            "target_node_id": target,
            "assignee_selection": "configured",
            "thinking_selection": "configured",
            "requires_approval": approval,
            "context_mode": context_mode,
            "context_source": {"kind": "immediate_source"},
            "parameters": [
                _parameter_for_source_node(value, monitor_id, field)
                for field in fields
            ],
        }
        if prompt is not None:
            edge["prompt_template"] = prompt
        return transition_group, edge

    retry_group, retry_edge = new_route(
        "ci_monitor_retry_preparation",
        prepare_id,
        CI_PREPARATION_RETRY_FIELDS,
        approval=False,
        context_mode="new_session",
    )
    action_group, action_edge = new_route(
        "ci_monitor_preparation_needs_user_action",
        monitor_id,
        CI_PREPARATION_USER_ACTION_FIELDS,
        approval=True,
        context_mode="compact_and_continue_session",
        prompt=CI_PREPARATION_USER_ACTION_PROMPT,
    )
    value["transition_groups"].extend([retry_group, action_group])
    value["edges"].extend([retry_edge, action_edge])
    edges[retry_edge["key"]] = retry_edge
    edges[action_edge["key"]] = action_edge
    modified.update({retry_edge["key"], action_edge["key"]})

    # Keep target branch identity distinct from mutable base OID/CI history.
    for edge in value["edges"]:
        keys = {parameter.get("key") for parameter in edge.get("parameters", [])}
        if keys & PR_IDENTITY_FIELDS:
            anchor = (
                "pr_base_oid" if "pr_base_oid" in keys
                else "branch_name" if "branch_name" in keys
                else "pr_url"
            )
            _insert_after(value, edge, "pr_base_branch", anchor)
            modified.add(edge["key"])

    # The PR-feedback path returns the entire flat release proof and cursor to
    # Prepare; no report/checkpoint identity is reconstructed from prose.
    _replace_parameters(value, edges["pr_feedback"], FULL_PR_FEEDBACK_FIELDS)
    modified.add("pr_feedback")

    # Publish -> Monitor and Monitor -> Cleanup preserve the original operation
    # and PR identity rather than synthesizing defaults at a later phase.
    _replace_parameters(value, edges["monitor_release"], (
        "workspace_path", "operation_id", "branch_name", "pr_base_branch",
        "release_version", "release_tag", "target_commit", "pr_url",
        "tag_push_status", "release_notes_path", "publication_report",
        "publication_report_digest", "release_run", ENTRY_TRANSITION,
    ))
    modified.add("monitor_release")
    _replace_parameters(value, edges["release_release_published"], (
        "workspace_path", "operation_id", "branch_name", "pr_base_branch",
        "pr_url", "release_report", "publication_report",
        "publication_report_digest", "release_notes_path", "release_notes_digest",
        "release_run", "release_report_digest",
    ))
    modified.add("release_release_published")

    ship_prompt = edges["compliance_ship_pr"].get("prompt_template", "")
    old_ship_instruction = (
        "Complete with CI watch and provide the existing PR carrier."
    )
    new_ship_instruction = (
        "Before creating or updating the PR, resolve its expected base branch "
        "from the existing release policy; verify the actual baseRefName and "
        "carry that exact pr_base_branch. Never infer it from an OID or silently "
        "retarget. Complete with CI watch and provide the existing PR carrier."
    )
    if ship_prompt.count(old_ship_instruction) != 1:
        raise ValueError("Ship PR prompt target-policy anchor is not unique")
    edges["compliance_ship_pr"]["prompt_template"] = ship_prompt.replace(
        old_ship_instruction, new_ship_instruction, 1,
    )

    # Cancellation and unmerged-close routes retain the same operation/target.
    _insert_after(value, edges["release_cancel_cleanup"], "operation_id", "workspace_path")
    _insert_after(value, edges["release_cancel_cleanup"], "pr_base_branch", "branch_name")
    modified.add("release_cancel_cleanup")
    _insert_after(value, edges["merge_watch_close_without_merge"],
                  "pr_base_branch", "pr_base_oid")
    modified.add("merge_watch_close_without_merge")

    # Merge approval binds T observed by the existing merge watcher. Publish
    # compares this supplied T with a fresh merged-PR read; it never retargets.
    _insert_after(value, edges["merge_watch_pr_merged"],
                  "target_commit", "pr_base_branch")
    modified.add("merge_watch_pr_merged")

    # Cleanup -> Janitor and every Janitor recovery outcome retain the original
    # operation ID and explicit PR target branch.
    _insert_after(value, edges["cleanup_task_janitor"],
                  "operation_id", "workspace_path")
    for key in (
        "task_janitor_done", "task_janitor_blocked",
        "task_janitor_needs_user_action", "task_janitor_retry",
        "task_janitor_preserved",
    ):
        _insert_after(value, edges[key], "pr_base_branch", "branch_name")
        modified.add(key)
    modified.add("cleanup_task_janitor")

    janitor = next(node for node in value["nodes"] if node.get("key") == "task_janitor")
    janitor["script_path"] = JANITOR_SCRIPT

    for key in CLEANUP_AGENT_EDGES:
        prompt = edges[key].get("prompt_template")
        if not isinstance(prompt, str) or not prompt.strip():
            raise ValueError(f"Cleanup Agent edge has no prompt: {key}")
        if "Provider-read provenance for the current Release source:" not in prompt:
            edges[key]["prompt_template"] = (
                prompt.rstrip() + "\n\n" + CLEANUP_PROVIDER_GUIDANCE
            )
            modified.add(key)

    # Preserve the native IDs and existing 21 edge-key business routes.
    for key in ROUTED_TRANSITIONS:
        edge = edges[key]
        parameters = edge.get("parameters", [])
        keys = [parameter.get("key") for parameter in parameters]
        if keys.count(ENTRY_TRANSITION) != 1:
            raise ValueError(f"route carrier count drifted: {key}")
        if keys[-1] != ENTRY_TRANSITION:
            edge["parameters"] = [
                parameter for parameter in parameters
                if parameter.get("key") != ENTRY_TRANSITION
            ] + [copy.deepcopy(ROUTE_PARAMETER)]
            modified.add(key)

    _add_missing_prompt_bindings(value, original, modified)
    normalizer = runpy.run_path(str(RELEASE_BUILDER))
    value.update(normalizer["normalized_export"](value))


def build_graph() -> dict[str, Any]:
    original = _historical_base()
    value = copy.deepcopy(original)
    _transform_graph(value, original)
    validate_graph(value, original)
    return value


def validate_graph(value: dict[str, Any], original: dict[str, Any] | None = None) -> None:
    _validate_source_92_native_ids()
    workflow = value.get("workflow", {})
    if (
        workflow.get("id") != WORKFLOW_ID
        or workflow.get("version") != WORKFLOW_VERSION
        or workflow.get("source_revision") != SOURCE_REVISION
        or workflow.get("name") != "Puber Release"
        or workflow.get("default") is not False
    ):
        raise ValueError("successor identity must preserve UUID and use 97/source 92")
    if (len(value.get("nodes", [])), len(value.get("transition_groups", [])),
            len(value.get("edges", []))) != (18, 53, 53):
        raise ValueError("successor must preserve 18 nodes and add the approved S4 routes")
    edges = {edge.get("key"): edge for edge in value["edges"]}
    required_edges = set(ROUTED_TRANSITIONS) | {
        "ci_prepare_failed",
        "ci_monitor_retry_preparation",
        "ci_monitor_preparation_needs_user_action",
    }
    if len(edges) != 53 or required_edges - set(edges):
        raise ValueError("successor routes are incomplete or duplicated")
    group_ids = [group.get("id") for group in value["transition_groups"]]
    edge_ids = [edge.get("id") for edge in value["edges"]]
    if len(set(group_ids)) != len(group_ids) or len(set(edge_ids)) != len(edge_ids):
        raise ValueError("successor contains duplicate native group or edge IDs")
    groups_by_id = {
        group.get("id"): group for group in value["transition_groups"]
    }
    for key, mapping in SOURCE_92_NATIVE_IDS.items():
        edge = edges[key]
        group = groups_by_id.get(mapping["group_id"])
        if (
            edge.get("id") != mapping["edge_id"]
            or edge.get("transition_group_id") != mapping["group_id"]
            or group is None
            or group.get("transition_id") != key
        ):
            raise ValueError(f"source-92 native identity mapping differs: {key}")
    routed = {
        key for key, edge in edges.items()
        if any(parameter.get("key") == ENTRY_TRANSITION
               for parameter in edge.get("parameters", []))
    }
    if routed != set(ROUTED_TRANSITIONS):
        raise ValueError("successor changed the exact Script route-carrier set")
    for key, edge in edges.items():
        params = [parameter.get("key") for parameter in edge.get("parameters", [])]
        if len(params) != len(set(params)):
            raise ValueError(f"duplicate parameter on edge {key}")
        if key in ROUTED_TRANSITIONS and params[-1] != ENTRY_TRANSITION:
            raise ValueError(f"entry_transition is not final on {key}")
    target_keys = {
        node["id"]: node["key"] for node in value.get("nodes", [])
    }
    ci_failure = edges["ci_prepare_failed"]
    ci_retry = edges["ci_monitor_retry_preparation"]
    ci_blocked = edges["ci_monitor_preparation_needs_user_action"]
    if (
        target_keys.get(ci_failure.get("target_node_id")) != "ci_monitor"
        or tuple(p.get("key") for p in ci_failure.get("parameters", []))
        != CI_PREPARATION_FAILURE_FIELDS
        or ci_failure.get("requires_approval") is not False
    ):
        raise ValueError("typed CI preparation failure route differs from S4")
    if (
        target_keys.get(ci_retry.get("target_node_id")) != "ci_prepare"
        or tuple(p.get("key") for p in ci_retry.get("parameters", []))
        != CI_PREPARATION_RETRY_FIELDS
        or ci_retry.get("requires_approval") is not False
    ):
        raise ValueError("CI preparation retry route differs from S4")
    if (
        target_keys.get(ci_blocked.get("target_node_id")) != "ci_monitor"
        or tuple(p.get("key") for p in ci_blocked.get("parameters", []))
        != CI_PREPARATION_USER_ACTION_FIELDS
        or ci_blocked.get("requires_approval") is not True
        or ci_blocked.get("context_mode") != "compact_and_continue_session"
    ):
        raise ValueError("CI preparation user-action route differs from S4")
    for key in (
        "merge_watch_pr_merged", "publish_needs_user_action",
        "profile_generation_needs_user_action",
    ):
        if edges[key].get("requires_approval") is not True:
            raise ValueError(f"approval gate was weakened: {key}")
    if "target_commit" not in {
        parameter.get("key") for parameter in edges["merge_watch_pr_merged"]["parameters"]
    }:
        raise ValueError("merged PR route does not carry exact target T")
    if tuple(parameter.get("key") for parameter in edges["pr_feedback"]["parameters"]) != FULL_PR_FEEDBACK_FIELDS:
        raise ValueError("PR feedback does not carry full flat release/CI proof")
    janitor = next(node for node in value["nodes"] if node.get("key") == "task_janitor")
    if janitor.get("script_path") != JANITOR_SCRIPT:
        raise ValueError("Release Janitor does not use the project-owned adapter")
    if original is not None:
        original_native_ids = {
            identity
            for item in (*original["transition_groups"], *original["edges"])
            if isinstance(item, dict)
            for identity in (item.get("id"),)
            if isinstance(identity, str)
        }
        mapped_native_ids = {
            identity
            for mapping in SOURCE_92_NATIVE_IDS.values()
            for identity in mapping.values()
        }
        if original_native_ids & mapped_native_ids:
            raise ValueError("source-92 native IDs collide with historical identities")
        expected_groups = copy.deepcopy(original["transition_groups"])
        failure_group_id = next(
            edge["transition_group_id"] for edge in original["edges"]
            if edge["key"] == RENAMED_TRANSITIONS["ci_prepare_failed"]
        )
        failure_group = next(
            group for group in expected_groups if group["id"] == failure_group_id
        )
        failure_group["transition_id"] = "ci_prepare_failed"
        for key in (
            "ci_monitor_retry_preparation",
            "ci_monitor_preparation_needs_user_action",
        ):
            edge = edges[key]
            expected_groups.append(next(
                group for group in value["transition_groups"]
                if group["id"] == edge["transition_group_id"]
            ))
        if value["transition_groups"] != expected_groups:
            raise ValueError(
                "successor changed native groups beyond the S4 failure repair and additions"
            )
        old_nodes = {node["key"]: node for node in original["nodes"]}
        new_nodes = {node["key"]: node for node in value["nodes"]}
        if list(old_nodes) != list(new_nodes):
            raise ValueError("successor changed node order or key set")
        for key, before in old_nodes.items():
            after = copy.deepcopy(new_nodes[key])
            if key == "task_janitor":
                after["script_path"] = before["script_path"]
            if after != before:
                raise ValueError(f"successor changed node beyond Janitor path: {key}")
        old_edges = {edge["key"]: edge for edge in original["edges"]}
        for key, edge in edges.items():
            previous_key = RENAMED_TRANSITIONS.get(key, key)
            if previous_key not in old_edges:
                continue
            before = old_edges[previous_key]
            for field in (
                "id", "workflow_id", "transition_group_id", "target_node_id",
                "requires_approval", "context_mode", "context_source",
            ):
                if key == "ci_prepare_failed" and field in {
                    "target_node_id", "requires_approval", "context_mode",
                }:
                    continue
                if edge.get(field) != before.get(field):
                    raise ValueError(f"successor changed edge identity/gate/context: {key}:{field}")


def _build_spec_text() -> str:
    raw = BASE_SPEC.read_text(encoding="utf-8")
    old_path = 'path = ".kent/workflows/puber-release-kit-5af.manifest.json"'
    if raw.count(old_path) != 1:
        raise ValueError("historical source-manifest path is not unique")
    raw = raw.replace(
        old_path,
        'path = ".kent/workflows/puber-release-current.manifest.json"',
        1,
    )
    old_title = "# Private Kit5af-named proposal only: future qualified Kit K and source commit C remain unbound.\n"
    new_title = "# Private Release-only successor; qualified immutable Kit K and source commit C remain unbound.\n"
    if raw.count(old_title) != 1:
        raise ValueError("historical proposal title is not unique")
    raw = raw.replace(old_title, new_title, 1)
    old_revision = (
        "# Private source revision 91 preserves the Script-based preparation\n"
        "# sequence and approved route carriers with qualified Cleanup identity prompts."
    )
    new_revision = (
        "# Release-only source revision 92 preserves v90/5af history and approval\n"
        "# gates while repairing target-branch, operation, CI, and native outputs."
    )
    if raw.count(old_revision) != 1:
        raise ValueError("historical revision note is not unique")
    return raw.replace(old_revision, new_revision, 1)


def _derived_paths() -> set[str]:
    profile = tomllib.loads(PROFILE.read_text(encoding="utf-8"))
    spec = tomllib.loads(_build_spec_text())
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
    for key in ("spec_path", "builder_path", "snapshot_path"):
        add(profile.get("release", {}).get(key))
    for table in ("required_jobs_v1", "qualification_jobs_v1", "effect_jobs_v1"):
        for job in spec.get(table, {}).get("jobs", []):
            add(job.get("workflow_path"))
    for materialization in spec.get("approval_materializations", []):
        add(materialization.get("source_path"))
    add(spec.get("source_manifest", {}).get("path"))
    return result


def _expected_manifest() -> dict[str, Any]:
    base = _load_object(BASE_MANIFEST)
    old_sources = {
        item["key"].split("=", 1)[0]
        for item in base.get("external_roots", [])
        if item.get("kind") == "source-sha256"
        and isinstance(item.get("key"), str)
        and "=" in item["key"]
    }
    # Keep only Release source inputs; do not retain or regenerate Engineering outputs.
    old_sources = {
        path for path in old_sources
        if not ENGINEERING_SOURCE.search(path)
        and path != ".kent/workflows/builders/puber_release_kit_5af.py"
    }
    old_additional = {
        path for path in base.get("additional_paths", [])
        if isinstance(path, str) and not ENGINEERING_SOURCE.search(path)
    }
    derived = _derived_paths()
    sources = old_sources | old_additional | set(CLOSED_PATHS) | derived
    sources = {
        path for path in sources
        if not ENGINEERING_SOURCE.search(path)
        and path != str(MANIFEST.relative_to(ROOT))
        and path not in DYNAMIC_RELEASE_PATHS
    }
    builder_path = str(Path(__file__).resolve().relative_to(ROOT))
    source_roots = [
        {
            "kind": "source-sha256",
            "key": f"{path}={_sha(_safe_file(path))}",
            "runtime_digest_required": True,
        }
        # The required builder-sha256 descriptor already captures this exact
        # Git blob; do not charge the same physical file twice to the runtime
        # external-capture budget.
        for path in sorted(sources - {builder_path})
    ]
    roots = [{
        "kind": "builder-sha256",
        "key": _sha(_safe_file(builder_path)),
        "runtime_digest_required": True,
    }, *source_roots]
    roots.sort(key=lambda item: (item["kind"], item["key"]))
    additional = sorted(sources - derived - BINARY_SOURCE_ROOTS - DYNAMIC_RELEASE_PATHS)
    return {
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


def _write(path: Path, value: str) -> None:
    path.write_text(value, encoding="utf-8")
    path.chmod(0o644)


def write_outputs() -> None:
    _write(GRAPH, json.dumps(build_graph(), ensure_ascii=False, indent=2) + "\n")
    _write(SPEC, _build_spec_text())
    _write(MANIFEST, json.dumps(_expected_manifest(), ensure_ascii=False, indent=2) + "\n")


def write_graph_and_manifest() -> None:
    _write(GRAPH, json.dumps(build_graph(), ensure_ascii=False, indent=2) + "\n")
    _write(MANIFEST, json.dumps(_expected_manifest(), ensure_ascii=False, indent=2) + "\n")


def check() -> list[str]:
    errors: list[str] = []
    try:
        actual_graph = _load_object(GRAPH)
        if actual_graph != build_graph():
            errors.append("current Release graph differs from v97/source-92 proposal")
        validate_graph(actual_graph)
        if SPEC.read_text(encoding="utf-8") != _build_spec_text():
            errors.append("current Release spec differs from deterministic successor spec")
        spec = tomllib.loads(SPEC.read_text(encoding="utf-8"))
        if (
            spec.get("schema_version") != 2
            or spec.get("workflow_source_intent", {}).get("id") != WORKFLOW_ID
            or spec.get("runtime_attested") is not False
            or spec.get("source_manifest", {}).get("runtime_attested") is not False
            or spec.get("source_manifest", {}).get("path") != str(MANIFEST.relative_to(ROOT))
        ):
            errors.append("successor spec identity or non-attested state drifted")
        actual_manifest = _load_object(MANIFEST)
        if actual_manifest != _expected_manifest():
            errors.append("current Release source manifest differs from deterministic closure")
        if actual_manifest.get("runtime_attested") is not False:
            errors.append("successor manifest must remain non-attested")
        paths = set(actual_manifest.get("additional_paths", []))
        if paths & BINARY_SOURCE_ROOTS:
            errors.append("binary source roots must not be UTF-8 additional paths")
        if any(ENGINEERING_SOURCE.search(path) for path in paths):
            errors.append("Release-only manifest unexpectedly selects Engineering artifacts")
        if not any(
            item.get("kind") == "builder-sha256"
            and item.get("key") == _sha(Path(__file__))
            for item in actual_manifest.get("external_roots", [])
        ):
            errors.append("successor manifest does not bind its own builder")
        builder_path = str(Path(__file__).resolve().relative_to(ROOT))
        if any(
            item.get("kind") == "source-sha256"
            and isinstance(item.get("key"), str)
            and item["key"].split("=", 1)[0] == builder_path
            for item in actual_manifest.get("external_roots", [])
        ):
            errors.append("successor manifest redundantly captures its builder")
    except (OSError, ValueError, KeyError, TypeError, json.JSONDecodeError) as error:
        errors.append(str(error))
    return errors


def main() -> int:
    parser = argparse.ArgumentParser()
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--write", action="store_true")
    mode.add_argument("--write-graph-and-manifest", action="store_true")
    mode.add_argument("--check", action="store_true")
    args = parser.parse_args()
    if args.write:
        write_outputs()
        return 0
    if args.write_graph_and_manifest:
        write_graph_and_manifest()
        return 0
    errors = check()
    if errors:
        print("\n".join(f"puber-release-current: {error}" for error in errors), file=sys.stderr)
        return 1
    print("puber-release-current: deterministic non-attested v97/source-92 proposal passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
