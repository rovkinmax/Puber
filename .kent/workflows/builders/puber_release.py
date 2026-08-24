#!/usr/bin/env python3
"""Validate the tracked schema-4 Puber Release graph offline."""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import sys
import tomllib

WORKFLOW_ID = "10d8adb2-c74c-4ef0-8b5c-311cb5cd0459"
ROOT = Path(__file__).resolve().parents[3]
SPEC = ROOT / ".kent/workflows/specs/puber-release.toml"
MANIFEST = ROOT / ".kent/workflows/puber-release.manifest.json"


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", type=Path, required=True)
    args = parser.parse_args()
    try:
        graph_path = args.check.resolve()
        graph = json.loads(graph_path.read_text())
        spec = tomllib.loads(SPEC.read_text())
        manifest = json.loads(MANIFEST.read_text())
    except (OSError, json.JSONDecodeError, tomllib.TOMLDecodeError) as error:
        print(f"puber-release: {error}", file=sys.stderr)
        return 1

    workflow = graph.get("workflow", {})
    expected = {
        "nodes": int(spec["nodes"]),
        "transition_groups": int(spec["transition_groups"]),
        "edges": int(spec["edges"]),
    }
    actual = {
        "nodes": len(graph.get("nodes", [])),
        "transition_groups": len(graph.get("transition_groups", [])),
        "edges": len(graph.get("edges", [])),
    }
    checks = [
        (workflow.get("id") == WORKFLOW_ID, "workflow UUID is not canonical"),
        (workflow.get("name") == "Puber Release", "workflow name is not canonical"),
        (workflow.get("version") == 88, "tracked source revision must be 88"),
        (workflow.get("schema_version") == 4, "schema version must be 4"),
        (workflow.get("default") is False, "Puber Release must remain non-default"),
        (actual == expected, f"graph counts mismatch: expected {expected}, got {actual}"),
        (manifest.get("workflow", {}).get("counts") == expected, "manifest counts mismatch"),
        (manifest.get("no_live_apply") is True, "manifest must declare no_live_apply=true"),
    ]
    for passed, message in checks:
        if not passed:
            print(f"puber-release: {message}", file=sys.stderr)
            return 1

    nodes = {node.get("key"): node for node in graph.get("nodes", [])}
    for key, script_path in zip(spec["required_node_keys"], spec["required_script_paths"]):
        if nodes.get(key, {}).get("script_path") != script_path:
            print(f"puber-release: {key} is not bound to {script_path}", file=sys.stderr)
            return 1
    digest = hashlib.sha256(graph_path.read_bytes()).hexdigest()
    if manifest.get("graph_sha256") != digest:
        print("puber-release: manifest graph digest mismatch", file=sys.stderr)
        return 1
    print(json.dumps({"status": "passed", "revision": 88, "counts": actual}, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
