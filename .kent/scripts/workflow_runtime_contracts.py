"""Pure, closed runtime contracts shared by the Kit and project adapters.

This module intentionally has no package-relative imports and no filesystem,
Git, Kent, network, clock, randomness, journal, or write effects.  It is also
the source copied to a project's sibling runtime support module.
"""

import base64
from dataclasses import dataclass
import hashlib
import json
import math
import re
from types import MappingProxyType
from typing import Any, Iterable, Mapping, Sequence


class RuntimeContractError(ValueError):
    """Raised when a runtime contract is malformed or inconsistent."""


RuntimeValidationError = RuntimeContractError


class _FrozenList(tuple):
    """Private marker preserving list semantics during materialization."""


def _freeze_classification_value(value: Any) -> Any:
    if isinstance(value, Mapping):
        return MappingProxyType(
            {
                key: _freeze_classification_value(item)
                for key, item in value.items()
            }
        )
    if isinstance(value, list):
        return _FrozenList(
            _freeze_classification_value(item) for item in value
        )
    if isinstance(value, tuple):
        return tuple(_freeze_classification_value(item) for item in value)
    return value


def _thaw_classification_value(value: Any) -> Any:
    if isinstance(value, Mapping):
        return {
            key: _thaw_classification_value(item)
            for key, item in value.items()
        }
    if isinstance(value, _FrozenList):
        return [_thaw_classification_value(item) for item in value]
    if isinstance(value, tuple):
        return tuple(_thaw_classification_value(item) for item in value)
    return value


MAX_CAPTURE_STDIN_BYTES = 6 * 1024 * 1024
MAX_EXTERNAL_ROOT_BYTES = 1024 * 1024
MAX_EXTERNAL_TOTAL_BYTES = 4 * 1024 * 1024
MAX_SOURCE_PREVIEW_BYTES = 4 * 1024 * 1024
MAX_EXPECTED_CHECKS = 100
MAX_EXPECTED_CHECKS_BYTES = 32 * 1024
MAX_FEEDBACK_ITEMS = 100
MAX_FEEDBACK_HARD_LIMIT = 1000
MAX_FEEDBACK_BYTES = 64 * 1024
MAX_CI_EXPECTED_OBSERVATIONS = 100
MAX_CI_UNEXPECTED_OBSERVATIONS = 10000
MAX_CI_ATTEMPTS = 8
MAX_CI_REPORT_BYTES = 64 * 1024
MAX_CI_ATTEMPT_BYTES = 48 * 1024
MAX_OBSERVATION_CANONICAL_BYTES = 4 * 1024 * 1024
MAX_CANONICAL_JSON_NESTING = 100

SOURCE_ENVELOPE_SCHEMA = "runtime-source-envelope-v1"
SELECTED_INPUTS_SCHEMA = "selected-runtime-source-inputs-v1"
EXTERNAL_CAPTURE_SCHEMA = "runtime-external-captures-v1"
TERMINAL_SEAL_REQUEST_SCHEMA = "terminal-evidence-seal-request-v1"
TERMINAL_SEAL_SCHEMA = "terminal_evidence_seal_v1"
TERMINAL_MARKER_SCHEMA = "terminal_evidence_v1"
VERIFICATION_REPORT_SCHEMA = "workflow-verification-report-v2"
PR_CURSOR_SCHEMA = "github-pr-feedback-cursor-v1"
EXPECTED_CHECKS_SCHEMA = "github-ci-expected-checks-v1"
CI_REPORT_SCHEMA = "github-ci-report-v2"


@dataclass(frozen=True)
class RejectedObservationReceipt:
    source: str
    count: int
    sha256: str

    def __post_init__(self) -> None:
        if self.source not in {"projected_rows", "unexpected_rows"}:
            raise RuntimeContractError("unsupported rejected observation source")
        if not isinstance(self.count, int) or isinstance(self.count, bool):
            raise RuntimeContractError("rejected observation count must be an integer")
        if not 0 <= self.count <= 2147483647:
            raise RuntimeContractError("rejected observation count is out of range")
        if not isinstance(self.sha256, str) or not SHA256_RE.fullmatch(self.sha256):
            raise RuntimeContractError("rejected observation digest is invalid")


@dataclass(frozen=True)
class RejectedObservationHardLimit:
    source: str
    prefix_sha256: str

    def __post_init__(self) -> None:
        if self.source not in {"projected_rows", "unexpected_rows"}:
            raise RuntimeContractError("unsupported rejected observation source")
        if not isinstance(self.prefix_sha256, str) or not SHA256_RE.fullmatch(
            self.prefix_sha256
        ):
            raise RuntimeContractError("rejected observation prefix is invalid")


@dataclass(frozen=True)
class ExpectedCiClassification:
    state: str
    value: Mapping[str, Any] | None
    projected_observations: (
        RejectedObservationReceipt | RejectedObservationHardLimit | None
    )
    unexpected_observations: (
        RejectedObservationReceipt | RejectedObservationHardLimit | None
    )
    grammar_error: RuntimeContractError | None = None

    def __post_init__(self) -> None:
        if self.state not in {
            "ordinary",
            "observation_limit",
            "grammar_invalid",
            "hard_limit",
        }:
            raise RuntimeContractError("unsupported expected CI classification state")
        if self.state in {"ordinary", "observation_limit"}:
            if not isinstance(self.value, Mapping):
                raise RuntimeContractError(
                    "expected CI classification value must be a mapping"
                )
            object.__setattr__(
                self,
                "value",
                _freeze_classification_value(self.value),
            )
        elif self.value is not None:
            raise RuntimeContractError(
                "bounded expected CI classifications must not carry a value"
            )
        if self.state == "grammar_invalid":
            if not isinstance(self.grammar_error, RuntimeContractError):
                raise RuntimeContractError(
                    "grammar-invalid classifications require a grammar error"
                )
            if not isinstance(
                self.projected_observations,
                RejectedObservationReceipt,
            ) or self.projected_observations.source != "projected_rows" or (
                self.unexpected_observations is not None
            ):
                raise RuntimeContractError(
                    "grammar-invalid classifications require projected observations"
                )
        elif self.state == "observation_limit":
            if not isinstance(
                self.projected_observations,
                RejectedObservationReceipt,
            ) or not isinstance(
                self.unexpected_observations,
                RejectedObservationReceipt,
            ) or self.projected_observations.source != "projected_rows" or (
                self.unexpected_observations.source != "unexpected_rows"
            ):
                raise RuntimeContractError(
                    "observation-limit classifications require both receipts"
                )
            if self.grammar_error is not None:
                raise RuntimeContractError(
                    "observation-limit classifications must not carry a grammar error"
                )
        elif self.state == "hard_limit":
            projected_hard = isinstance(
                self.projected_observations,
                RejectedObservationHardLimit,
            )
            unexpected_hard = isinstance(
                self.unexpected_observations,
                RejectedObservationHardLimit,
            )
            projected_receipt = isinstance(
                self.projected_observations,
                RejectedObservationReceipt,
            )
            valid_projected_hard = (
                projected_hard
                and self.projected_observations.source == "projected_rows"
                and self.unexpected_observations is None
            )
            valid_unexpected_hard = (
                projected_receipt
                and self.projected_observations.source == "projected_rows"
                and unexpected_hard
                and self.unexpected_observations.source == "unexpected_rows"
            )
            if not (valid_projected_hard or valid_unexpected_hard):
                raise RuntimeContractError(
                    "hard-limit classification has an unreachable receipt shape"
                )
            if self.grammar_error is not None:
                raise RuntimeContractError(
                    "hard-limit classifications must not carry a grammar error"
                )
        else:
            if not isinstance(
                self.projected_observations,
                (RejectedObservationReceipt, type(None)),
            ) or (
                isinstance(
                    self.projected_observations,
                    RejectedObservationReceipt,
                )
                and self.projected_observations.source != "projected_rows"
            ) or self.unexpected_observations is not None:
                raise RuntimeContractError(
                    "ordinary classifications require only projected observations"
                )
            if self.grammar_error is not None:
                raise RuntimeContractError(
                    "ordinary classifications must not carry a grammar error"
                )

    def materialize_value(self) -> dict[str, Any]:
        if self.value is None:
            raise RuntimeContractError("classification has no ordinary value")
        materialized = _thaw_classification_value(self.value)
        if not isinstance(materialized, dict):
            raise RuntimeContractError(
                "classification value did not materialize as a mapping"
            )
        return materialized


class CiAttemptSizeLimit(RuntimeContractError):
    """Raised only when a canonical CI attempt exceeds its wire limit."""

SHA256_RE = re.compile(r"^[0-9a-f]{64}$")
SHA1_RE = re.compile(r"^[0-9a-f]{40}$")
TASK_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
REPOSITORY_RE = re.compile(r"^[^/\s]{1,100}/[^/\s]{1,100}$")
UPPER_TOKEN_RE = re.compile(r"^[A-Z_]{1,64}$")
IDENTIFIER_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]*$")
LOG_PATH_RE = re.compile(
    r"^build/kent-workflow/verification-report-[0-9a-f]{64}\.log$"
)
LINK_RE = re.compile(
    r"^https://(?:github\.com|www\.github\.com)(?:/|$)"
)
RFC3339_RE = re.compile(
    r"^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}"
    r"(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})$"
)


def canonical_bytes(value: Any) -> bytes:
    """Return compact, UTF-8, sorted-key JSON bytes without NaN values."""

    try:
        return b"".join(_canonical_byte_chunks(value))
    except RuntimeContractError:
        raise
    except RecursionError as error:
        raise RuntimeContractError(
            "canonical JSON nesting exceeds its limit"
        ) from error
    except (TypeError, ValueError, UnicodeEncodeError) as error:
        raise RuntimeContractError(
            f"value is not canonical JSON: {error}"
        ) from error


canonical_json_bytes = canonical_bytes


def sha256_bytes(value: bytes) -> str:
    if not isinstance(value, bytes):
        raise RuntimeContractError("digest input must be bytes")
    return hashlib.sha256(value).hexdigest()


def canonical_sha256(value: Any) -> str:
    return sha256_bytes(canonical_bytes(value))


_CANONICAL_TEXT_FRAGMENT_CHARS = 1024
_CANONICAL_ESCAPES = {
    '"': '\\"',
    "\\": "\\\\",
    "\b": "\\b",
    "\f": "\\f",
    "\n": "\\n",
    "\r": "\\r",
    "\t": "\\t",
}


def _iter_canonical_string(value: str) -> Iterable[str]:
    yield '"'
    fragment: list[str] = []
    fragment_length = 0
    for index, character in enumerate(value):
        codepoint = ord(character)
        if 0xD800 <= codepoint <= 0xDFFF:
            raise UnicodeEncodeError(
                "utf-8",
                value,
                index,
                index + 1,
                "surrogates not allowed",
            )
        escaped = _CANONICAL_ESCAPES.get(character)
        if escaped is None:
            escaped = (
                "\\u{:04x}".format(codepoint)
                if codepoint < 0x20
                else character
            )
        if (
            fragment
            and fragment_length + len(escaped)
            > _CANONICAL_TEXT_FRAGMENT_CHARS
        ):
            yield "".join(fragment)
            fragment = []
            fragment_length = 0
        fragment.append(escaped)
        fragment_length += len(escaped)
    if fragment:
        yield "".join(fragment)
    yield '"'


def _canonical_object_key(value: Any) -> str:
    if isinstance(value, str):
        return value
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, int):
        return str(value)
    if isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("out-of-range float values are not JSON compliant")
        return json.dumps(
            value,
            ensure_ascii=False,
            separators=(",", ":"),
            allow_nan=False,
        )
    raise TypeError(
        "keys must be str, int, float, bool or None, not {}".format(
            type(value).__name__
        )
    )


def _iter_canonical_fragments(
    value: Any,
    active: set[int] | None = None,
    nesting: int = 0,
) -> Iterable[str]:
    if active is None:
        active = set()
    if value is None:
        yield "null"
    elif value is True:
        yield "true"
    elif value is False:
        yield "false"
    elif isinstance(value, int):
        yield str(value)
    elif isinstance(value, float):
        if not math.isfinite(value):
            raise ValueError("out-of-range float values are not JSON compliant")
        yield json.dumps(
            value,
            ensure_ascii=False,
            separators=(",", ":"),
            allow_nan=False,
        )
    elif isinstance(value, str):
        yield from _iter_canonical_string(value)
    elif isinstance(value, (list, tuple)):
        next_nesting = nesting + 1
        if next_nesting > MAX_CANONICAL_JSON_NESTING:
            raise RuntimeContractError(
                "canonical JSON nesting exceeds its limit"
            )
        identity = id(value)
        if identity in active:
            raise ValueError("Circular reference detected")
        active.add(identity)
        try:
            yield "["
            for index, item in enumerate(value):
                if index:
                    yield ","
                yield from _iter_canonical_fragments(
                    item,
                    active,
                    next_nesting,
                )
            yield "]"
        finally:
            active.remove(identity)
    elif isinstance(value, dict):
        next_nesting = nesting + 1
        if next_nesting > MAX_CANONICAL_JSON_NESTING:
            raise RuntimeContractError(
                "canonical JSON nesting exceeds its limit"
            )
        identity = id(value)
        if identity in active:
            raise ValueError("Circular reference detected")
        active.add(identity)
        try:
            try:
                keys = sorted(value)
            except TypeError:
                raise TypeError("keys are not mutually comparable") from None
            yield "{"
            for index, key in enumerate(keys):
                if index:
                    yield ","
                yield from _iter_canonical_string(_canonical_object_key(key))
                yield ":"
                yield from _iter_canonical_fragments(
                    value[key],
                    active,
                    next_nesting,
                )
            yield "}"
        finally:
            active.remove(identity)
    else:
        raise TypeError(
            "Object of type {} is not JSON serializable".format(
                type(value).__name__
            )
        )


def _canonical_byte_chunks(value: Any) -> Iterable[bytes]:
    for fragment in _iter_canonical_fragments(value):
        for offset in range(0, len(fragment), _CANONICAL_TEXT_FRAGMENT_CHARS):
            piece = fragment[offset : offset + _CANONICAL_TEXT_FRAGMENT_CHARS]
            yield piece.encode("utf-8")


def _bounded_canonical_observation(
    rows: Sequence[Mapping[str, Any]],
    source: str,
) -> RejectedObservationReceipt | RejectedObservationHardLimit:
    if source not in {"projected_rows", "unexpected_rows"}:
        raise RuntimeContractError("unsupported observation receipt source")
    digest = hashlib.sha256()
    retained = 0
    limit = MAX_OBSERVATION_CANONICAL_BYTES + 1
    try:
        for encoded in _canonical_byte_chunks(rows):
            remaining = limit - retained
            prefix = encoded[:remaining]
            if prefix:
                digest.update(prefix)
                retained += len(prefix)
            if retained == limit:
                return RejectedObservationHardLimit(source, digest.hexdigest())
    except RecursionError as error:
        raise RuntimeContractError(
            "canonical JSON nesting exceeds its limit"
        ) from error
    except (TypeError, ValueError, UnicodeEncodeError) as error:
        raise RuntimeContractError(
            f"observations are not canonical JSON: {error}"
        ) from error
    return RejectedObservationReceipt(source, len(rows), digest.hexdigest())


