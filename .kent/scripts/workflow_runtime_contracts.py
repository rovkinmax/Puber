#!/usr/bin/env python3
"""Offline schema-4 operation-carrier helpers for the Puber release scripts."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path
import subprocess
import sys
from typing import Any, Mapping

RUNTIME_SCHEMA_VERSION = 4
WORKFLOW_ID = "10d8adb2-c74c-4ef0-8b5c-311cb5cd0459"


class ContractError(ValueError):
    pass


def read_object() -> dict[str, Any]:
    try:
        value = json.load(sys.stdin)
    except json.JSONDecodeError as error:
        raise ContractError(f"stdin must be one JSON object: {error}") from error
    if not isinstance(value, dict):
        raise ContractError("stdin must be one JSON object")
    return value


def required(payload: Mapping[str, Any], key: str, *, allow_empty: bool = False) -> str:
    value = payload.get(key)
    if not isinstance(value, str):
        raise ContractError(f"{key} must be a string")
    value = value.strip()
    if not allow_empty and not value:
        raise ContractError(f"{key} must be non-empty")
    return value


def operation(payload: Mapping[str, Any], node: str) -> dict[str, Any]:
    supplied = payload.get("operation")
    if supplied is not None and not isinstance(supplied, dict):
        raise ContractError("operation must be an object")
    result = dict(supplied or {})
    result.setdefault("workflow_id", WORKFLOW_ID)
    result.setdefault("node_key", node)
    result.setdefault("schema_version", RUNTIME_SCHEMA_VERSION)
    if result["workflow_id"] != WORKFLOW_ID or result["schema_version"] != RUNTIME_SCHEMA_VERSION:
        raise ContractError("operation authority is not the schema-4 Puber Release contract")
    if not isinstance(result.get("operation_id"), str) or not result["operation_id"].strip():
        canonical = {key: value for key, value in payload.items() if key != "operation"}
        encoded = json.dumps(canonical, sort_keys=True, separators=(",", ":")).encode()
        result["operation_id"] = hashlib.sha256(encoded).hexdigest()
    return result


def emit(transition: str, *, operation_data: dict[str, Any] | None = None, **fields: Any) -> None:
    result: dict[str, Any] = {"schema_version": RUNTIME_SCHEMA_VERSION, "transition": transition}
    if operation_data is not None:
        result["operation"] = operation_data
    result.update(fields)
    print(json.dumps(result, ensure_ascii=False, sort_keys=True))


def git(root: Path, *args: str, check: bool = True) -> str:
    result = subprocess.run(
        ["git", *args],
        cwd=root,
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        check=False,
    )
    if check and result.returncode != 0:
        raise ContractError(result.stderr.strip() or result.stdout.strip() or "git command failed")
    return result.stdout.strip()


def repository_root(workspace: str) -> Path:
    path = Path(workspace).expanduser().resolve()
    if not path.is_dir():
        raise ContractError(f"workspace does not exist: {path}")
    root = Path(git(path, "rev-parse", "--show-toplevel"))
    if root != path:
        raise ContractError(f"workspace must be repository root: {path}")
    return root