def parse_canonical_json(
    raw: bytes | str,
    *,
    label: str = "JSON value",
    max_bytes: int | None = None,
) -> Any:
    if isinstance(raw, str):
        raw_bytes = raw.encode("utf-8")
    elif isinstance(raw, bytes):
        raw_bytes = raw
    else:
        raise RuntimeContractError(f"{label} must be bytes or text")
    if max_bytes is not None and len(raw_bytes) > max_bytes:
        raise RuntimeContractError(f"{label} exceeds its byte limit")
    try:
        value = json.loads(
            raw_bytes.decode("utf-8"),
            parse_constant=lambda constant: (
                (_ for _ in ()).throw(
                    RuntimeContractError(
                        f"{label} contains invalid JSON constant {constant}"
                    )
                )
            ),
        )
    except RuntimeContractError:
        raise
    except RecursionError as error:
        raise RuntimeContractError(
            f"{label} exceeds the canonical JSON nesting limit"
        ) from error
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise RuntimeContractError(f"{label} is invalid JSON: {error}") from error
    try:
        encoded = canonical_bytes(value)
    except RecursionError as error:
        raise RuntimeContractError(
            f"{label} exceeds the canonical JSON nesting limit"
        ) from error
    except RuntimeContractError as error:
        if str(error) == "canonical JSON nesting exceeds its limit":
            raise RuntimeContractError(
                f"{label} exceeds the canonical JSON nesting limit"
            ) from error
        raise
    if encoded != raw_bytes:
        raise RuntimeContractError(f"{label} is not canonical JSON")
    return value


canonicalize_json = canonical_bytes


def _object(value: Any, label: str) -> dict[str, Any]:
    if not isinstance(value, Mapping):
        raise RuntimeContractError(f"{label} must be an object")
    return dict(value)


def _closed(value: Any, keys: set[str], label: str) -> dict[str, Any]:
    result = _object(value, label)
    unknown = sorted(set(result) - keys)
    if unknown:
        raise RuntimeContractError(f"{label} has unknown fields: {unknown}")
    return result


def _required(value: Mapping[str, Any], key: str, label: str) -> Any:
    if key not in value:
        raise RuntimeContractError(f"{label} is missing {key!r}")
    return value[key]


def _string(
    value: Any,
    label: str,
    *,
    nonempty: bool = True,
    max_bytes: int | None = None,
) -> str:
    if not isinstance(value, str):
        raise RuntimeContractError(f"{label} must be a string")
    if nonempty and not value:
        raise RuntimeContractError(f"{label} must be non-empty")
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        raise RuntimeContractError(f"{label} contains a control character")
    encoded_length = len(value.encode("utf-8"))
    if max_bytes is not None and encoded_length > max_bytes:
        raise RuntimeContractError(
            f"{label} exceeds the {max_bytes}-byte limit"
        )
    return value


def _integer(value: Any, label: str, *, minimum: int = 0) -> int:
    if not isinstance(value, int) or isinstance(value, bool) or value < minimum:
        raise RuntimeContractError(
            f"{label} must be an integer >= {minimum}"
        )
    return value


def _boolean(value: Any, label: str) -> bool:
    if not isinstance(value, bool):
        raise RuntimeContractError(f"{label} must be a boolean")
    return value


def _digest(value: Any, label: str) -> str:
    value = _string(value, label, max_bytes=64)
    if not SHA256_RE.fullmatch(value):
        raise RuntimeContractError(f"{label} must be lowercase 64-hex")
    return value


def _commit(value: Any, label: str, *, nullable: bool = False) -> str | None:
    if value is None and nullable:
        return None
    value = _string(value, label, max_bytes=40)
    if not SHA1_RE.fullmatch(value):
        raise RuntimeContractError(f"{label} must be lowercase 40-hex")
    return value


def _sorted_unique_strings(
    value: Any,
    label: str,
    *,
    max_items: int | None = None,
) -> tuple[str, ...]:
    if not isinstance(value, list):
        raise RuntimeContractError(f"{label} must be an array")
    if max_items is not None and len(value) > max_items:
        raise RuntimeContractError(f"{label} exceeds its item limit")
    result = tuple(_string(item, f"{label}[]") for item in value)
    if result != tuple(sorted(set(result))):
        raise RuntimeContractError(f"{label} must be sorted and unique")
    return result


@dataclass(frozen=True)
class RuntimeExternalRoot:
    kind: str
    key: str
    runtime_digest_required: bool = True

    def __post_init__(self) -> None:
        _string(self.kind, "external_root.kind")
        _string(self.key, "external_root.key")
        if not self.runtime_digest_required:
            raise RuntimeContractError(
                "external_root.runtime_digest_required must be true"
            )

    @classmethod
    def from_mapping(cls, value: Mapping[str, Any]) -> "RuntimeExternalRoot":
        data = _closed(
            value,
            {"kind", "key", "runtime_digest_required"},
            "external_root",
        )
        if set(data) != {"kind", "key", "runtime_digest_required"}:
            raise RuntimeContractError(
                "external_root has an incomplete field set"
            )
        return cls(
            kind=_string(_required(data, "kind", "external_root"), "kind"),
            key=_string(_required(data, "key", "external_root"), "key"),
            runtime_digest_required=_boolean(
                _required(data, "runtime_digest_required", "external_root"),
                "runtime_digest_required",
            ),
        )

    def as_dict(self) -> dict[str, Any]:
        return {
            "kind": self.kind,
            "key": self.key,
            "runtime_digest_required": self.runtime_digest_required,
        }


_RUNTIME_PROOF_TOKEN = object()


@dataclass(frozen=True, init=False)
class SelectedRuntimeSourceInputs:
    """A selected-revision source bundle carrying a private in-process proof."""

    project_name: str
    repository: str
    topology_kind: str
    project_commit: str
    source_preview_bytes: bytes
    source_preview_sha256: str
    artifact_digests: Mapping[str, str]
    external_roots: tuple[RuntimeExternalRoot, ...]
    _proof: object

    def __init__(
        self,
        project_name: str,
        repository: str,
        topology_kind: str,
        project_commit: str,
        source_preview_bytes: bytes,
        source_preview_sha256: str,
        artifact_digests: Mapping[str, str],
        external_roots: Sequence[RuntimeExternalRoot],
        *,
        _proof: object | None = None,
    ) -> None:
        if _proof is not _RUNTIME_PROOF_TOKEN:
            raise RuntimeContractError(
                "SelectedRuntimeSourceInputs may only be created by "
                "selected-revision preflight"
            )
        project_name = _string(project_name, "project_name")
        repository = _string(repository, "repository")
        if not REPOSITORY_RE.fullmatch(repository):
            raise RuntimeContractError("repository has an invalid shape")
        topology_kind = _string(topology_kind, "topology_kind")
        project_commit = _commit(project_commit, "project_commit")
        if not isinstance(source_preview_bytes, bytes):
            raise RuntimeContractError("source_preview_bytes must be bytes")
        if len(source_preview_bytes) > MAX_SOURCE_PREVIEW_BYTES:
            raise RuntimeContractError("source preview exceeds its byte limit")
        if _digest(source_preview_sha256, "source_preview_sha256") != (
            sha256_bytes(source_preview_bytes)
        ):
            raise RuntimeContractError(
                "source_preview_sha256 does not match source_preview_bytes"
            )
        digests = dict(artifact_digests)
        required = {
            "spec_raw_blob_sha256",
            "source_manifest_raw_blob_sha256",
            "snapshot_raw_blob_sha256",
        }
        allowed = required | {"builder_raw_blob_sha256"}
        if set(digests) - allowed or not required <= set(digests):
            raise RuntimeContractError(
                "artifact_digests has an invalid field set"
            )
        for key, value in digests.items():
            _digest(value, f"artifact_digests.{key}")
        roots = tuple(external_roots)
        if any(not isinstance(root, RuntimeExternalRoot) for root in roots):
            raise RuntimeContractError("external_roots are not typed descriptors")
        root_keys = [(root.kind, root.key) for root in roots]
        if root_keys != sorted(root_keys) or len(set(root_keys)) != len(root_keys):
            raise RuntimeContractError(
                "external_roots must be sorted and unique"
            )
        object.__setattr__(self, "project_name", project_name)
        object.__setattr__(self, "repository", repository)
        object.__setattr__(self, "topology_kind", topology_kind)
        object.__setattr__(self, "project_commit", project_commit)
        object.__setattr__(self, "source_preview_bytes", source_preview_bytes)
        object.__setattr__(self, "source_preview_sha256", source_preview_sha256)
        object.__setattr__(
            self,
            "artifact_digests",
            MappingProxyType(dict(sorted(digests.items()))),
        )
        object.__setattr__(self, "external_roots", roots)
        object.__setattr__(self, "_proof", _proof)

    def _core_dict(self) -> dict[str, Any]:
        return {
            "schema": SELECTED_INPUTS_SCHEMA,
            "project_name": self.project_name,
            "repository": self.repository,
            "topology_kind": self.topology_kind,
            "project_commit": self.project_commit,
            "source_preview_sha256": self.source_preview_sha256,
            "artifact_digests": dict(self.artifact_digests),
            "external_roots": [
                root.as_dict() for root in self.external_roots
            ],
        }

    @property
    def selected_runtime_source_inputs_sha256(self) -> str:
        return canonical_sha256(self._core_dict())

    @property
    def commit_oid(self) -> str:
        return self.project_commit

    def as_dict(self) -> dict[str, Any]:
        result = self._core_dict()
        result["selected_runtime_source_inputs_sha256"] = (
            self.selected_runtime_source_inputs_sha256
        )
        return result

    def __json__(self) -> dict[str, Any]:
        return self.as_dict()


def _make_selected_runtime_source_inputs(
    *,
    project_name: str,
    repository: str,
    topology_kind: str,
    project_commit: str,
    source_preview: Any,
    artifact_digests: Mapping[str, str],
    external_roots: Sequence[RuntimeExternalRoot],
) -> SelectedRuntimeSourceInputs:
    preview_bytes = canonical_bytes(source_preview)
    return SelectedRuntimeSourceInputs(
        project_name,
        repository,
        topology_kind,
        project_commit,
        preview_bytes,
        sha256_bytes(preview_bytes),
        artifact_digests,
        external_roots,
        _proof=_RUNTIME_PROOF_TOKEN,
    )


def _require_proven_inputs(
    value: Any,
) -> SelectedRuntimeSourceInputs:
    if not isinstance(value, SelectedRuntimeSourceInputs):
        raise RuntimeContractError(
            "capture requires SelectedRuntimeSourceInputs from this module"
        )
    if value._proof is not _RUNTIME_PROOF_TOKEN:
        raise RuntimeContractError("runtime source proof is invalid")
    return value


_SEALED_RUNTIME_PROOF = object()
_RUNTIME_SENTINELS = {
    "runtime",
    "dynamic",
    "current",
    "auto",
    "any",
    "unknown",
    "unset",
    "null",
    "none",
    "*",
    "-",
    "0",
    "$runtime",
    "${runtime}",
    "<runtime>",
}
_TASK_UUID_RE = re.compile(
    r"^task-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
    r"[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$"
)
_TASK_SHORT_ID_RE = re.compile(r"^[A-Z][A-Z0-9]+-[0-9]+$")
_AUTHORITY_KEY_RE = re.compile(r"^[a-z][a-z0-9_-]*$")
_SUPPORTED_GITHUB_EVENTS = {
    "branch_protection_rule",
    "check_run",
    "check_suite",
    "create",
    "delete",
    "deployment",
    "deployment_status",
    "discussion",
    "discussion_comment",
    "fork",
    "gollum",
    "issue_comment",
    "issues",
    "label",
    "merge_group",
    "milestone",
    "page_build",
    "project",
    "project_card",
    "project_column",
    "public",
    "pull_request",
    "pull_request_review",
    "pull_request_review_comment",
    "pull_request_target",
    "push",
    "release",
    "repository_dispatch",
    "schedule",
    "status",
    "watch",
    "workflow_call",
    "workflow_dispatch",
    "workflow_run",
}


def _policy_string(value: Any, label: str) -> str:
    value = _string(value, label)
    if value != value.strip():
        raise RuntimeContractError(f"{label} must be normalized")
    if any(ord(character) < 32 or ord(character) == 127 for character in value):
        raise RuntimeContractError(f"{label} contains a control character")
    lowered = value.casefold()
    if (
        "{" in value
        or "}" in value
        or value.startswith("$")
        or (value.startswith("<") and value.endswith(">"))
        or lowered in _RUNTIME_SENTINELS
    ):
        raise RuntimeContractError(f"{label} contains a runtime placeholder")
    return value


def _runtime_path(value: Any, label: str) -> str:
    value = _policy_string(value, label)
    if value.startswith("/") or "\\" in value:
        raise RuntimeContractError(f"{label} must be a project-relative path")
    parts = value.split("/")
    if any(part in {"", ".", ".."} for part in parts):
        raise RuntimeContractError(f"{label} is not normalized")
    return value


def _runtime_ref(value: Any, label: str) -> str:
    value = _policy_string(value, label)
    if not value.startswith(("refs/heads/", "refs/tags/")) or value.endswith("/"):
        raise RuntimeContractError(f"{label} is not a normalized Git ref")
    if any(character.isspace() for character in value):
        raise RuntimeContractError(f"{label} must not contain whitespace")
    if any(token in value for token in ("..", "@{", "//", "\\", "~", "^", ":", "?", "*", "[")):
        raise RuntimeContractError(f"{label} contains forbidden ref syntax")
    for component in value.split("/")[2:]:
        if (
            not component
            or component in {".", ".."}
            or component.startswith(".")
            or component.endswith(".")
            or component.endswith(".lock")
        ):
            raise RuntimeContractError(f"{label} contains an invalid component")
    return value


def _sealed_new(cls: type[Any], values: Mapping[str, Any]) -> Any:
    instance = object.__new__(cls)
    for key, value in values.items():
        object.__setattr__(instance, key, value)
    return instance


@dataclass(frozen=True, init=False)
class RuntimeExecutionContext:
    kind: str
    task_id: str | None
    task_short_id: str | None
    workflow_id: str | None
    workflow_revision: int | None
    project_id: str | None
    project_commit: str
    authority_transition: str | None
    repository: str | None
    workflow_path: str | None
    workflow_name: str | None
    event: str | None
    run_id: int | None
    attempt: int | None
    head_sha: str | None
    ref: str | None
    selected_runtime_source_inputs_sha256: str
    execution_context_sha256: str
    _proof: object

    def __init__(self, *args: Any, **kwargs: Any) -> None:
        raise TypeError("RuntimeExecutionContext is sealed")

    def __repr__(self) -> str:
        return (
            "RuntimeExecutionContext("
            f"kind={self.kind!r}, "
            f"selected_runtime_source_inputs_sha256="
            f"{self.selected_runtime_source_inputs_sha256!r})"
        )


@dataclass(frozen=True, init=False)
class RuntimeAuthorityBinding:
    authority: Mapping[str, Any]
    repository: str
    project_commit: str
    selected_runtime_source_inputs_sha256: str
    execution_context_sha256: str
    runtime_source_envelope: Mapping[str, Any]
    runtime_source_envelope_digest: str
    provenance_fingerprint: str
    _proof: object

    def __init__(self, *args: Any, **kwargs: Any) -> None:
        raise TypeError("RuntimeAuthorityBinding is sealed")

    def __repr__(self) -> str:
        return (
            "RuntimeAuthorityBinding("
            f"repository={self.repository!r}, "
            f"project_commit={self.project_commit!r})"
        )


def _context_core(context: RuntimeExecutionContext) -> dict[str, Any]:
    return {
        "kind": context.kind,
        "task_id": context.task_id,
        "task_short_id": context.task_short_id,
        "workflow_id": context.workflow_id,
        "workflow_revision": context.workflow_revision,
        "project_id": context.project_id,
        "project_commit": context.project_commit,
        "authority_transition": context.authority_transition,
        "repository": context.repository,
        "workflow_path": context.workflow_path,
        "workflow_name": context.workflow_name,
        "event": context.event,
        "run_id": context.run_id,
        "attempt": context.attempt,
        "head_sha": context.head_sha,
        "ref": context.ref,
        "selected_runtime_source_inputs_sha256": (
            context.selected_runtime_source_inputs_sha256
        ),
    }


def _require_context(
    value: Any,
    inputs: SelectedRuntimeSourceInputs,
) -> RuntimeExecutionContext:
    inputs = _require_proven_inputs(inputs)
    if not isinstance(value, RuntimeExecutionContext):
        raise RuntimeContractError("runtime execution context is not sealed")
    if any(
        not hasattr(value, field)
        for field in ("_proof", "selected_runtime_source_inputs_sha256")
    ):
        raise RuntimeContractError("runtime execution context is incomplete")
    if value._proof is not _SEALED_RUNTIME_PROOF:
        raise RuntimeContractError("runtime execution context proof is invalid")
    if value.selected_runtime_source_inputs_sha256 != (
        inputs.selected_runtime_source_inputs_sha256
    ):
        raise RuntimeContractError("runtime execution context uses another source")
    if value.execution_context_sha256 != canonical_sha256(_context_core(value)):
        raise RuntimeContractError("runtime execution context fingerprint is stale")
    return value


def capture_runtime_execution_context(
    inputs: SelectedRuntimeSourceInputs,
    current_execution: Mapping[str, Any],
) -> RuntimeExecutionContext:
    inputs = _require_proven_inputs(inputs)
    data = dict(current_execution) if isinstance(current_execution, Mapping) else None
    if data is None:
        raise RuntimeContractError("current execution must be a mapping")
    kind = data.get("kind")
    if kind == "kent_transition":
        if set(data) != {
            "kind", "task_id", "task_short_id", "workflow_id",
            "workflow_revision", "project_id", "project_commit",
            "authority_transition",
        }:
            raise RuntimeContractError("Kent execution context has invalid fields")
        task_id = _string(data["task_id"], "task_id")
        if not _TASK_UUID_RE.fullmatch(task_id):
            raise RuntimeContractError("task_id must be task-<UUID>")
        task_short_id = _string(data["task_short_id"], "task_short_id")
        if not _TASK_SHORT_ID_RE.fullmatch(task_short_id):
            raise RuntimeContractError("task_short_id has invalid grammar")
        workflow_id = _string(data["workflow_id"], "workflow_id")
        if not re.fullmatch(
            r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
            r"[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}",
            workflow_id,
        ):
            raise RuntimeContractError("workflow_id must be a UUID")
        revision = data["workflow_revision"]
        if not isinstance(revision, int) or isinstance(revision, bool) or revision <= 0:
            raise RuntimeContractError("workflow_revision must be positive")
        project_id = _string(data["project_id"], "project_id")
        if not re.fullmatch(
            r"project-[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
            r"[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}",
            project_id,
        ):
            raise RuntimeContractError("project_id must be project-<UUID>")
        commit = _commit(data["project_commit"], "project_commit")
        if commit != inputs.project_commit:
            raise RuntimeContractError("Kent context commit does not match selected source")
        transition = _policy_string(data["authority_transition"], "authority_transition")
        if not _AUTHORITY_KEY_RE.fullmatch(transition):
            raise RuntimeContractError("authority_transition is not normalized")
        values = {
            "kind": kind,
            "task_id": task_id,
            "task_short_id": task_short_id,
            "workflow_id": workflow_id,
            "workflow_revision": revision,
            "project_id": project_id,
            "project_commit": commit,
            "authority_transition": transition,
            "repository": None,
            "workflow_path": None,
            "workflow_name": None,
            "event": None,
            "run_id": None,
            "attempt": None,
            "head_sha": None,
            "ref": None,
            "selected_runtime_source_inputs_sha256": (
                inputs.selected_runtime_source_inputs_sha256
            ),
            "_proof": _SEALED_RUNTIME_PROOF,
        }
    elif kind == "github_run":
        if set(data) != {
            "kind", "repository", "workflow_path", "workflow_name", "event",
            "run_id", "attempt", "head_sha", "ref",
        }:
            raise RuntimeContractError("GitHub execution context has invalid fields")
        repository = _string(data["repository"], "repository")
        if not REPOSITORY_RE.fullmatch(repository) or repository != inputs.repository:
            raise RuntimeContractError("GitHub repository does not match selected source")
        path = _runtime_path(data["workflow_path"], "workflow_path")
        name = _policy_string(data["workflow_name"], "workflow_name")
        event = _policy_string(data["event"], "event")
        if event not in _SUPPORTED_GITHUB_EVENTS:
            raise RuntimeContractError("GitHub event is unsupported")
        run_id = data["run_id"]
        attempt = data["attempt"]
        if not isinstance(run_id, int) or isinstance(run_id, bool) or run_id <= 0:
            raise RuntimeContractError("run_id must be positive")
        if not isinstance(attempt, int) or isinstance(attempt, bool) or attempt <= 0:
            raise RuntimeContractError("attempt must be positive")
        head_sha = _commit(data["head_sha"], "head_sha")
        if head_sha != inputs.project_commit:
            raise RuntimeContractError("GitHub head_sha does not match selected source")
        ref = _runtime_ref(data["ref"], "ref")
        values = {
            "kind": kind,
            "task_id": None,
            "task_short_id": None,
            "workflow_id": None,
            "workflow_revision": None,
            "project_id": None,
            "project_commit": inputs.project_commit,
            "authority_transition": None,
            "repository": repository,
            "workflow_path": path,
            "workflow_name": name,
            "event": event,
            "run_id": run_id,
            "attempt": attempt,
            "head_sha": head_sha,
            "ref": ref,
            "selected_runtime_source_inputs_sha256": (
                inputs.selected_runtime_source_inputs_sha256
            ),
            "_proof": _SEALED_RUNTIME_PROOF,
        }
    else:
        raise RuntimeContractError("current execution kind is unsupported")
    values["execution_context_sha256"] = canonical_sha256(
        {key: value for key, value in values.items() if key != "_proof"}
    )
    return _sealed_new(RuntimeExecutionContext, values)


def _authority_values(value: Any) -> dict[str, Any]:
    if not isinstance(value, Mapping):
        raise RuntimeContractError("observed authority must be a mapping")
    data = dict(value)
    kind = data.get("kind")
    if kind == "kent_transition":
        required = {
            "kind", "task_short_id", "workflow_id", "workflow_revision",
            "project_id", "approval_authority", "authority_transition",
        }
        if set(data) != required:
            raise RuntimeContractError("Kent authority has invalid fields")
        if not _TASK_SHORT_ID_RE.fullmatch(_policy_string(data["task_short_id"], "task_short_id")):
            raise RuntimeContractError("authority task_short_id is invalid")
        if not re.fullmatch(
            r"[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-"
            r"[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}",
            _policy_string(data["workflow_id"], "workflow_id"),
        ):
            raise RuntimeContractError("authority workflow_id is invalid")
        revision = data["workflow_revision"]
        if not isinstance(revision, int) or isinstance(revision, bool) or revision <= 0:
            raise RuntimeContractError("authority workflow_revision is invalid")
        project_id = _policy_string(data["project_id"], "project_id")
        if not project_id.startswith("project-"):
            raise RuntimeContractError("authority project_id is invalid")
        approval = _policy_string(data["approval_authority"], "approval_authority")
        if not re.fullmatch(r"[a-z][a-z0-9_-]*", approval):
            raise RuntimeContractError("approval_authority is not normalized")
        transition = _policy_string(data["authority_transition"], "authority_transition")
        if not _AUTHORITY_KEY_RE.fullmatch(transition):
            raise RuntimeContractError("authority_transition is not normalized")
        return {
            "kind": "kent_transition",
            "task_short_id": data["task_short_id"],
            "workflow_id": data["workflow_id"],
            "workflow_revision": revision,
            "project_id": data["project_id"],
            "approval_authority": approval,
            "authority_transition": transition,
        }
    if kind == "github_run":
        required = {
            "kind", "workflow_path", "workflow_name", "event", "run_id",
            "attempt", "head_sha", "ref",
        }
        if set(data) != required:
            raise RuntimeContractError("GitHub authority has invalid fields")
        path = _runtime_path(data["workflow_path"], "workflow_path")
        name = _policy_string(data["workflow_name"], "workflow_name")
        event = _policy_string(data["event"], "event")
        if event not in _SUPPORTED_GITHUB_EVENTS:
            raise RuntimeContractError("authority event is unsupported")
        run_id = data["run_id"]
        attempt = data["attempt"]
        if not isinstance(run_id, int) or isinstance(run_id, bool) or run_id <= 0:
            raise RuntimeContractError("authority run_id is invalid")
        if not isinstance(attempt, int) or isinstance(attempt, bool) or attempt <= 0:
            raise RuntimeContractError("authority attempt is invalid")
        head_sha = _commit(data["head_sha"], "head_sha")
        ref = _runtime_ref(data["ref"], "ref")
        return {
            "kind": "github_run",
            "workflow_path": path,
            "workflow_name": name,
            "event": event,
            "run_id": run_id,
            "attempt": attempt,
            "head_sha": head_sha,
            "ref": ref,
        }
    raise RuntimeContractError("authority kind is unsupported")


def capture_runtime_authority_binding(
    inputs: SelectedRuntimeSourceInputs,
    captures: Iterable[tuple[str, str, bytes]],
    execution_context: RuntimeExecutionContext,
    observed_authority: Mapping[str, Any],
) -> RuntimeAuthorityBinding:
    inputs = _require_proven_inputs(inputs)
    context = _require_context(execution_context, inputs)
    captured = capture_runtime_source_envelope(inputs, captures)
    authority = _authority_values(observed_authority)
    if authority["kind"] != context.kind:
        raise RuntimeContractError("authority kind does not match execution context")
    if context.kind == "kent_transition":
        for key in (
            "task_short_id",
            "workflow_id",
            "workflow_revision",
            "project_id",
            "authority_transition",
        ):
            if authority[key] != getattr(context, key):
                raise RuntimeContractError(f"Kent authority {key} does not match context")
        repository = inputs.repository
    else:
        for key in (
            "workflow_path",
            "workflow_name",
            "event",
            "run_id",
            "attempt",
            "head_sha",
            "ref",
        ):
            if authority[key] != getattr(context, key):
                raise RuntimeContractError(f"GitHub authority {key} does not match context")
        repository = context.repository
    envelope = _freeze_classification_value(captured["runtime_source_envelope"])
    authority_frozen = _freeze_classification_value(authority)
    provenance = canonical_sha256(
        {
            "authority": authority,
            "repository": repository,
            "project_commit": inputs.project_commit,
            "runtime_source_envelope": captured["runtime_source_envelope"],
            "runtime_source_envelope_digest": captured[
                "runtime_source_envelope_digest"
            ],
            "selected_runtime_source_inputs_sha256": (
                inputs.selected_runtime_source_inputs_sha256
            ),
            "execution_context_sha256": context.execution_context_sha256,
        }
    )
    return _sealed_new(
        RuntimeAuthorityBinding,
        {
            "authority": authority_frozen,
            "repository": repository,
            "project_commit": inputs.project_commit,
            "selected_runtime_source_inputs_sha256": (
                inputs.selected_runtime_source_inputs_sha256
            ),
            "execution_context_sha256": context.execution_context_sha256,
            "runtime_source_envelope": envelope,
            "runtime_source_envelope_digest": captured[
                "runtime_source_envelope_digest"
            ],
            "provenance_fingerprint": provenance,
            "_proof": _SEALED_RUNTIME_PROOF,
        },
    )


def _resolve_runtime_authority_binding(
    binding: RuntimeAuthorityBinding,
    execution_context: RuntimeExecutionContext,
    *,
    runtime_source_envelope_digest: str,
) -> Mapping[str, Any]:
    if not isinstance(binding, RuntimeAuthorityBinding):
        raise RuntimeContractError("runtime authority binding is not sealed")
    if any(
        not hasattr(binding, field)
        for field in ("_proof", "authority", "runtime_source_envelope")
    ):
        raise RuntimeContractError("runtime authority binding is incomplete")
    if binding._proof is not _SEALED_RUNTIME_PROOF:
        raise RuntimeContractError("runtime authority binding proof is invalid")
    if not isinstance(execution_context, RuntimeExecutionContext):
        raise RuntimeContractError("runtime execution context is not sealed")
    if any(
        not hasattr(execution_context, field)
        for field in ("_proof", "execution_context_sha256")
    ):
        raise RuntimeContractError("runtime execution context is incomplete")
    if execution_context._proof is not _SEALED_RUNTIME_PROOF:
        raise RuntimeContractError("runtime execution context proof is invalid")
    _digest(runtime_source_envelope_digest, "runtime_source_envelope_digest")
    _digest(
        binding.selected_runtime_source_inputs_sha256,
        "binding.selected_runtime_source_inputs_sha256",
    )
    _commit(binding.project_commit, "binding.project_commit")
    if binding.selected_runtime_source_inputs_sha256 != (
        execution_context.selected_runtime_source_inputs_sha256
    ):
        raise RuntimeContractError("binding uses a different selected source")
    if binding.execution_context_sha256 != execution_context.execution_context_sha256:
        raise RuntimeContractError("binding uses a different execution context")
    if binding.project_commit != execution_context.project_commit:
        raise RuntimeContractError("binding uses a different project commit")
    if (
        execution_context.kind == "github_run"
        and binding.repository != execution_context.repository
    ):
        raise RuntimeContractError("binding uses a different repository")
    if binding.runtime_source_envelope_digest != runtime_source_envelope_digest:
        raise RuntimeContractError("runtime source envelope digest drifted")
    if binding.execution_context_sha256 != canonical_sha256(_context_core(execution_context)):
        raise RuntimeContractError("execution context fingerprint is stale")
    envelope = _thaw_classification_value(binding.runtime_source_envelope)
    if binding.runtime_source_envelope_digest != sha256_bytes(
        canonical_bytes(envelope)
    ):
        raise RuntimeContractError("runtime source envelope fingerprint is stale")
    authority = _authority_values(_thaw_classification_value(binding.authority))
    if authority["kind"] != execution_context.kind:
        raise RuntimeContractError("binding authority kind is stale")
    if execution_context.kind == "kent_transition":
        for key in (
            "task_short_id",
            "workflow_id",
            "workflow_revision",
            "project_id",
            "authority_transition",
        ):
            if authority[key] != getattr(execution_context, key):
                raise RuntimeContractError(f"binding authority {key} is stale")
    else:
        for key in (
            "workflow_path",
            "workflow_name",
            "event",
            "run_id",
            "attempt",
            "head_sha",
            "ref",
        ):
            if authority[key] != getattr(execution_context, key):
                raise RuntimeContractError(f"binding authority {key} is stale")
    if binding.provenance_fingerprint != canonical_sha256(
        {
            "authority": authority,
            "repository": binding.repository,
            "project_commit": binding.project_commit,
            "runtime_source_envelope": envelope,
            "runtime_source_envelope_digest": binding.runtime_source_envelope_digest,
            "selected_runtime_source_inputs_sha256": (
                binding.selected_runtime_source_inputs_sha256
            ),
            "execution_context_sha256": binding.execution_context_sha256,
        }
    ):
        raise RuntimeContractError("runtime authority binding fingerprint is stale")
    return {
        "authority": authority,
        "repository": binding.repository,
        "project_commit": binding.project_commit,
        "runtime_source_envelope_digest": binding.runtime_source_envelope_digest,
        "selected_runtime_source_inputs_sha256": (
            binding.selected_runtime_source_inputs_sha256
        ),
        "execution_context_sha256": binding.execution_context_sha256,
    }


def _envelope_payload(
    inputs: SelectedRuntimeSourceInputs,
    captures: Sequence[tuple[str, str, bytes]],
) -> dict[str, Any]:
    roots = []
    for (kind, key, contents), descriptor in zip(
        captures,
        inputs.external_roots,
    ):
        if kind != descriptor.kind or key != descriptor.key:
            raise RuntimeContractError(
                "external captures must match the proven root order"
            )
        if not isinstance(contents, bytes):
            raise RuntimeContractError(
                "external capture contents must be bytes"
            )
        if len(contents) > MAX_EXTERNAL_ROOT_BYTES:
            raise RuntimeContractError("external root exceeds its byte limit")
        roots.append(
            {
                "kind": kind,
                "key": key,
                "byte_count": len(contents),
                "sha256": sha256_bytes(contents),
            }
        )
    result = {
        "schema": SOURCE_ENVELOPE_SCHEMA,
        "project_name": inputs.project_name,
        "repository": inputs.repository,
        "topology_kind": inputs.topology_kind,
        "project_commit": inputs.project_commit,
        "source_preview_sha256": inputs.source_preview_sha256,
        "artifact_digests": dict(inputs.artifact_digests),
        "external_roots": roots,
    }
    return result


def capture_runtime_source_envelope(
    inputs: SelectedRuntimeSourceInputs,
    captures: Iterable[tuple[str, str, bytes]],
) -> dict[str, Any]:
    """Capture exact external roots against one proven source selection."""

    inputs = _require_proven_inputs(inputs)
    if isinstance(captures, (str, bytes, bytearray)):
        raise RuntimeContractError("external captures must be a sequence")
    values = list(captures)
    if len(values) != len(inputs.external_roots):
        raise RuntimeContractError(
            "external captures must contain exactly every proven root"
        )
    normalized: list[tuple[str, str, bytes]] = []
    total = 0
    seen: set[tuple[str, str]] = set()
    for index, item in enumerate(values):
        if not isinstance(item, (tuple, list)) or len(item) != 3:
            raise RuntimeContractError(
                f"external capture {index} must be (kind, key, bytes)"
            )
        kind = _string(item[0], f"external capture {index}.kind")
        key = _string(item[1], f"external capture {index}.key")
        contents = item[2]
        if not isinstance(contents, bytes):
            raise RuntimeContractError(
                f"external capture {index}.contents must be bytes"
            )
        total += len(contents)
        if total > MAX_EXTERNAL_TOTAL_BYTES:
            raise RuntimeContractError(
                "external captures exceed the total byte limit"
            )
        identity = (kind, key)
        if identity in seen:
            raise RuntimeContractError("external captures must be unique")
        seen.add(identity)
        normalized.append((kind, key, contents))
    payload = _envelope_payload(inputs, normalized)
    envelope_bytes = canonical_bytes(payload)
    if len(envelope_bytes) > MAX_SOURCE_PREVIEW_BYTES:
        raise RuntimeContractError("runtime source envelope is too large")
    return {
        "runtime_source_envelope": payload,
        "runtime_source_envelope_digest": sha256_bytes(envelope_bytes),
        "project_commit": inputs.project_commit,
    }


def revalidate_runtime_source_envelope(
    inputs: SelectedRuntimeSourceInputs,
    previous: Mapping[str, Any],
    captures: Iterable[tuple[str, str, bytes]],
) -> dict[str, Any]:
    fresh = capture_runtime_source_envelope(inputs, captures)
    if dict(previous) != fresh:
        raise RuntimeContractError(
            "runtime source envelope changed during revalidation"
        )
    return fresh


def validate_runtime_source_envelope(
    value: Mapping[str, Any],
) -> dict[str, Any]:
    data = _closed(
        value,
        {
            "schema",
            "project_name",
            "repository",
            "topology_kind",
            "project_commit",
            "source_preview_sha256",
            "artifact_digests",
            "external_roots",
        },
        "runtime source envelope",
    )
    if set(data) != {
        "schema",
        "project_name",
        "repository",
        "topology_kind",
        "project_commit",
        "source_preview_sha256",
        "artifact_digests",
        "external_roots",
    }:
        raise RuntimeContractError("runtime source envelope has missing fields")
    if data["schema"] != SOURCE_ENVELOPE_SCHEMA:
        raise RuntimeContractError("unsupported runtime source envelope schema")
    project_name = _string(data["project_name"], "project_name")
    repository = _string(data["repository"], "repository")
    if not REPOSITORY_RE.fullmatch(repository):
        raise RuntimeContractError("repository has an invalid shape")
    topology = _string(data["topology_kind"], "topology_kind")
    commit = _commit(data["project_commit"], "project_commit")
    _digest(data["source_preview_sha256"], "source_preview_sha256")
    artifacts = _object(data["artifact_digests"], "artifact_digests")
    required = {
        "spec_raw_blob_sha256",
        "source_manifest_raw_blob_sha256",
        "snapshot_raw_blob_sha256",
    }
    if set(artifacts) - required - {"builder_raw_blob_sha256"}:
        raise RuntimeContractError("artifact_digests has unknown fields")
    if not required <= set(artifacts):
        raise RuntimeContractError("artifact_digests is missing fields")
    for key, digest in artifacts.items():
        _digest(digest, f"artifact_digests.{key}")
    roots = data["external_roots"]
    if not isinstance(roots, list):
        raise RuntimeContractError("external_roots must be an array")
    normalized_roots = []
    identities: list[tuple[str, str]] = []
    total_bytes = 0
    for index, value in enumerate(roots):
        root = _closed(
            value,
            {"kind", "key", "byte_count", "sha256"},
            f"external_roots[{index}]",
        )
        if set(root) != {"kind", "key", "byte_count", "sha256"}:
            raise RuntimeContractError(
                f"external_roots[{index}] has missing fields"
            )
        kind = _string(root["kind"], f"external_roots[{index}].kind")
        key = _string(root["key"], f"external_roots[{index}].key")
        identities.append((kind, key))
        byte_count = _integer(
            root["byte_count"],
            f"external_roots[{index}].byte_count",
        )
        total_bytes += byte_count
        if total_bytes > MAX_EXTERNAL_TOTAL_BYTES:
            raise RuntimeContractError(
                "external_roots exceed the total byte limit"
            )
        normalized_roots.append(
            {
                "kind": kind,
                "key": key,
                "byte_count": byte_count,
                "sha256": _digest(
                    root["sha256"],
                    f"external_roots[{index}].sha256",
                ),
            }
        )
        if normalized_roots[-1]["byte_count"] > MAX_EXTERNAL_ROOT_BYTES:
            raise RuntimeContractError(
                f"external_roots[{index}].byte_count exceeds its limit"
            )
    if identities != sorted(identities) or len(set(identities)) != len(identities):
        raise RuntimeContractError(
            "external_roots must be sorted and unique"
        )
    result = {
        "schema": SOURCE_ENVELOPE_SCHEMA,
        "project_name": project_name,
        "repository": repository,
        "topology_kind": topology,
        "project_commit": commit,
        "source_preview_sha256": data["source_preview_sha256"],
        "artifact_digests": dict(sorted(artifacts.items())),
        "external_roots": normalized_roots,
    }
    if len(canonical_bytes(result)) > MAX_SOURCE_PREVIEW_BYTES:
        raise RuntimeContractError("runtime source envelope is too large")
    return result


def validate_captured_runtime_source_envelope(
    value: Mapping[str, Any],
) -> dict[str, Any]:
    data = _closed(
        value,
        {
            "runtime_source_envelope",
            "runtime_source_envelope_digest",
            "project_commit",
        },
        "captured runtime source envelope",
    )
    if set(data) != {
        "runtime_source_envelope",
        "runtime_source_envelope_digest",
        "project_commit",
    }:
        raise RuntimeContractError(
            "captured runtime source envelope has missing fields"
        )
    envelope = validate_runtime_source_envelope(
        data["runtime_source_envelope"]
    )
    digest = _digest(
        data["runtime_source_envelope_digest"],
        "runtime_source_envelope_digest",
    )
    if digest != canonical_sha256(envelope):
        raise RuntimeContractError(
            "runtime_source_envelope_digest does not match envelope"
        )
    if data["project_commit"] != envelope["project_commit"]:
        raise RuntimeContractError(
            "captured project_commit does not match envelope"
        )
    return {
        "runtime_source_envelope": envelope,
        "runtime_source_envelope_digest": digest,
        "project_commit": envelope["project_commit"],
    }


def parse_runtime_external_captures(raw: bytes | str) -> tuple[
    tuple[str, str, bytes], ...
]:
    if isinstance(raw, str):
        raw_bytes = raw.encode("utf-8")
    elif isinstance(raw, bytes):
        raw_bytes = raw
    else:
        raise RuntimeContractError("capture input must be bytes or text")
    if len(raw_bytes) > MAX_CAPTURE_STDIN_BYTES:
        raise RuntimeContractError("runtime capture input exceeds its limit")
    def reject_duplicate_keys(
        pairs: list[tuple[str, Any]],
    ) -> dict[str, Any]:
        result: dict[str, Any] = {}
        for key, item in pairs:
            if key in result:
                raise RuntimeContractError(
                    "runtime capture input contains duplicate object keys"
                )
            result[key] = item
        return result

    try:
        text = raw_bytes.decode("utf-8")
        decoder = json.JSONDecoder(object_pairs_hook=reject_duplicate_keys)
        value, end = decoder.raw_decode(text)
        if end != len(text):
            raise RuntimeContractError(
                "runtime capture input contains trailing bytes"
            )
    except RuntimeContractError:
        raise
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise RuntimeContractError(
            f"runtime capture input is invalid JSON: {error}"
        ) from error
    data = _closed(value, {"schema", "roots"}, "runtime capture input")
    if set(data) != {"schema", "roots"}:
        raise RuntimeContractError("runtime capture input has missing fields")
    if data["schema"] != EXTERNAL_CAPTURE_SCHEMA:
        raise RuntimeContractError("unsupported runtime capture schema")
    roots = data["roots"]
    if not isinstance(roots, list):
        raise RuntimeContractError("runtime capture roots must be an array")
    result = []
    total = 0
    for index, value in enumerate(roots):
        item = _closed(
            value,
            {"kind", "key", "contents_base64"},
            f"runtime capture roots[{index}]",
        )
        if set(item) != {"kind", "key", "contents_base64"}:
            raise RuntimeContractError(
                f"runtime capture roots[{index}] has missing fields"
            )
        kind = _string(item["kind"], f"runtime capture roots[{index}].kind")
        key = _string(item["key"], f"runtime capture roots[{index}].key")
        encoded = item["contents_base64"]
        if not isinstance(encoded, str):
            raise RuntimeContractError(
                f"runtime capture roots[{index}].contents_base64 must be text"
            )
        try:
            contents = base64.b64decode(encoded, validate=True)
        except (ValueError, UnicodeError, base64.binascii.Error) as error:
            raise RuntimeContractError(
                f"runtime capture roots[{index}] has invalid base64"
            ) from error
        if base64.b64encode(contents).decode("ascii") != encoded:
            raise RuntimeContractError(
                f"runtime capture roots[{index}] is not canonical base64"
            )
        if len(contents) > MAX_EXTERNAL_ROOT_BYTES:
            raise RuntimeContractError(
                f"runtime capture roots[{index}] exceeds its byte limit"
            )
        total += len(contents)
        if total > MAX_EXTERNAL_TOTAL_BYTES:
            raise RuntimeContractError(
                "runtime capture roots exceed the total byte limit"
            )
        result.append((kind, key, contents))
    identities = [(kind, key) for kind, key, _ in result]
    if identities != sorted(identities) or len(set(identities)) != len(identities):
        raise RuntimeContractError(
            "runtime capture roots must be sorted and unique"
        )
    return tuple(result)


SEAL_REQUEST_KEYS = {
    "schema",
    "operation_report_digests",
    "redaction",
    "retention_class",
}


def validate_terminal_seal_request(value: Mapping[str, Any]) -> dict[str, Any]:
    data = _closed(value, SEAL_REQUEST_KEYS, "terminal seal request")
    if set(data) != SEAL_REQUEST_KEYS:
        raise RuntimeContractError("terminal seal request has missing fields")
    if data["schema"] != TERMINAL_SEAL_REQUEST_SCHEMA:
        raise RuntimeContractError("unsupported terminal seal request schema")
    reports = data["operation_report_digests"]
    if not isinstance(reports, list):
        raise RuntimeContractError("operation_report_digests must be an array")
    allowed = {
        "approval",
        "merge",
        "publication",
        "qualification",
        "runtime_source",
    }
    normalized_reports = []
    kinds = []
    for index, value in enumerate(reports):
        item = _closed(
            value,
            {"kind", "sha256"},
            f"operation_report_digests[{index}]",
        )
        if set(item) != {"kind", "sha256"}:
            raise RuntimeContractError(
                f"operation_report_digests[{index}] has missing fields"
            )
        kind = _string(item["kind"], f"operation_report_digests[{index}].kind")
        if kind not in allowed:
            raise RuntimeContractError(
                f"unsupported operation report kind: {kind}"
            )
        kinds.append(kind)
        normalized_reports.append(
            {"kind": kind, "sha256": _digest(
                item["sha256"],
                f"operation_report_digests[{index}].sha256",
            )}
        )
    if kinds != sorted(kinds) or len(set(kinds)) != len(kinds):
        raise RuntimeContractError(
            "operation_report_digests must be sorted and unique by kind"
        )
    redaction = _closed(
        data["redaction"],
        {"status", "report_sha256"},
        "redaction",
    )
    if set(redaction) != {"status", "report_sha256"}:
        raise RuntimeContractError("redaction has missing fields")
    if redaction["status"] != "passed":
        raise RuntimeContractError("redaction.status must be passed")
    normalized_redaction = {
        "status": "passed",
        "report_sha256": _digest(redaction["report_sha256"], "report_sha256"),
    }
    if data["retention_class"] != "cleanup_report_only":
        raise RuntimeContractError(
            "retention_class must be cleanup_report_only"
        )
    return {
        "schema": TERMINAL_SEAL_REQUEST_SCHEMA,
        "operation_report_digests": normalized_reports,
        "redaction": normalized_redaction,
        "retention_class": "cleanup_report_only",
    }


def _event_hash(value: Mapping[str, Any]) -> str:
    return canonical_sha256(value)


def build_terminal_seal_record(
    request: Mapping[str, Any],
    *,
    sequence: int,
    task_short_id: str,
    previous_hash: str,
) -> dict[str, Any]:
    request = validate_terminal_seal_request(request)
    sequence = _integer(sequence, "sequence", minimum=2)
    task_short_id = _string(task_short_id, "task_short_id")
    if not TASK_RE.fullmatch(task_short_id):
        raise RuntimeContractError("task_short_id has an invalid shape")
    if previous_hash and not SHA256_RE.fullmatch(previous_hash):
        raise RuntimeContractError("previous_hash must be lowercase 64-hex")
    unsigned = {
        "schema_version": 1,
        "record_kind": TERMINAL_SEAL_SCHEMA,
        "sequence": sequence,
        "task_short_id": task_short_id,
        "operation_report_digests": request["operation_report_digests"],
        "redaction": request["redaction"],
        "retention_class": request["retention_class"],
        "previous_hash": previous_hash,
    }
    return {**unsigned, "event_hash": _event_hash(unsigned)}


def validate_terminal_seal_record(
    value: Mapping[str, Any],
    *,
    minimum_sequence: int = 2,
) -> dict[str, Any]:
    keys = {
        "schema_version",
        "record_kind",
        "sequence",
        "task_short_id",
        "operation_report_digests",
        "redaction",
        "retention_class",
        "previous_hash",
        "event_hash",
    }
    data = _closed(value, keys, "terminal seal record")
    if set(data) != keys:
        raise RuntimeContractError("terminal seal record has missing fields")
    if data["schema_version"] != 1 or data["record_kind"] != TERMINAL_SEAL_SCHEMA:
        raise RuntimeContractError("invalid terminal seal record identity")
    sequence = _integer(data["sequence"], "sequence", minimum=minimum_sequence)
    request = validate_terminal_seal_request(
        {
            "schema": TERMINAL_SEAL_REQUEST_SCHEMA,
            "operation_report_digests": data["operation_report_digests"],
            "redaction": data["redaction"],
            "retention_class": data["retention_class"],
        }
    )
    task = _string(data["task_short_id"], "task_short_id")
    if not TASK_RE.fullmatch(task):
        raise RuntimeContractError("task_short_id has an invalid shape")
    previous = _string(
        data["previous_hash"],
        "previous_hash",
        nonempty=False,
        max_bytes=64,
    )
    if previous and not SHA256_RE.fullmatch(previous):
        raise RuntimeContractError("previous_hash must be lowercase 64-hex")
    event_hash = _digest(data["event_hash"], "event_hash")
    unsigned = {
        "schema_version": 1,
        "record_kind": TERMINAL_SEAL_SCHEMA,
        "sequence": sequence,
        "task_short_id": task,
        **{
            "operation_report_digests": request["operation_report_digests"],
            "redaction": request["redaction"],
            "retention_class": request["retention_class"],
            "previous_hash": previous,
        },
    }
    if _event_hash(unsigned) != event_hash:
        raise RuntimeContractError("terminal seal event_hash is invalid")
    return {**unsigned, "event_hash": event_hash}


def build_terminal_marker(
    seal_record: Mapping[str, Any],
) -> dict[str, Any]:
    seal = validate_terminal_seal_record(seal_record)
    return {
        "schema": TERMINAL_MARKER_SCHEMA,
        "task_short_id": seal["task_short_id"],
        "event_count": seal["sequence"],
        "final_hash": seal["event_hash"],
        "operation_report_digests": seal["operation_report_digests"],
        "redaction": seal["redaction"],
        "retention_class": seal["retention_class"],
    }


def validate_terminal_marker(value: Mapping[str, Any]) -> dict[str, Any]:
    keys = {
        "schema",
        "task_short_id",
        "event_count",
        "final_hash",
        "operation_report_digests",
        "redaction",
        "retention_class",
    }
    data = _closed(value, keys, "terminal marker")
    if set(data) != keys:
        raise RuntimeContractError("terminal marker has missing fields")
    if data["schema"] != TERMINAL_MARKER_SCHEMA:
        raise RuntimeContractError("unsupported terminal marker schema")
    task = _string(data["task_short_id"], "task_short_id")
    if not TASK_RE.fullmatch(task):
        raise RuntimeContractError("task_short_id has an invalid shape")
    count = _integer(data["event_count"], "event_count", minimum=2)
    final_hash = _digest(data["final_hash"], "final_hash")
    request = validate_terminal_seal_request(
        {
            "schema": TERMINAL_SEAL_REQUEST_SCHEMA,
            "operation_report_digests": data["operation_report_digests"],
            "redaction": data["redaction"],
            "retention_class": data["retention_class"],
        }
    )
    return {
        "schema": TERMINAL_MARKER_SCHEMA,
        "task_short_id": task,
        "event_count": count,
        "final_hash": final_hash,
        "operation_report_digests": request["operation_report_digests"],
        "redaction": request["redaction"],
        "retention_class": request["retention_class"],
    }


def terminal_marker_line(marker: Mapping[str, Any]) -> str:
    return "TERMINAL_EVIDENCE_V1 " + json.dumps(
        validate_terminal_marker(marker),
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    )


def parse_terminal_marker_line(line: str) -> dict[str, Any]:
    if not isinstance(line, str) or not line.startswith(
        "TERMINAL_EVIDENCE_V1 "
    ):
        raise RuntimeContractError("invalid terminal marker line")
    suffix = line.removeprefix("TERMINAL_EVIDENCE_V1 ")
    if not suffix or suffix != suffix.strip():
        raise RuntimeContractError("terminal marker line is not canonical")
    try:
        value = json.loads(suffix)
    except json.JSONDecodeError as error:
        raise RuntimeContractError("terminal marker line is invalid JSON") from error
    marker = validate_terminal_marker(value)
    if terminal_marker_line(marker) != line:
        raise RuntimeContractError("terminal marker line is not canonical")
    return marker


def validate_terminal_chain(
    records: Sequence[Mapping[str, Any]],
    *,
    task_short_id: str,
) -> dict[str, Any]:
    if not records:
        raise RuntimeContractError("terminal chain must not be empty")
    previous = ""
    for index, raw in enumerate(records, start=1):
        data = _object(raw, f"ledger record {index}")
        if data.get("schema_version") != 1:
            raise RuntimeContractError("ledger record has unsupported schema")
        if data.get("sequence") != index:
            raise RuntimeContractError("ledger sequence is not contiguous")
        if data.get("task_short_id") != task_short_id:
            raise RuntimeContractError("ledger task does not match")
        if data.get("previous_hash") != previous:
            raise RuntimeContractError("ledger hash chain is broken")
        event_hash = _digest(data.get("event_hash"), "event_hash")
        unsigned = dict(data)
        del unsigned["event_hash"]
        if _event_hash(unsigned) != event_hash:
            raise RuntimeContractError("ledger event hash is invalid")
        if (
            index < len(records)
            and data.get("record_kind") == TERMINAL_SEAL_SCHEMA
        ):
            raise RuntimeContractError("terminal seal must be the final record")
        previous = event_hash
    seal = validate_terminal_seal_record(records[-1])
    if seal["sequence"] != len(records) or seal["previous_hash"] != (
        records[-2]["event_hash"]
    ):
        raise RuntimeContractError("terminal seal is not the final chain record")
    return build_terminal_marker(seal)


def validate_cleanup_report(report: str) -> dict[str, Any]:
    if not isinstance(report, str):
        raise RuntimeContractError("cleanup_report must be text")
    lines = report.splitlines()
    if not lines:
        raise RuntimeContractError("cleanup_report must contain a marker")
    marker_indices = [
        index for index, line in enumerate(lines)
        if line.startswith("TERMINAL_EVIDENCE_V1 ")
    ]
    if marker_indices != [len(lines) - 1]:
        raise RuntimeContractError(
            "cleanup_report must end with exactly one terminal marker"
        )
    return parse_terminal_marker_line(lines[-1])


def classify_terminal_state(
    *,
    active: bool,
    tombstone: bool,
    sentinel: bool,
    ledger_valid: bool,
    marker_valid: bool,
    managed_worktree: bool = False,
    workspace_present: bool = True,
    git_registered: bool = True,
    same_invocation: bool = False,
    tombstone_entries: Sequence[str] = (),
) -> str:
    values = (
        active,
        tombstone,
        sentinel,
        ledger_valid,
        marker_valid,
        managed_worktree,
        workspace_present,
        git_registered,
        same_invocation,
    )
    if any(not isinstance(value, bool) for value in values):
        raise RuntimeContractError("terminal state flags must be booleans")
    if not isinstance(tombstone_entries, (tuple, list)):
        raise RuntimeContractError("tombstone_entries must be a sequence")
    entries = tuple(
        _string(item, "tombstone_entries[]", max_bytes=256)
        for item in tombstone_entries
    )
    allowed_entries = ("fix-checkpoint.json", "smoke-checkpoint.json")
    if entries != tuple(
        item for item in allowed_entries if item in entries
    ):
        return "blocked_unknown_tombstone_entry"
    if not tombstone and entries:
        return "blocked_unknown_tombstone_entry"
    if active and tombstone:
        return "blocked_conflicting_task_state"
    if active and sentinel:
        return "blocked_active_terminal_conflict"
    if tombstone and not marker_valid:
        return "blocked_marker_invalid"
    if tombstone and not ledger_valid and not sentinel:
        return "blocked_missing_valid_ledger"
    if tombstone and not sentinel and ledger_valid and marker_valid:
        return "pre_sentinel_recovery"
    if sentinel and tombstone and ledger_valid and marker_valid:
        return "resume_tombstone_cleanup"
    if (
        sentinel
        and tombstone
        and not ledger_valid
        and marker_valid
        and not entries
    ):
        return "resume_tombstone_cleanup"
    if sentinel and tombstone and not ledger_valid and marker_valid:
        return "blocked_checkpoint_after_ledger_loss"
    if sentinel and not active and not tombstone and marker_valid:
        return "acknowledgement_loss"
    if managed_worktree and same_invocation and not workspace_present and not git_registered:
        return "acknowledgement_loss"
    if managed_worktree and not workspace_present and not same_invocation:
        return "blocked_managed_workspace_evidence_loss"
    if managed_worktree and workspace_present and not tombstone:
        return "blocked_managed_workspace_evidence_loss"
    if not active and not tombstone and not sentinel:
        return "no_runtime_state"
    return "blocked_terminal_state"


VERIFICATION_CODES = {
    "passed",
    "verification_failed",
    "verification_blocked",
    "input_invalid",
    "workspace_invalid",
    "verifier_missing",
    "verifier_unsafe",
    "log_path_unsafe",
    "child_timeout",
    "child_exit_nonzero",
    "child_output_invalid",
    "log_limit_exceeded",
    "internal_error",
}


def validate_verification_report(value: Mapping[str, Any]) -> dict[str, Any]:
    keys = {"schema", "code", "log_path", "log_sha256", "exit_code"}
    data = _closed(value, keys, "verification report")
    if set(data) != keys:
        raise RuntimeContractError("verification report has missing fields")
    if data["schema"] != VERIFICATION_REPORT_SCHEMA:
        raise RuntimeContractError("unsupported verification report schema")
    code = _string(data["code"], "verification report.code")
    if code not in VERIFICATION_CODES:
        raise RuntimeContractError("verification report.code is unsupported")
    path = data["log_path"]
    if path is not None:
        path = _string(path, "verification report.log_path")
        if not LOG_PATH_RE.fullmatch(path):
            raise RuntimeContractError("verification report.log_path is unsafe")
    digest = data["log_sha256"]
    if digest is not None:
        digest = _digest(digest, "verification report.log_sha256")
    exit_code = data["exit_code"]
    if exit_code is not None:
        if not isinstance(exit_code, int) or isinstance(exit_code, bool):
            raise RuntimeContractError("verification report.exit_code is invalid")
        if not -255 <= exit_code <= 255:
            raise RuntimeContractError("verification report.exit_code is out of range")
    if code == "passed" and (path is None or digest is None or exit_code != 0):
        raise RuntimeContractError("passed verification requires a committed log")
    return {
        "schema": VERIFICATION_REPORT_SCHEMA,
        "code": code,
        "log_path": path,
        "log_sha256": digest,
        "exit_code": exit_code,
    }


def classify_verification_report(
    value: Mapping[str, Any],
) -> str:
    code = validate_verification_report(value)["code"]
    if code == "passed":
        return "passed"
    if code in {"verification_failed", "child_exit_nonzero"}:
        return "needs_changes"
    return "blocked"


def classify_ci_report(value: Mapping[str, Any]) -> str:
    report = validate_ci_report(value)
    reason = report["attempts"][-1]["reason"]
    if reason == "all_expected_checks_terminal_green":
        return "ci_watch_passed"
    if reason in {"expected_check_failed", "expected_check_skipped"}:
        return "ci_watch_failed"
    if reason in {
        "github_query_failed",
        "duplicate_observed_check",
        "expected_contract_invalid",
        "report_invalid",
    }:
        return "ci_watch_blocked"
    return "ci_watch_waiting"


def _check_name(value: Any, label: str) -> str:
    return _string(value, label, max_bytes=256)


def _validate_observed_check(
    value: Mapping[str, Any],
    label: str,
) -> dict[str, Any]:
    keys = {"workflow_name", "check_name", "bucket", "state", "link"}
    data = _closed(value, keys, label)
    if set(data) != keys:
        raise RuntimeContractError(f"{label} has missing fields")
    workflow = _check_name(data["workflow_name"], f"{label}.workflow_name")
    check = _check_name(data["check_name"], f"{label}.check_name")
    bucket = _string(data["bucket"], f"{label}.bucket")
    if bucket not in {"pass", "fail", "pending", "skipping", "cancel"}:
        raise RuntimeContractError(f"{label}.bucket is unsupported")
    state = _string(data["state"], f"{label}.state", max_bytes=64)
    if not re.fullmatch(r"[A-Z_]{1,64}", state):
        raise RuntimeContractError(f"{label}.state is invalid")
    link = data["link"]
    if link is not None:
        link = _string(link, f"{label}.link", max_bytes=2048)
        if not LINK_RE.match(link):
            raise RuntimeContractError(f"{label}.link is unsafe")
    return {
        "workflow_name": workflow,
        "check_name": check,
        "bucket": bucket,
        "state": state,
        "link": link,
    }


def validate_pr_feedback_item(value: Mapping[str, Any]) -> dict[str, Any]:
    data = _object(value, "feedback item")
    kind = data.get("kind")
    if kind == "issue_comment":
        keys = {
            "kind",
            "id",
            "author_login",
            "created_at",
            "updated_at",
            "body_bytes",
            "body_sha256",
        }
        item = _closed(data, keys, "issue_comment")
        if set(item) != keys:
            raise RuntimeContractError("issue_comment has missing fields")
        return {
            "kind": kind,
            "id": _string(item["id"], "issue_comment.id", max_bytes=256),
            "author_login": (
                None
                if item["author_login"] is None
                else _string(item["author_login"], "issue_comment.author_login", max_bytes=100)
            ),
            "created_at": _timestamp(item["created_at"], "issue_comment.created_at"),
            "updated_at": _timestamp(item["updated_at"], "issue_comment.updated_at"),
            "body_bytes": _integer(item["body_bytes"], "issue_comment.body_bytes"),
            "body_sha256": _digest(item["body_sha256"], "issue_comment.body_sha256"),
        }
    if kind == "review":
        keys = {
            "kind",
            "id",
            "author_login",
            "state",
            "submitted_at",
            "updated_at",
            "commit_oid",
            "body_bytes",
            "body_sha256",
        }
        item = _closed(data, keys, "review")
        if set(item) != keys:
            raise RuntimeContractError("review has missing fields")
        return {
            "kind": kind,
            "id": _string(item["id"], "review.id", max_bytes=256),
            "author_login": _optional_login(item["author_login"], "review.author_login"),
            "state": _upper_state(item["state"], "review.state"),
            "submitted_at": _optional_timestamp(item["submitted_at"], "review.submitted_at"),
            "updated_at": _timestamp(item["updated_at"], "review.updated_at"),
            "commit_oid": _commit(item["commit_oid"], "review.commit_oid", nullable=True),
            "body_bytes": _integer(item["body_bytes"], "review.body_bytes"),
            "body_sha256": _digest(item["body_sha256"], "review.body_sha256"),
        }
    if kind == "review_thread":
        keys = {
            "kind",
            "id",
            "resolved",
            "outdated",
            "path",
            "current_line",
            "current_start_line",
            "original_line",
            "original_start_line",
            "subject_type",
            "comment_ids",
        }
        item = _closed(data, keys, "review_thread")
        if set(item) != keys:
            raise RuntimeContractError("review_thread has missing fields")
        comments = _sorted_unique_strings(
            item["comment_ids"],
            "review_thread.comment_ids",
            max_items=MAX_FEEDBACK_HARD_LIMIT,
        )
        if any(len(comment_id.encode("utf-8")) > 256 for comment_id in comments):
            raise RuntimeContractError(
                "review_thread.comment_ids[] exceeds the 256-byte limit"
            )
        return {
            "kind": kind,
            "id": _string(item["id"], "review_thread.id", max_bytes=256),
            "resolved": _boolean(item["resolved"], "review_thread.resolved"),
            "outdated": _boolean(item["outdated"], "review_thread.outdated"),
            "path": _string(item["path"], "review_thread.path", max_bytes=1024),
            "current_line": _line(item["current_line"], "review_thread.current_line"),
            "current_start_line": _line(
                item["current_start_line"],
                "review_thread.current_start_line",
            ),
            "original_line": _line(item["original_line"], "review_thread.original_line"),
            "original_start_line": _line(
                item["original_start_line"],
                "review_thread.original_start_line",
            ),
            "subject_type": _string(item["subject_type"], "review_thread.subject_type", max_bytes=64),
            "comment_ids": list(comments),
        }
    if kind == "review_comment":
        keys = {
            "kind",
            "id",
            "thread_id",
            "author_login",
            "created_at",
            "updated_at",
            "current_commit_oid",
            "original_commit_oid",
            "body_bytes",
            "body_sha256",
        }
        item = _closed(data, keys, "review_comment")
        if set(item) != keys:
            raise RuntimeContractError("review_comment has missing fields")
        return {
            "kind": kind,
            "id": _string(item["id"], "review_comment.id", max_bytes=256),
            "thread_id": _string(item["thread_id"], "review_comment.thread_id", max_bytes=256),
            "author_login": _optional_login(item["author_login"], "review_comment.author_login"),
            "created_at": _timestamp(item["created_at"], "review_comment.created_at"),
            "updated_at": _timestamp(item["updated_at"], "review_comment.updated_at"),
            "current_commit_oid": _commit(
                item["current_commit_oid"],
                "review_comment.current_commit_oid",
                nullable=True,
            ),
            "original_commit_oid": _commit(
                item["original_commit_oid"],
                "review_comment.original_commit_oid",
                nullable=True,
            ),
            "body_bytes": _integer(item["body_bytes"], "review_comment.body_bytes"),
            "body_sha256": _digest(item["body_sha256"], "review_comment.body_sha256"),
        }
    raise RuntimeContractError("feedback item kind is unsupported")


def check_state_sha256(checks: Sequence[Mapping[str, Any]]) -> str:
    if not isinstance(checks, Sequence) or isinstance(checks, (str, bytes)):
        raise RuntimeContractError("check state rows must be a sequence")
    if len(checks) > 1000:
        raise RuntimeContractError("check state rows exceed their hard limit")
    rows = []
    for index, value in enumerate(checks):
        data = _object(value, f"check_state[{index}]")
        allowed = {"name", "context", "status", "state", "conclusion"}
        unknown = sorted(set(data) - allowed)
        if unknown:
            raise RuntimeContractError(
                f"check_state[{index}] has unknown fields: {unknown}"
            )
        identity_keys = [
            key for key in ("name", "context")
            if key in data and data[key] is not None
        ]
        state_keys = [
            key for key in ("status", "state")
            if key in data and data[key] is not None
        ]
        if len(identity_keys) != 1 or len(state_keys) != 1:
            raise RuntimeContractError(
                f"check_state[{index}] needs exactly one non-null "
                "name/context and status/state"
            )
        rows.append(
            [
                _string(
                    data[identity_keys[0]],
                    f"check_state[{index}].{identity_keys[0]}",
                    max_bytes=256,
                ),
                _string(
                    data[state_keys[0]],
                    f"check_state[{index}].{state_keys[0]}",
                    max_bytes=64,
                ),
                _string(
                    "" if data.get("conclusion") is None else data["conclusion"],
                    f"check_state[{index}].conclusion",
                    nonempty=False,
                    max_bytes=64,
                ),
            ]
        )
    rows.sort()
    return canonical_sha256(rows)


def _timestamp(value: Any, label: str) -> str:
    value = _string(value, label, max_bytes=40)
    if not RFC3339_RE.fullmatch(value):
        raise RuntimeContractError(f"{label} is not RFC3339")
    return value


def _optional_timestamp(value: Any, label: str) -> str | None:
    return None if value is None else _timestamp(value, label)


def _optional_login(value: Any, label: str) -> str | None:
    return None if value is None else _string(value, label, max_bytes=100)


def _upper_state(value: Any, label: str) -> str:
    value = _string(value, label, max_bytes=64)
    if not UPPER_TOKEN_RE.fullmatch(value):
        raise RuntimeContractError(f"{label} is invalid")
    return value


def _line(value: Any, label: str) -> int | None:
    if value is None:
        return None
    return _integer(value, label, minimum=1)


def _timestamp_or_empty(value: Any, label: str) -> str:
    if value == "":
        return ""
    return _upper_state(value, label)


def validate_pr_feedback_cursor(value: Any) -> str | dict[str, Any]:
    if value == "uninitialized":
        return value
    data = _object(value, "PR feedback cursor")
    common = {
        "schema",
        "mode",
        "repository",
        "pull_number",
        "head_oid",
        "base_oid",
        "pr_state",
        "review_decision",
        "merge_state_status",
        "check_count",
        "checks_sha256",
        "item_count",
        "items_sha256",
    }
    mode = data.get("mode")
    keys = common | ({"items"} if mode == "complete" else set())
    data = _closed(data, keys, "PR feedback cursor")
    if set(data) != keys:
        raise RuntimeContractError("PR feedback cursor has missing fields")
    if data["schema"] != PR_CURSOR_SCHEMA:
        raise RuntimeContractError("unsupported PR feedback cursor schema")
    if mode not in {"complete", "digest_only"}:
        raise RuntimeContractError("PR feedback cursor.mode is unsupported")
    repository = _string(data["repository"], "cursor.repository")
    if not REPOSITORY_RE.fullmatch(repository):
        raise RuntimeContractError("cursor.repository has an invalid shape")
    pull = _integer(data["pull_number"], "cursor.pull_number", minimum=1)
    if pull > 2147483647:
        raise RuntimeContractError("cursor.pull_number is too large")
    head = _commit(data["head_oid"], "cursor.head_oid")
    base = _commit(data["base_oid"], "cursor.base_oid")
    pr_state = _upper_state(data["pr_state"], "cursor.pr_state")
    review = _timestamp_or_empty(data["review_decision"], "cursor.review_decision")
    merge = _timestamp_or_empty(
        data["merge_state_status"],
        "cursor.merge_state_status",
    )
    check_count = _integer(data["check_count"], "cursor.check_count")
    if check_count > MAX_FEEDBACK_HARD_LIMIT:
        raise RuntimeContractError("cursor.check_count exceeds its hard limit")
    checks_sha = _digest(data["checks_sha256"], "cursor.checks_sha256")
    item_count = _integer(data["item_count"], "cursor.item_count")
    if item_count > MAX_FEEDBACK_HARD_LIMIT:
        raise RuntimeContractError("cursor.item_count exceeds its hard limit")
    items_sha = _digest(data["items_sha256"], "cursor.items_sha256")
    result = {
        "schema": PR_CURSOR_SCHEMA,
        "mode": mode,
        "repository": repository,
        "pull_number": pull,
        "head_oid": head,
        "base_oid": base,
        "pr_state": pr_state,
        "review_decision": review,
        "merge_state_status": merge,
        "check_count": check_count,
        "checks_sha256": checks_sha,
        "item_count": item_count,
        "items_sha256": items_sha,
    }
    if mode == "complete":
        items = data["items"]
        if not isinstance(items, list) or len(items) > MAX_FEEDBACK_ITEMS:
            raise RuntimeContractError("complete cursor items exceed its limit")
        normalized = [validate_pr_feedback_item(item) for item in items]
        identities = [(item["kind"], item["id"]) for item in normalized]
        if identities != sorted(identities) or len(set(identities)) != len(identities):
            raise RuntimeContractError("cursor items must be sorted and unique")
        if canonical_sha256(normalized) != items_sha:
            raise RuntimeContractError("cursor items_sha256 is invalid")
        if item_count != len(normalized):
            raise RuntimeContractError(
                "complete cursor item_count does not match items"
            )
        result["items"] = normalized
    if len(canonical_bytes(result)) > MAX_FEEDBACK_BYTES:
        raise RuntimeContractError("PR feedback cursor exceeds its byte limit")
    return result


def make_pr_feedback_cursor(
    *,
    repository: str,
    pull_number: int,
    head_oid: str,
    base_oid: str,
    pr_state: str,
    review_decision: str,
    merge_state_status: str,
    checks: Sequence[Mapping[str, Any]],
    items: Sequence[Mapping[str, Any]],
) -> str | dict[str, Any]:
    normalized_checks = [
        _validate_observed_check(item, f"checks[{index}]")
        for index, item in enumerate(checks)
    ]
    if len(normalized_checks) > 1000:
        raise RuntimeContractError("cursor checks exceed their hard limit")
    normalized_checks.sort(
        key=lambda item: (
            item["workflow_name"],
            item["check_name"],
            item["state"],
        )
    )
    normalized_items = [
        validate_pr_feedback_item(item) for item in items
    ]
    if len(normalized_items) > MAX_FEEDBACK_HARD_LIMIT:
        raise RuntimeContractError("feedback item hard limit exceeded")
    identities = [(item["kind"], item["id"]) for item in normalized_items]
    if identities != sorted(identities) or len(set(identities)) != len(identities):
        raise RuntimeContractError("cursor items must be sorted and unique")
    item_digest = canonical_sha256(normalized_items)
    check_digest = check_state_sha256(
        [
            {
                "name": (
                    f"{item['workflow_name']}::{item['check_name']}"
                ),
                "status": item["state"],
                "conclusion": item["bucket"],
            }
            for item in normalized_checks
        ]
    )
    mode = "complete" if len(normalized_items) <= MAX_FEEDBACK_ITEMS else "digest_only"
    result: dict[str, Any] = {
        "schema": PR_CURSOR_SCHEMA,
        "mode": mode,
        "repository": repository,
        "pull_number": pull_number,
        "head_oid": head_oid,
        "base_oid": base_oid,
        "pr_state": pr_state,
        "review_decision": review_decision,
        "merge_state_status": merge_state_status,
        "check_count": len(normalized_checks),
        "checks_sha256": check_digest,
        "item_count": len(normalized_items),
        "items_sha256": item_digest,
    }
    if mode == "complete":
        result["items"] = normalized_items
        if len(canonical_bytes(result)) > MAX_FEEDBACK_BYTES:
            result.pop("items")
            result["mode"] = "digest_only"
    return validate_pr_feedback_cursor(result)


def classify_pr_feedback(
    previous: Any,
    current: Mapping[str, Any] | str,
) -> dict[str, Any]:
    current = validate_pr_feedback_cursor(current)
    if current == "uninitialized":
        raise RuntimeContractError("current cursor must be materialized")
    previous = validate_pr_feedback_cursor(previous)
    if current["item_count"] > MAX_FEEDBACK_HARD_LIMIT:
        return {"transition": "feedback_hard_limit", "cursor": current}
    if previous == "uninitialized":
        transition = (
            "state_changed" if current["item_count"] else "still_waiting"
        )
    else:
        transition = (
            "still_waiting" if previous == current else "state_changed"
        )
    return {"transition": transition, "cursor": current}


def validate_expected_ci_checks(value: Mapping[str, Any]) -> dict[str, Any]:
    keys = {
        "schema",
        "repository",
        "project_commit",
        "runtime_source_envelope_digest",
        "checks",
    }
    data = _closed(value, keys, "expected CI checks")
    if set(data) != keys:
        raise RuntimeContractError("expected CI checks has missing fields")
    if data["schema"] != EXPECTED_CHECKS_SCHEMA:
        raise RuntimeContractError("unsupported expected CI checks schema")
    repository = _string(data["repository"], "expected.repository")
    if not REPOSITORY_RE.fullmatch(repository):
        raise RuntimeContractError("expected.repository has an invalid shape")
    commit = _commit(data["project_commit"], "expected.project_commit")
    envelope = _digest(
        data["runtime_source_envelope_digest"],
        "expected.runtime_source_envelope_digest",
    )
    checks = data["checks"]
    if not isinstance(checks, list) or not checks:
        raise RuntimeContractError("expected checks must be non-empty")
    if len(checks) > MAX_EXPECTED_CHECKS:
        raise RuntimeContractError("expected checks exceed their limit")
    normalized = []
    identities = []
    for index, value in enumerate(checks):
        item = _closed(
            value,
            {"workflow_name", "check_name", "allow_skipped"},
            f"expected.checks[{index}]",
        )
        if set(item) != {"workflow_name", "check_name", "allow_skipped"}:
            raise RuntimeContractError(
                f"expected.checks[{index}] has missing fields"
            )
        workflow = _check_name(
            item["workflow_name"],
            f"expected.checks[{index}].workflow_name",
        )
        name = _check_name(
            item["check_name"],
            f"expected.checks[{index}].check_name",
        )
        identity = (workflow, name)
        identities.append(identity)
        normalized.append(
            {
                "workflow_name": workflow,
                "check_name": name,
                "allow_skipped": _boolean(
                    item["allow_skipped"],
                    f"expected.checks[{index}].allow_skipped",
                ),
            }
        )
    if identities != sorted(identities) or len(set(identities)) != len(identities):
        raise RuntimeContractError("expected checks must be sorted and unique")
    result = {
        "schema": EXPECTED_CHECKS_SCHEMA,
        "repository": repository,
        "project_commit": commit,
        "runtime_source_envelope_digest": envelope,
        "checks": normalized,
    }
    if len(canonical_bytes(result)) > MAX_EXPECTED_CHECKS_BYTES:
        raise RuntimeContractError("expected CI checks exceed their byte limit")
    return result


def expected_ci_checks_sha256(value: Mapping[str, Any]) -> str:
    return canonical_sha256(validate_expected_ci_checks(value))


def _observed_check_sort_key(value: Mapping[str, Any]) -> tuple[str, ...]:
    return (
        value["workflow_name"],
        value["check_name"],
        value["bucket"],
        value["state"],
        "" if value["link"] is None else value["link"],
    )


def _expected_check_observation_metadata(
    normalized: Sequence[Mapping[str, Any]],
    expected_by_identity: Mapping[tuple[str, str], Mapping[str, Any]],
) -> dict[str, Any]:
    ordered = sorted(normalized, key=_observed_check_sort_key)
    expected_rows = [
        item
        for item in ordered
        if (item["workflow_name"], item["check_name"])
        in expected_by_identity
    ]
    unexpected_rows = [
        item
        for item in ordered
        if (item["workflow_name"], item["check_name"])
        not in expected_by_identity
    ]
    return {
        "expected_checks": expected_rows,
        "unexpected_check_count": len(unexpected_rows),
        "unexpected_checks_sha256": canonical_sha256(unexpected_rows),
    }


def _expected_check_result(
    transition: str,
    metadata: Mapping[str, Any],
    **extra: Any,
) -> dict[str, Any]:
    result = {"transition": transition}
    result.update(metadata)
    result.update(extra)
    return result


def _empty_expected_check_observation_metadata() -> dict[str, Any]:
    return {
        "expected_checks": [],
        "unexpected_check_count": 0,
        "unexpected_checks_sha256": canonical_sha256([]),
    }


def _project_observed_rows(observed: Sequence[Any]) -> list[dict[str, Any]]:
    fields = ("workflow_name", "check_name", "bucket", "state", "link")
    projected = []
    for item in observed:
        if isinstance(item, Mapping):
            projected.append({field: item.get(field) for field in fields})
        else:
            projected.append({field: None for field in fields})
    return projected


def _observation_limit_value(
    receipt: RejectedObservationReceipt,
) -> dict[str, Any]:
    return _expected_check_result(
        "report_invalid",
        {
            "expected_checks": [],
            "unexpected_check_count": receipt.count,
            "unexpected_checks_sha256": receipt.sha256,
        },
        reason="observation_limit",
    )


def classify_expected_ci_checks_with_receipt(
    expected: Mapping[str, Any],
    observed: Sequence[Mapping[str, Any]],
    *,
    current_repository: str,
    current_head_oid: str,
    runtime_source_envelope_digest: str,
    expected_checks_digest: str,
) -> ExpectedCiClassification:
    expected = validate_expected_ci_checks(expected)
    current_repository = _string(current_repository, "current_repository")
    if not REPOSITORY_RE.fullmatch(current_repository):
        raise RuntimeContractError("current_repository has an invalid shape")
    current_head_oid = _commit(current_head_oid, "current_head_oid")
    runtime_source_envelope_digest = _digest(
        runtime_source_envelope_digest,
        "runtime_source_envelope_digest",
    )
    expected_checks_digest = _digest(
        expected_checks_digest,
        "expected_checks_sha256",
    )
    empty_metadata = _empty_expected_check_observation_metadata()
    expected_object_digest = expected_ci_checks_sha256(expected)
    if expected["repository"] != current_repository:
        return ExpectedCiClassification(
            "ordinary",
            _expected_check_result("expected_contract_invalid", empty_metadata),
            None,
            None,
        )
    if expected["project_commit"] != current_head_oid:
        return ExpectedCiClassification(
            "ordinary",
            _expected_check_result("source_changed", empty_metadata),
            None,
            None,
        )
    if (
        expected["runtime_source_envelope_digest"]
        != runtime_source_envelope_digest
    ):
        return ExpectedCiClassification(
            "ordinary",
            _expected_check_result("expected_contract_invalid", empty_metadata),
            None,
            None,
        )
    if expected_object_digest != expected_checks_digest:
        return ExpectedCiClassification(
            "ordinary",
            _expected_check_result("expected_contract_invalid", empty_metadata),
            None,
            None,
        )
    expected_by_identity = {
        (item["workflow_name"], item["check_name"]): item
        for item in expected["checks"]
    }
    if not isinstance(observed, Sequence) or isinstance(observed, (str, bytes)):
        raise RuntimeContractError("observed checks must be a sequence")
    projected = _project_observed_rows(observed)
    projected_receipt = _bounded_canonical_observation(
        projected,
        "projected_rows",
    )
    if isinstance(projected_receipt, RejectedObservationHardLimit):
        return ExpectedCiClassification(
            "hard_limit",
            None,
            projected_receipt,
            None,
        )
    try:
        normalized = [
            _validate_observed_check(item, f"observed[{index}]")
            for index, item in enumerate(observed)
        ]
    except RuntimeContractError as error:
        return ExpectedCiClassification(
            "grammar_invalid",
            None,
            projected_receipt,
            None,
            error,
        )
    ordered = sorted(normalized, key=_observed_check_sort_key)
    expected_rows = [
        item
        for item in ordered
        if (item["workflow_name"], item["check_name"]) in expected_by_identity
    ]
    unexpected_rows = [
        item
        for item in ordered
        if (item["workflow_name"], item["check_name"]) not in expected_by_identity
    ]
    unexpected_receipt = _bounded_canonical_observation(
        unexpected_rows,
        "unexpected_rows",
    )
    if isinstance(unexpected_receipt, RejectedObservationHardLimit):
        return ExpectedCiClassification(
            "hard_limit",
            None,
            projected_receipt,
            unexpected_receipt,
        )
    metadata = {
        "expected_checks": expected_rows,
        "unexpected_check_count": unexpected_receipt.count,
        "unexpected_checks_sha256": unexpected_receipt.sha256,
    }
    identities = [
        (item["workflow_name"], item["check_name"]) for item in normalized
    ]
    if unexpected_receipt.count > MAX_CI_UNEXPECTED_OBSERVATIONS:
        return ExpectedCiClassification(
            "observation_limit",
            _observation_limit_value(unexpected_receipt),
            projected_receipt,
            unexpected_receipt,
        )
    if len(identities) != len(set(identities)):
        return ExpectedCiClassification(
            "ordinary",
            _expected_check_result("duplicate_observed_check", metadata),
            projected_receipt,
            None,
        )
    observed_by_identity = dict(zip(identities, normalized))
    if not observed:
        value = _expected_check_result("no_checks_reported", metadata)
        return ExpectedCiClassification("ordinary", value, projected_receipt, None)
    missing = sorted(set(expected_by_identity) - set(observed_by_identity))
    if missing:
        value = _expected_check_result(
            "expected_check_missing", metadata, missing=missing
        )
        return ExpectedCiClassification("ordinary", value, projected_receipt, None)
    for identity, item in observed_by_identity.items():
        if identity not in expected_by_identity:
            continue
        if item["bucket"] == "fail" or item["bucket"] == "cancel":
            value = _expected_check_result(
                "expected_check_failed", metadata, check=identity
            )
            return ExpectedCiClassification("ordinary", value, projected_receipt, None)
        if item["bucket"] == "skipping" and not expected_by_identity[identity]["allow_skipped"]:
            value = _expected_check_result(
                "expected_check_skipped", metadata, check=identity
            )
            return ExpectedCiClassification("ordinary", value, projected_receipt, None)
        if item["bucket"] == "pending":
            value = _expected_check_result(
                "pending_limit", metadata, check=identity
            )
            return ExpectedCiClassification("ordinary", value, projected_receipt, None)
    value = _expected_check_result(
        "all_expected_checks_terminal_green", metadata
    )
    return ExpectedCiClassification("ordinary", value, projected_receipt, None)


def classify_expected_ci_checks(
    expected: Mapping[str, Any],
    observed: Sequence[Mapping[str, Any]],
    *,
    current_repository: str,
    current_head_oid: str,
    runtime_source_envelope_digest: str,
    expected_checks_digest: str,
) -> dict[str, Any]:
    classification = classify_expected_ci_checks_with_receipt(
        expected,
        observed,
        current_repository=current_repository,
        current_head_oid=current_head_oid,
        runtime_source_envelope_digest=runtime_source_envelope_digest,
        expected_checks_digest=expected_checks_digest,
    )
    if classification.state == "grammar_invalid":
        if classification.grammar_error is not None:
            raise classification.grammar_error
        raise RuntimeContractError("observed checks have invalid grammar")
    if classification.state == "hard_limit":
        raise RuntimeContractError("observations exceed their canonical byte limit")
    if classification.value is None:
        raise RuntimeContractError("classification has no ordinary value")
    return classification.materialize_value()


CI_REPORT_REASONS = {
    "all_expected_checks_terminal_green",
    "expected_check_missing",
    "expected_check_failed",
    "expected_check_skipped",
    "pending_limit",
    "duplicate_observed_check",
    "no_checks_reported",
    "github_query_failed",
    "expected_contract_invalid",
    "report_invalid",
}
CI_ERROR_CODES = {
    "github_query_failed",
    "github_output_invalid",
    "expected_contract_invalid",
    "report_invalid",
    "observation_limit",
    "hard_limit",
}


def _validate_ci_attempt(value: Mapping[str, Any], label: str) -> dict[str, Any]:
    keys = {
        "sequence",
        "head_oid",
        "base_oid",
        "reason",
        "watcher_exit_code",
        "expected_checks",
        "unexpected_check_count",
        "unexpected_checks_sha256",
        "retry",
        "safe_error",
    }
    data = _closed(value, keys, label)
    if set(data) != keys:
        raise RuntimeContractError(f"{label} has missing fields")
    sequence = _integer(data["sequence"], f"{label}.sequence", minimum=1)
    if sequence > 2147483647:
        raise RuntimeContractError(f"{label}.sequence is too large")
    reason = _string(data["reason"], f"{label}.reason")
    if reason not in CI_REPORT_REASONS:
        raise RuntimeContractError(f"{label}.reason is unsupported")
    watcher_exit = data["watcher_exit_code"]
    if watcher_exit is not None:
        watcher_exit = _integer(
            watcher_exit,
            f"{label}.watcher_exit_code",
            minimum=-255,
        )
        if watcher_exit > 255:
            raise RuntimeContractError(f"{label}.watcher_exit_code is too large")
    observations = data["expected_checks"]
    if not isinstance(observations, list):
        raise RuntimeContractError(f"{label}.expected_checks must be an array")
    raw_safe_error = data["safe_error"]
    raw_error_code = (
        raw_safe_error.get("code")
        if isinstance(raw_safe_error, Mapping)
        else None
    )
    unexpected_count = _integer(
        data["unexpected_check_count"],
        f"{label}.unexpected_check_count",
    )
    exceptional_observation_attempt = (
        raw_error_code == "observation_limit"
        and unexpected_count > MAX_CI_UNEXPECTED_OBSERVATIONS
    )
    hard_limit_attempt = (
        reason == "report_invalid" and raw_error_code == "hard_limit"
    )
    if (
        (exceptional_observation_attempt or hard_limit_attempt)
        and reason != "report_invalid"
    ):
        raise RuntimeContractError(
            f"{label}.bounded observation errors require report_invalid"
        )
    special_observation_attempt = (
        reason == "report_invalid"
        and (exceptional_observation_attempt or hard_limit_attempt)
    )
    if special_observation_attempt and observations:
        raise RuntimeContractError(
            f"{label}.expected_checks must be empty for a bounded observation error"
        )
    if not special_observation_attempt and len(observations) > MAX_CI_EXPECTED_OBSERVATIONS:
        raise RuntimeContractError(f"{label}.expected_checks exceeds its limit")
    normalized_observations = [
        _validate_observed_check(item, f"{label}.expected_checks[{index}]")
        for index, item in enumerate(observations)
    ]
    identities = [
        (item["workflow_name"], item["check_name"])
        for item in normalized_observations
    ]
    if identities != sorted(identities) or (
        len(set(identities)) != len(identities)
        and reason != "duplicate_observed_check"
    ):
        raise RuntimeContractError(
            f"{label}.expected_checks must be sorted and unique"
        )
    retry = data["retry"]
    if retry is not None:
        retry_data = _closed(
            retry,
            {"job_id", "failure_fingerprint_sha256"},
            f"{label}.retry",
        )
        if set(retry_data) != {"job_id", "failure_fingerprint_sha256"}:
            raise RuntimeContractError(f"{label}.retry has missing fields")
        retry = {
            "job_id": _integer(
                retry_data["job_id"],
                f"{label}.retry.job_id",
                minimum=1,
            ),
            "failure_fingerprint_sha256": _digest(
                retry_data["failure_fingerprint_sha256"],
                f"{label}.retry.failure_fingerprint_sha256",
            ),
        }
        if retry["job_id"] > 9223372036854775807:
            raise RuntimeContractError(f"{label}.retry.job_id is too large")
    safe_error = data["safe_error"]
    if safe_error is not None:
        error_data = _closed(
            safe_error,
            {"code", "exit_code", "stdout_sha256", "stderr_sha256"},
            f"{label}.safe_error",
        )
        if set(error_data) != {"code", "exit_code", "stdout_sha256", "stderr_sha256"}:
            raise RuntimeContractError(f"{label}.safe_error has missing fields")
        error_code = _string(error_data["code"], f"{label}.safe_error.code")
        if error_code not in CI_ERROR_CODES:
            raise RuntimeContractError(f"{label}.safe_error.code is unsupported")
        error_exit = error_data["exit_code"]
        if error_exit is not None:
            error_exit = _integer(
                error_exit,
                f"{label}.safe_error.exit_code",
                minimum=-255,
            )
            if error_exit > 255:
                raise RuntimeContractError(
                    f"{label}.safe_error.exit_code is too large"
                )
        safe_error = {
            "code": error_code,
            "exit_code": error_exit,
            "stdout_sha256": _digest(
                error_data["stdout_sha256"],
                f"{label}.safe_error.stdout_sha256",
            ),
            "stderr_sha256": _digest(
                error_data["stderr_sha256"],
                f"{label}.safe_error.stderr_sha256",
            ),
        }
    if special_observation_attempt and retry is not None:
        raise RuntimeContractError(
            f"{label}.retry must be null for a bounded observation error"
        )
    if (
        special_observation_attempt
        and safe_error is not None
        and safe_error["code"] == "hard_limit"
        and unexpected_count != 0
    ):
        raise RuntimeContractError(
            f"{label}.unexpected_check_count must be zero for hard_limit"
        )
    if (
        special_observation_attempt
        and safe_error is not None
        and safe_error["code"] == "hard_limit"
    ):
        if data["unexpected_checks_sha256"] != canonical_sha256([]):
            raise RuntimeContractError(
                f"{label}.unexpected_checks_sha256 must represent empty observations"
            )
        empty_output_digest = sha256_bytes(b"")
        if safe_error["stdout_sha256"] != empty_output_digest:
            raise RuntimeContractError(
                f"{label}.safe_error.stdout_sha256 must represent empty output"
            )
        if safe_error["stderr_sha256"] != empty_output_digest:
            raise RuntimeContractError(
                f"{label}.safe_error.stderr_sha256 must represent empty output"
            )
    if special_observation_attempt and unexpected_count > 2147483647:
        raise RuntimeContractError(
            f"{label}.unexpected_check_count is too large"
        )
    if not special_observation_attempt and unexpected_count > MAX_CI_UNEXPECTED_OBSERVATIONS:
        raise RuntimeContractError(
            f"{label}.unexpected_check_count is too large"
        )
    result = {
        "sequence": sequence,
        "head_oid": _commit(data["head_oid"], f"{label}.head_oid"),
        "base_oid": _commit(data["base_oid"], f"{label}.base_oid"),
        "reason": reason,
        "watcher_exit_code": watcher_exit,
        "expected_checks": normalized_observations,
        "unexpected_check_count": unexpected_count,
        "unexpected_checks_sha256": _digest(
            data["unexpected_checks_sha256"],
            f"{label}.unexpected_checks_sha256",
        ),
        "retry": retry,
        "safe_error": safe_error,
    }
    if len(canonical_bytes(result)) > MAX_CI_ATTEMPT_BYTES:
        raise CiAttemptSizeLimit(
            f"{label} exceeds the {MAX_CI_ATTEMPT_BYTES}-byte limit"
        )
    return result


def validate_ci_report(value: Mapping[str, Any]) -> dict[str, Any]:
    keys = {
        "schema",
        "mode",
        "repository",
        "pull_number",
        "runtime_source_envelope_digest",
        "expected_ci_checks_sha256",
        "discarded_attempt_count",
        "discarded_attempts_sha256",
        "attempts",
    }
    data = _closed(value, keys, "CI report")
    if set(data) != keys:
        raise RuntimeContractError("CI report has missing fields")
    if data["schema"] != CI_REPORT_SCHEMA:
        raise RuntimeContractError("unsupported CI report schema")
    mode = _string(data["mode"], "CI report.mode")
    if mode not in {"expected-v1", "legacy-schema3-observed-checks-v1"}:
        raise RuntimeContractError("CI report.mode is unsupported")
    repository = _string(data["repository"], "CI report.repository")
    if not REPOSITORY_RE.fullmatch(repository):
        raise RuntimeContractError("CI report.repository has an invalid shape")
    pull = _integer(data["pull_number"], "CI report.pull_number", minimum=1)
    if pull > 2147483647:
        raise RuntimeContractError("CI report.pull_number is too large")
    envelope = data["runtime_source_envelope_digest"]
    if envelope is not None:
        envelope = _digest(envelope, "CI report.runtime_source_envelope_digest")
    expected_digest = data["expected_ci_checks_sha256"]
    if expected_digest is not None:
        expected_digest = _digest(
            expected_digest,
            "CI report.expected_ci_checks_sha256",
        )
    if mode == "expected-v1" and (
        envelope is None or expected_digest is None
    ):
        raise RuntimeContractError(
            "expected-v1 CI reports require runtime and expected-check digests"
        )
    if mode == "legacy-schema3-observed-checks-v1" and (
        envelope is not None or expected_digest is not None
    ):
        raise RuntimeContractError(
            "legacy CI reports must not carry v2 authority digests"
        )
    discarded_count = _integer(
        data["discarded_attempt_count"],
        "CI report.discarded_attempt_count",
    )
    discarded_digest = _digest(
        data["discarded_attempts_sha256"],
        "CI report.discarded_attempts_sha256",
    )
    attempts = data["attempts"]
    if not isinstance(attempts, list) or not attempts:
        raise RuntimeContractError("CI report attempts must be non-empty")
    normalized = [
        _validate_ci_attempt(item, f"CI report.attempts[{index}]")
        for index, item in enumerate(attempts)
    ]
    if [item["sequence"] for item in normalized] != list(
        range(normalized[0]["sequence"], normalized[0]["sequence"] + len(normalized))
    ):
        raise RuntimeContractError("CI report attempt sequences are not contiguous")
    if normalized[0]["sequence"] < 1:
        raise RuntimeContractError("CI report attempt sequence is invalid")
    if normalized[0]["sequence"] != discarded_count + 1:
        raise RuntimeContractError(
            "CI report attempt sequence does not match discarded count"
        )
    result = {
        "schema": CI_REPORT_SCHEMA,
        "mode": mode,
        "repository": repository,
        "pull_number": pull,
        "runtime_source_envelope_digest": envelope,
        "expected_ci_checks_sha256": expected_digest,
        "discarded_attempt_count": discarded_count,
        "discarded_attempts_sha256": discarded_digest,
        "attempts": normalized,
    }
    if len(normalized) > MAX_CI_ATTEMPTS:
        raise RuntimeContractError("CI report retains too many attempts")
    if len(canonical_bytes(result)) > MAX_CI_REPORT_BYTES:
        raise RuntimeContractError("CI report exceeds its byte limit")
    return result


def discarded_attempt_digest(
    previous_digest: str,
    attempt: Mapping[str, Any],
) -> str:
    previous_digest = _digest(previous_digest, "previous discarded digest")
    attempt_bytes = canonical_bytes(_validate_ci_attempt(attempt, "attempt"))
    return sha256_bytes(
        b"github-ci-discarded-v1\0"
        + bytes.fromhex(previous_digest)
        + attempt_bytes
    )


def make_observation_limit_attempt(
    *,
    sequence: int,
    head_oid: str,
    base_oid: str,
    receipt: RejectedObservationReceipt,
    watcher_exit_code: int | None = None,
) -> dict[str, Any]:
    if not isinstance(receipt, RejectedObservationReceipt):
        raise RuntimeContractError("observation limit requires a receipt")
    return _validate_ci_attempt(
        {
            "sequence": sequence,
            "head_oid": head_oid,
            "base_oid": base_oid,
            "reason": "report_invalid",
            "watcher_exit_code": watcher_exit_code,
            "expected_checks": [],
            "unexpected_check_count": receipt.count,
            "unexpected_checks_sha256": receipt.sha256,
            "retry": None,
            "safe_error": {
                "code": "observation_limit",
                "exit_code": watcher_exit_code,
                "stdout_sha256": sha256_bytes(b""),
                "stderr_sha256": sha256_bytes(b""),
            },
        },
        "observation_limit_attempt",
    )


def make_observation_hard_limit_attempt(
    *,
    sequence: int,
    head_oid: str,
    base_oid: str,
    hard_limit: RejectedObservationHardLimit,
    watcher_exit_code: int | None = None,
) -> dict[str, Any]:
    if not isinstance(hard_limit, RejectedObservationHardLimit):
        raise RuntimeContractError("hard limit requires a hard-limit receipt")
    empty_digest = canonical_sha256([])
    empty_output_digest = sha256_bytes(b"")
    return _validate_ci_attempt(
        {
            "sequence": sequence,
            "head_oid": head_oid,
            "base_oid": base_oid,
            "reason": "report_invalid",
            "watcher_exit_code": watcher_exit_code,
            "expected_checks": [],
            "unexpected_check_count": 0,
            "unexpected_checks_sha256": empty_digest,
            "retry": None,
            "safe_error": {
                "code": "hard_limit",
                "exit_code": watcher_exit_code,
                "stdout_sha256": empty_output_digest,
                "stderr_sha256": empty_output_digest,
            },
        },
        "observation_hard_limit_attempt",
    )


def prepare_ci_attempt(
    value: Mapping[str, Any],
    projected_receipt: RejectedObservationReceipt,
) -> dict[str, Any]:
    if not isinstance(projected_receipt, RejectedObservationReceipt):
        raise RuntimeContractError("attempt preparation requires a receipt")
    if projected_receipt.source != "projected_rows":
        raise RuntimeContractError(
            "attempt preparation requires a projected_rows receipt"
        )
    try:
        return _validate_ci_attempt(value, "attempt")
    except CiAttemptSizeLimit:
        return make_observation_limit_attempt(
            sequence=value["sequence"],
            head_oid=value["head_oid"],
            base_oid=value["base_oid"],
            receipt=projected_receipt,
            watcher_exit_code=value.get("watcher_exit_code"),
        )


def make_report_invalid_attempt(
    *,
    sequence: int,
    head_oid: str,
    base_oid: str,
    raw_observations: Sequence[Any],
    watcher_exit_code: int | None = None,
    stdout: bytes = b"",
    stderr: bytes = b"",
) -> dict[str, Any]:
    """Build the bounded fail-closed attempt for rejected observations."""

    sequence = _integer(sequence, "sequence", minimum=1)
    if sequence > 2147483647:
        raise RuntimeContractError("sequence is too large")
    head_oid = _commit(head_oid, "head_oid")
    base_oid = _commit(base_oid, "base_oid")
    if watcher_exit_code is not None:
        watcher_exit_code = _integer(
            watcher_exit_code,
            "watcher_exit_code",
            minimum=-255,
        )
        if watcher_exit_code > 255:
            raise RuntimeContractError("watcher_exit_code is too large")
    if not isinstance(raw_observations, Sequence) or isinstance(
        raw_observations,
        (str, bytes),
    ):
        raise RuntimeContractError("raw_observations must be a sequence")
    if not isinstance(stdout, bytes) or not isinstance(stderr, bytes):
        raise RuntimeContractError("watcher output must be bytes")
    count = len(raw_observations)
    if count > MAX_CI_UNEXPECTED_OBSERVATIONS:
        raise RuntimeContractError("raw observations exceed their hard limit")
    return _validate_ci_attempt(
        {
            "sequence": sequence,
            "head_oid": head_oid,
            "base_oid": base_oid,
            "reason": "report_invalid",
            "watcher_exit_code": watcher_exit_code,
            "expected_checks": [],
            "unexpected_check_count": count,
            "unexpected_checks_sha256": canonical_sha256(
                list(raw_observations)
            ),
            "retry": None,
            "safe_error": {
                "code": "observation_limit",
                "exit_code": watcher_exit_code,
                "stdout_sha256": sha256_bytes(stdout),
                "stderr_sha256": sha256_bytes(stderr),
            },
        },
        "report_invalid_attempt",
    )


def _normalize_ci_attempt_for_build(
    value: Mapping[str, Any],
    label: str,
) -> dict[str, Any]:
    try:
        return _validate_ci_attempt(value, label)
    except RuntimeContractError:
        if isinstance(value, Mapping) and isinstance(
            value.get("expected_checks"),
            list,
        ):
            skeleton = dict(value)
            skeleton["expected_checks"] = []
            _validate_ci_attempt(skeleton, f"{label}.authority")
            sequence = value.get("sequence", 1)
            head_oid = value.get("head_oid", "0" * 40)
            base_oid = value.get("base_oid", "0" * 40)
            if (
                isinstance(sequence, int)
                and not isinstance(sequence, bool)
                and 1 <= sequence <= 2147483647
                and isinstance(head_oid, str)
                and isinstance(base_oid, str)
                and SHA1_RE.fullmatch(head_oid)
                and SHA1_RE.fullmatch(base_oid)
            ):
                return make_report_invalid_attempt(
                    sequence=sequence,
                    head_oid=head_oid,
                    base_oid=base_oid,
                    raw_observations=value["expected_checks"],
                    watcher_exit_code=(
                        value.get("watcher_exit_code")
                        if isinstance(value.get("watcher_exit_code"), int)
                        and not isinstance(value.get("watcher_exit_code"), bool)
                        else None
                    ),
                )
        raise


def _bound_ci_report(
    *,
    mode: str,
    repository: str,
    pull_number: int,
    runtime_source_envelope_digest: str | None,
    expected_ci_checks_sha256: str | None,
    retained: list[dict[str, Any]],
    discarded_attempt_count: int,
    discarded_attempts_sha256: str,
) -> dict[str, Any]:
    if not retained:
        raise RuntimeContractError("CI report needs one current attempt")
    while True:
        first_sequence = discarded_attempt_count + 1
        for index, item in enumerate(retained, start=first_sequence):
            if item["sequence"] != index:
                raise RuntimeContractError(
                    "CI report attempts must have contiguous global sequences"
                )
        report = {
            "schema": CI_REPORT_SCHEMA,
            "mode": mode,
            "repository": repository,
            "pull_number": pull_number,
            "runtime_source_envelope_digest": runtime_source_envelope_digest,
            "expected_ci_checks_sha256": expected_ci_checks_sha256,
            "discarded_attempt_count": discarded_attempt_count,
            "discarded_attempts_sha256": discarded_attempts_sha256,
            "attempts": retained,
        }
        if (
            len(retained) <= MAX_CI_ATTEMPTS
            and len(canonical_bytes(report)) <= MAX_CI_REPORT_BYTES
        ):
            return validate_ci_report(report)
        if len(retained) == 1:
            raise RuntimeContractError(
                "latest CI attempt cannot fit the report limit"
            )
        discarded_attempts_sha256 = discarded_attempt_digest(
            discarded_attempts_sha256,
            retained.pop(0),
        )
        discarded_attempt_count += 1


def build_ci_report(
    *,
    mode: str,
    repository: str,
    pull_number: int,
    runtime_source_envelope_digest: str | None,
    expected_ci_checks_sha256: str | None,
    attempts: Sequence[Mapping[str, Any]],
    discarded_attempts: Sequence[Mapping[str, Any]] = (),
) -> dict[str, Any]:
    normalized_discarded = [
        _validate_ci_attempt(item, f"discarded_attempts[{index}]")
        for index, item in enumerate(discarded_attempts)
    ]
    if [
        item["sequence"] for item in normalized_discarded
    ] != list(range(1, len(normalized_discarded) + 1)):
        raise RuntimeContractError(
            "discarded attempts must have contiguous initial sequences"
        )
    normalized_attempts = [
        _normalize_ci_attempt_for_build(item, f"attempts[{index}]")
        for index, item in enumerate(attempts)
    ]
    if not normalized_attempts:
        raise RuntimeContractError("CI report needs one current attempt")
    discarded_digest = "0" * 64
    for item in normalized_discarded:
        discarded_digest = discarded_attempt_digest(discarded_digest, item)
    return _bound_ci_report(
        mode=mode,
        repository=repository,
        pull_number=pull_number,
        runtime_source_envelope_digest=runtime_source_envelope_digest,
        expected_ci_checks_sha256=expected_ci_checks_sha256,
        retained=list(normalized_attempts),
        discarded_attempt_count=len(normalized_discarded),
        discarded_attempts_sha256=discarded_digest,
    )


def append_ci_report_attempt(
    previous_report: Mapping[str, Any],
    attempt: Mapping[str, Any],
    *,
    discarded_attempts: Sequence[Mapping[str, Any]] = (),
) -> dict[str, Any]:
    """Append one watcher attempt while preserving the bounded history chain."""

    previous = (
        validate_ci_report_history(previous_report, discarded_attempts)
        if discarded_attempts
        else validate_ci_report(previous_report)
    )
    normalized = _normalize_ci_attempt_for_build(attempt, "attempt")
    if normalized["sequence"] != previous["attempts"][-1]["sequence"] + 1:
        raise RuntimeContractError(
            "appended CI attempt sequence is not the next global sequence"
        )
    return _bound_ci_report(
        mode=previous["mode"],
        repository=previous["repository"],
        pull_number=previous["pull_number"],
        runtime_source_envelope_digest=previous[
            "runtime_source_envelope_digest"
        ],
        expected_ci_checks_sha256=previous["expected_ci_checks_sha256"],
        retained=[*previous["attempts"], normalized],
        discarded_attempt_count=previous["discarded_attempt_count"],
        discarded_attempts_sha256=previous["discarded_attempts_sha256"],
    )


def validate_ci_report_history(
    value: Mapping[str, Any],
    discarded_attempts: Sequence[Mapping[str, Any]] = (),
) -> dict[str, Any]:
    report = validate_ci_report(value)
    discarded = "0" * 64
    count = 0
    for index, attempt in enumerate(discarded_attempts):
        normalized = _validate_ci_attempt(
            attempt,
            f"discarded_attempts[{index}]",
        )
        if normalized["sequence"] != index + 1:
            raise RuntimeContractError(
                "discarded attempt sequences are not contiguous"
            )
        discarded = discarded_attempt_digest(discarded, normalized)
        count += 1
    if count != report["discarded_attempt_count"]:
        raise RuntimeContractError("CI report discarded count is invalid")
    if discarded != report["discarded_attempts_sha256"]:
        raise RuntimeContractError("CI report discarded digest is invalid")
    return report


__all__ = [
    "CI_REPORT_SCHEMA",
    "CiAttemptSizeLimit",
    "ExpectedCiClassification",
    "EXPECTED_CHECKS_SCHEMA",
    "EXTERNAL_CAPTURE_SCHEMA",
    "MAX_CANONICAL_JSON_NESTING",
    "PR_CURSOR_SCHEMA",
    "RuntimeContractError",
    "RuntimeAuthorityBinding",
    "RuntimeExecutionContext",
    "RuntimeExternalRoot",
    "RejectedObservationHardLimit",
    "RejectedObservationReceipt",
    "SelectedRuntimeSourceInputs",
    "TERMINAL_MARKER_SCHEMA",
    "TERMINAL_SEAL_REQUEST_SCHEMA",
    "TERMINAL_SEAL_SCHEMA",
    "VERIFICATION_REPORT_SCHEMA",
    "build_ci_report",
    "append_ci_report_attempt",
    "build_terminal_marker",
    "build_terminal_seal_record",
    "canonical_bytes",
    "canonical_json_bytes",
    "canonical_sha256",
    "capture_runtime_source_envelope",
    "capture_runtime_authority_binding",
    "capture_runtime_execution_context",
    "check_state_sha256",
    "classify_expected_ci_checks",
    "classify_expected_ci_checks_with_receipt",
    "classify_ci_report",
    "classify_pr_feedback",
    "classify_terminal_state",
    "classify_verification_report",
    "discarded_attempt_digest",
    "expected_ci_checks_sha256",
    "make_observation_hard_limit_attempt",
    "make_observation_limit_attempt",
    "make_pr_feedback_cursor",
    "make_report_invalid_attempt",
    "parse_runtime_external_captures",
    "parse_canonical_json",
    "parse_terminal_marker_line",
    "prepare_ci_attempt",
    "revalidate_runtime_source_envelope",
    "sha256_bytes",
    "terminal_marker_line",
    "validate_ci_report",
    "validate_ci_report_history",
    "validate_captured_runtime_source_envelope",
    "validate_cleanup_report",
    "validate_expected_ci_checks",
    "validate_pr_feedback_cursor",
    "validate_pr_feedback_item",
    "validate_runtime_source_envelope",
    "validate_terminal_chain",
    "validate_terminal_marker",
    "validate_terminal_seal_record",
    "validate_terminal_seal_request",
    "validate_verification_report",
]
