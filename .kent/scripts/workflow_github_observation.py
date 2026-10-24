"""Bounded, read-only GitHub observations shared by PR and release watchers."""
from __future__ import annotations

import hashlib
import io
import json
import os
from pathlib import Path
import re
import selectors
import shutil
import signal
import subprocess
import tempfile
import time
from email.utils import parsedate_to_datetime
from typing import Any, Callable
from urllib.parse import urlparse


MAX_QUERY_BYTES = 4 * 1024 * 1024
MAX_FEEDBACK_ITEMS = 1000
MAX_CHECKS = 10000
MAX_BODY_BYTES = 64 * 1024
GROUP_SETTLEMENT_SECONDS = 0.2
GROUP_SETTLEMENT_PROBES = 40
PR_FIELDS = (
    "state,mergedAt,mergeCommit,headRefName,headRefOid,baseRefName,baseRefOid,"
    "url,mergeable,mergeStateStatus,reviewDecision,statusCheckRollup"
)
CHECK_FIELDS = "name,workflow,event,bucket,state,link"
IDENTITY_FIELDS = ("url", "headRefOid", "baseRefOid", "headRefName", "baseRefName")


class ObservationError(RuntimeError):
    def __init__(self, code: str, *, exit_code: int | None = None,
                 stdout: bytes = b"", stderr: bytes = b"",
                 rate_limited: bool = False,
                 retry_after_seconds: int | None = None,
                 provider_retry_unknown: bool = False,
                 provider_retry_captured: bool = False,
                 provider_retry_observed_at_ns: int | None = None) -> None:
        super().__init__(code)
        self.code = code
        self.exit_code = exit_code
        self.stdout_sha256 = hashlib.sha256(stdout).hexdigest()
        self.stderr_sha256 = hashlib.sha256(stderr).hexdigest()
        self.rate_limited = rate_limited
        self.retry_after_seconds = retry_after_seconds
        self.provider_retry_unknown = provider_retry_unknown
        # These private fields are local coordination data only. They are
        # deliberately absent from as_dict(), exception text and all packets.
        self._provider_retry_captured = provider_retry_captured
        self._provider_retry_observed_at_ns = provider_retry_observed_at_ns

    def as_dict(self) -> dict[str, Any]:
        return {
            "code": self.code,
            "message": self.code,
            "stdout_sha256": self.stdout_sha256,
            "stderr_sha256": self.stderr_sha256,
        }

    def retry_delay_seconds(
        self,
        *,
        interval: int,
        failure_count: int,
    ) -> int:
        """Honor provider throttling without changing the observer packet."""
        if not self.rate_limited:
            return interval
        if self.retry_after_seconds is not None:
            return max(interval, self.retry_after_seconds)
        exponent = min(max(0, failure_count - 1), 6)
        backoff = min(60 * (2**exponent), 3600)
        return max(interval, backoff)


_HTTP_DEBUG_STATUS = re.compile(r"^<\s*HTTP/\S+\s+(\d{3})(?:\s|$)", re.IGNORECASE)
_HTTP_DEBUG_HEADER = re.compile(r"^<\s*([A-Za-z0-9-]+)\s*:\s*(.*?)\s*$")
_RATE_LIMIT_BODY = re.compile(
    r'^\s*(?:\{\s*)?"message"\s*:\s*".*(?:API rate limit exceeded|secondary rate limit|'
    r'rate limit exceeded|abuse detection)',
    re.IGNORECASE,
)
_PRIMARY_RATE_LIMIT_BODY = re.compile(
    r'^\s*(?:\{\s*)?"message"\s*:\s*".*API rate limit exceeded',
    re.IGNORECASE,
)
_SECONDARY_RATE_LIMIT_BODY = re.compile(
    r'^\s*(?:\{\s*)?"message"\s*:\s*".*(?:secondary rate limit|abuse detection)',
    re.IGNORECASE,
)
_RETRY_HEADERS = {
    "retry-after",
    "x-ratelimit-remaining",
    "x-ratelimit-reset",
    "date",
}


def provider_retry_constraint(
    error: BaseException,
    *,
    interval: int,
    failure_count: int,
) -> str | None:
    """Return a closed retry DTO for one captured provider failure.

    The observation timestamp is sampled at the provider transport boundary
    and stays private on ``ObservationError``. This helper never inspects raw
    stderr or serialized diagnostics. Callers supply only the existing local
    interval and failure index so secondary-limit timing matches that policy.
    """
    if not getattr(error, "_provider_retry_captured", False):
        return None
    observed_at_ns = getattr(error, "_provider_retry_observed_at_ns", None)
    rate_limited = getattr(error, "rate_limited", False) is True
    retry_unknown = getattr(error, "provider_retry_unknown", False) is True
    if not rate_limited and not retry_unknown:
        return None
    retry_is_known = (
        type(observed_at_ns) is int
        and observed_at_ns >= 0
        and rate_limited
        and not retry_unknown
    )
    if retry_is_known:
        try:
            delay = error.retry_delay_seconds(  # type: ignore[attr-defined]
                interval=interval,
                failure_count=failure_count,
            )
        except (AttributeError, TypeError, ValueError, OverflowError):
            retry_is_known = False
        else:
            retry_is_known = type(delay) is int and delay >= 0
    if retry_is_known:
        deadline_ms = (observed_at_ns + delay * 1_000_000_000 + 999_999) // 1_000_000
        if deadline_ms > 9_223_372_036_854_775_807:
            retry_is_known = False
        else:
            constraint = {
                "schema": "github-provider-retry-v1",
                "provider_host": "github.com",
                "kind": "not_before",
                "not_before_unix_ms": deadline_ms,
            }
    if not retry_is_known:
        constraint = {
            "schema": "github-provider-retry-v1",
            "provider_host": "github.com",
            "kind": "unknown",
        }
    return json.dumps(
        constraint,
        ensure_ascii=False,
        sort_keys=True,
        separators=(",", ":"),
        allow_nan=False,
    )


def _http_date_timestamp(value: str | None) -> float | None:
    if value is None or len(value) > 64:
        return None
    try:
        parsed = parsedate_to_datetime(value)
        if parsed.tzinfo is None:
            return None
        return parsed.timestamp()
    except (TypeError, ValueError, OverflowError):
        return None


def _provider_retry_metadata(
    stderr: bytes,
    *,
    now_unix_ns: int | None = None,
) -> tuple[bool | None, int | None]:
    """Extract allowlisted timing from one complete final gh API response."""
    current: dict[str, Any] | None = None
    final_response: dict[str, Any] | None = None
    line_count = 0
    for raw_line in io.BytesIO(stderr):
        line_count += 1
        if line_count > 65536 or len(raw_line) > 16 * 1024:
            return None, None
        line = raw_line.decode("utf-8", "replace").rstrip("\r\n")
        status_match = _HTTP_DEBUG_STATUS.match(line)
        if line.startswith("< HTTP/") and status_match is None:
            return None, None
        if status_match is not None:
            if current is not None:
                return None, None
            current = {
                "status": int(status_match.group(1)),
                "headers": {},
                "headers_complete": False,
                "complete": False,
                "body_rate_limited": False,
                "body_primary_rate_limited": False,
                "body_secondary_rate_limited": False,
            }
            final_response = current
            continue
        if current is None:
            continue
        if not current["headers_complete"]:
            header_match = _HTTP_DEBUG_HEADER.match(line)
            if header_match is not None:
                name = header_match.group(1).lower()
                value = header_match.group(2).strip()
                if name in _RETRY_HEADERS:
                    if len(value) > 64:
                        return None, None
                    current["headers"][name] = value
            elif not line.strip():
                current["headers_complete"] = True
            elif line.startswith("* Request took"):
                current = None
            continue
        if line.startswith("* Request took"):
            current["complete"] = True
            current = None
        else:
            current["body_rate_limited"] = (
                current["body_rate_limited"]
                or _RATE_LIMIT_BODY.search(line) is not None
            )
            current["body_primary_rate_limited"] = (
                current["body_primary_rate_limited"]
                or _PRIMARY_RATE_LIMIT_BODY.search(line) is not None
            )
            current["body_secondary_rate_limited"] = (
                current["body_secondary_rate_limited"]
                or _SECONDARY_RATE_LIMIT_BODY.search(line) is not None
            )

    # Partial/unparseable output never provides retry evidence. Requiring the
    # final response's normal request-completion marker also rejects truncation.
    if (
        final_response is None
        or current is not None
        or not final_response["headers_complete"]
        or not final_response["complete"]
    ):
        return None, None

    status = final_response["status"]
    headers = final_response["headers"]
    body_rate_limited = final_response["body_rate_limited"]
    body_primary_rate_limited = final_response["body_primary_rate_limited"]
    body_secondary_rate_limited = final_response["body_secondary_rate_limited"]
    local_now = (
        now_unix_ns / 1_000_000_000
        if type(now_unix_ns) is int and now_unix_ns >= 0
        else time.time()
    )
    provider_now = _http_date_timestamp(headers.get("date"))
    reference_now = (
        min(local_now, provider_now)
        if provider_now is not None
        else local_now
    )
    retry_after = headers.get("retry-after")
    retry_after_seconds = None
    retry_after_invalid = False
    if retry_after is not None:
        if re.fullmatch(r"[0-9]{1,10}", retry_after):
            retry_after_seconds = int(retry_after)
        else:
            retry_after_timestamp = _http_date_timestamp(retry_after)
            if retry_after_timestamp is None:
                retry_after_invalid = True
            else:
                retry_after_seconds = max(
                    0,
                    int(retry_after_timestamp - reference_now + 0.999),
                )
    remaining = headers.get("x-ratelimit-remaining")
    reset = headers.get("x-ratelimit-reset")
    reset_seconds = None
    reset_invalid = False
    if reset is not None:
        if re.fullmatch(r"[0-9]{1,10}", reset):
            reset_seconds = int(reset)
        else:
            reset_invalid = True
    failed_response = status >= 400 or body_rate_limited
    primary_limit = failed_response and (
        remaining == "0" or body_primary_rate_limited
    )
    secondary_limit = failed_response and body_secondary_rate_limited
    rate_limit_signal = failed_response and (
        status == 429 or primary_limit or secondary_limit or body_rate_limited
    )
    if (
        not rate_limit_signal
        and status >= 400
        and retry_after_seconds is not None
    ):
        rate_limit_signal = True
    if not rate_limit_signal:
        return False, None
    if retry_after_invalid or reset_invalid:
        return None, None
    if retry_after_seconds is not None:
        return True, retry_after_seconds

    if primary_limit:
        if reset_seconds is None:
            return None, None
        if headers.get("date") is not None and provider_now is None:
            return None, None
        return True, max(0, int(reset_seconds - reference_now + 0.999))
    if secondary_limit:
        return True, None
    # A bare 429 or unclassified rate-limit body does not distinguish primary
    # reset timing from the secondary-limit fallback. Do not guess a retry time.
    return None, None


def _object(value: Any) -> dict[str, Any]:
    if not isinstance(value, dict):
        raise ObservationError("invalid_observation")
    return value


def _array(value: Any, *, limit: int = MAX_CHECKS) -> list[Any]:
    if not isinstance(value, list):
        raise ObservationError("invalid_observation")
    if len(value) > limit:
        raise ObservationError("observation_limit")
    return value


def _text(value: Any, *, empty: bool = False, limit: int = 2048) -> str:
    if not isinstance(value, str) or (not empty and not value.strip()):
        raise ObservationError("invalid_observation")
    if len(value.encode("utf-8")) > limit:
        raise ObservationError("observation_limit")
    return value


def _optional_text(value: Any, *, limit: int = 2048) -> str:
    return "" if value is None else _text(value, empty=True, limit=limit)


def parse_pr_url(value: str) -> tuple[str, int]:
    parsed = urlparse(_text(value))
    match = re.fullmatch(r"/([A-Za-z0-9_.-]+)/([A-Za-z0-9_.-]+)/pull/([1-9][0-9]*)", parsed.path)
    if (parsed.scheme != "https" or parsed.netloc not in {"github.com", "www.github.com"}
            or parsed.query or parsed.fragment or not match):
        raise ObservationError("invalid_pr_identity")
    number = int(match[3])
    if number > 2147483647:
        raise ObservationError("invalid_pr_identity")
    return f"{match[1]}/{match[2]}", number


def resolve_gh_bin() -> str:
    configured = os.environ.get("KENT_GH_BIN")
    candidates = (configured,) if configured else ("gh", "/opt/homebrew/bin/gh", "/usr/local/bin/gh")
    for candidate in candidates:
        if candidate and (found := shutil.which(candidate)):
            return found
    raise ObservationError("github_cli_unavailable")


def _signal_owned_group(process: subprocess.Popen[bytes], operation: int) -> bool:
    try:
        os.killpg(process.pid, operation)
    except ProcessLookupError:
        return False
    except PermissionError as original:
        # A disappearing zombie-only group can report EPERM on Darwin.
        # Only an explicit subsequent ESRCH proves disappearance.
        deadline = time.monotonic() + GROUP_SETTLEMENT_SECONDS
        for _ in range(GROUP_SETTLEMENT_PROBES):
            try:
                process.poll()
            except OSError:
                raise original
            if time.monotonic() >= deadline:
                raise original
            try:
                os.killpg(process.pid, 0)
            except ProcessLookupError:
                try:
                    process.poll()
                except OSError:
                    raise original
                return False
            except PermissionError:
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    raise original
                time.sleep(min(0.005, remaining))
            except OSError:
                raise original
            else:
                raise original
        raise original
    return True


def _stop_process(process: subprocess.Popen[bytes]) -> None:
    # The group is owned by this invocation, including pipe-holding descendants.
    if not _signal_owned_group(process, signal.SIGTERM):
        process.poll()
        return
    try:
        process.wait(timeout=0.2)
    except subprocess.TimeoutExpired:
        pass
    if not _signal_owned_group(process, signal.SIGKILL):
        process.poll()
        return
    process.wait()


def _read_transport(
    executable: str,
    workspace: Path,
    args: list[str],
    *,
    timeout: float = 60,
    limit: int = MAX_QUERY_BYTES,
    input_bytes: bytes | None = None,
    accepted_exit_codes: tuple[int, ...] = (0,),
    capture_provider_retry: bool = False,
    provider_guard: Callable[[], None] | None = None,
) -> tuple[bytes, bytes]:
    if input_bytes is not None and len(input_bytes) > 1024 * 1024:
        raise ObservationError("observation_limit")
    with tempfile.TemporaryFile() as input_stream:
        if input_bytes is not None:
            input_stream.write(input_bytes)
            input_stream.seek(0)
        child_env = None
        if capture_provider_retry:
            child_env = os.environ.copy()
            child_env["GH_DEBUG"] = "api"
        if provider_guard is not None:
            provider_guard()
        process = subprocess.Popen(
            [executable, *args], cwd=workspace,
            stdin=input_stream if input_bytes is not None else subprocess.DEVNULL,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, start_new_session=True,
            env=child_env,
        )
    assert process.stdout is not None and process.stderr is not None
    output = {"stdout": bytearray(), "stderr": bytearray()}
    deadline = time.monotonic() + timeout
    failure = None
    with selectors.DefaultSelector() as selector:
        selector.register(process.stdout, selectors.EVENT_READ, "stdout")
        selector.register(process.stderr, selectors.EVENT_READ, "stderr")
        while selector.get_map():
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                failure = "query_timeout"
                break
            events = selector.select(remaining)
            for key, _ in events:
                chunk = os.read(key.fileobj.fileno(), 65536)
                if not chunk:
                    selector.unregister(key.fileobj)
                    continue
                retained = sum(map(len, output.values()))
                output[key.data].extend(chunk[:max(0, limit + 1 - retained)])
                if sum(map(len, output.values())) > limit:
                    failure = "observation_limit"
                    break
            if failure:
                break
    if failure is None:
        try:
            process.wait(timeout=max(0, deadline - time.monotonic()))
        except subprocess.TimeoutExpired:
            failure = "query_timeout"
    try:
        if failure:
            _stop_process(process)
        elif _signal_owned_group(process, 0):
            _stop_process(process)
            failure = "query_failed"
    finally:
        process.stdout.close()
        process.stderr.close()
    stdout, stderr = bytes(output["stdout"]), bytes(output["stderr"])
    if failure or process.returncode not in accepted_exit_codes:
        observed_at_ns = time.time_ns() if capture_provider_retry else None
        retry_evidence, retry_after = (
            _provider_retry_metadata(stderr, now_unix_ns=observed_at_ns)
            if capture_provider_retry
            else (False, None)
        )
        retry_unknown = capture_provider_retry and (
            retry_evidence is None or failure == "observation_limit"
        )
        rate_limited = retry_evidence is True and not retry_unknown
        raise ObservationError(
            failure or "query_failed", exit_code=process.returncode, stdout=stdout, stderr=stderr,
            rate_limited=rate_limited,
            retry_after_seconds=retry_after,
            provider_retry_unknown=retry_unknown,
            provider_retry_captured=capture_provider_retry,
            provider_retry_observed_at_ns=observed_at_ns,
        )
    return stdout, stderr


def read_bytes(
    executable: str,
    workspace: Path,
    args: list[str],
    *,
    timeout: float = 60,
    limit: int = MAX_QUERY_BYTES,
    input_bytes: bytes | None = None,
    accepted_exit_codes: tuple[int, ...] = (0,),
    capture_provider_retry: bool = False,
    provider_guard: Callable[[], None] | None = None,
) -> bytes:
    """Run a bounded command and return its raw stdout bytes."""
    stdout, _ = _read_transport(
        executable,
        workspace,
        args,
        timeout=timeout,
        limit=limit,
        input_bytes=input_bytes,
        accepted_exit_codes=accepted_exit_codes,
        capture_provider_retry=capture_provider_retry,
        provider_guard=provider_guard,
    )
    return stdout


def read_json(
    executable: str,
    workspace: Path,
    args: list[str],
    *,
    timeout: float = 60,
    limit: int = MAX_QUERY_BYTES,
    input_bytes: bytes | None = None,
    accepted_exit_codes: tuple[int, ...] = (0,),
    capture_provider_retry: bool = False,
    provider_guard: Callable[[], None] | None = None,
) -> Any:
    """Run bounded transport, then decode strict UTF-8 JSON."""
    stdout, stderr = _read_transport(
        executable,
        workspace,
        args,
        timeout=timeout,
        limit=limit,
        input_bytes=input_bytes,
        accepted_exit_codes=accepted_exit_codes,
        capture_provider_retry=capture_provider_retry,
        provider_guard=provider_guard,
    )
    try:
        return json.loads(stdout.decode("utf-8"))
    except (ValueError, UnicodeDecodeError, RecursionError) as error:
        observed_at_ns = time.time_ns() if capture_provider_retry else None
        raise ObservationError(
            "invalid_observation",
            stdout=stdout,
            stderr=stderr,
            provider_retry_unknown=capture_provider_retry,
            provider_retry_captured=capture_provider_retry,
            provider_retry_observed_at_ns=observed_at_ns,
        ) from error


def _graphql_data(value: Any) -> dict[str, Any]:
    value = _object(value)
    if value.get("errors"):
        raise ObservationError("query_failed")
    return _object(value.get("data"))


def _connection(value: Any) -> tuple[list[Any], bool, str | None]:
    value = _object(value)
    nodes = _array(value.get("nodes"), limit=MAX_FEEDBACK_ITEMS)
    info = value.get("pageInfo")
    if not isinstance(info, dict):
        raise ObservationError("incomplete_observation")
    more, cursor = info.get("hasNextPage"), info.get("endCursor")
    if type(more) is not bool or (cursor is not None and not isinstance(cursor, str)):
        raise ObservationError("incomplete_observation")
    if more and not cursor:
        raise ObservationError("incomplete_observation")
    return nodes, more, cursor


THREAD_QUERY = """
query($owner:String!, $name:String!, $number:Int!, $endCursor:String) {
  repository(owner:$owner, name:$name) {
    pullRequest(number:$number) {
      reviewThreads(first:100, after:$endCursor) {
        nodes {
          id isResolved isOutdated path line startLine originalLine originalStartLine subjectType
          comments(first:100) { nodes { id } pageInfo { hasNextPage endCursor } }
        }
        pageInfo { hasNextPage endCursor }
      }
    }
  }
}
"""
COMMENT_QUERY = """
query($threadId:ID!, $endCursor:String) {
  node(id:$threadId) {
    ... on PullRequestReviewThread {
      comments(first:100, after:$endCursor) { nodes { id } pageInfo { hasNextPage endCursor } }
    }
  }
}
"""


def _feedback_item(kind: str, raw: dict[str, Any], *, thread_id: str | None = None) -> dict[str, Any]:
    body = raw.get("body")
    body = "" if body is None else _text(body, empty=True, limit=MAX_BODY_BYTES)
    encoded = body.encode("utf-8")
    identifier = raw.get("id")
    if isinstance(identifier, bool) or not isinstance(identifier, (str, int)):
        raise ObservationError("invalid_observation")
    identifier = _text(str(identifier), limit=256)
    user = raw.get("user")
    author = None if user is None else _object(user).get("login")
    if author is not None:
        author = _text(author, limit=100)
    common = {
        "kind": kind, "id": identifier, "author_login": author,
        "body_bytes": len(encoded), "body_sha256": hashlib.sha256(encoded).hexdigest(),
    }
    if kind == "review":
        return {
            **common, "state": _text(raw.get("state")),
            "submitted_at": raw.get("submitted_at"),
            "updated_at": raw.get("updated_at") or raw.get("submitted_at"),
            "commit_oid": raw.get("commit_id"),
        }
    common.update(
        created_at=_text(raw.get("created_at")), updated_at=_text(raw.get("updated_at")),
    )
    if kind == "review_comment":
        if thread_id is None:
            raise ObservationError("incomplete_observation")
        common.update(
            thread_id=thread_id, current_commit_oid=raw.get("commit_id"),
            original_commit_oid=raw.get("original_commit_id"),
        )
    return common


class GitHubObserver:
    def __init__(self, workspace: Path, *, gh_bin: str | None = None,
                 timeout: float = 60, query: Callable[[list[str]], Any] | None = None,
                 provider_guard: Callable[[], None] | None = None) -> None:
        self.workspace = workspace
        self._subprocess_query = query is None
        self.timeout = timeout
        self.gh_bin = gh_bin if query else (gh_bin or resolve_gh_bin())
        self.query = query or self._query
        self.provider_guard = provider_guard

    def _query(self, args: list[str]) -> Any:
        assert self.gh_bin is not None
        return read_json(
            self.gh_bin,
            self.workspace,
            args,
            timeout=self.timeout,
            capture_provider_retry=True,
            provider_guard=self.provider_guard,
        )

    def read_pr(self, pr_url: str) -> dict[str, Any]:
        repository, number = parse_pr_url(pr_url)
        value = _object(self.query(["pr", "view", pr_url, "--json", PR_FIELDS]))
        if parse_pr_url(value.get("url")) != (repository, number):
            raise ObservationError("invalid_pr_identity")
        for key in ("headRefOid", "baseRefOid"):
            if not re.fullmatch(r"[0-9a-f]{40}", _text(value.get(key))):
                raise ObservationError("invalid_pr_identity")
        for key in ("headRefName", "baseRefName", "mergeable", "mergeStateStatus"):
            _text(value.get(key))
        if value.get("state") not in {"OPEN", "MERGED", "CLOSED"}:
            raise ObservationError("invalid_pr_identity")
        if value["state"] == "MERGED":
            _text(value.get("mergedAt"))
            if not re.fullmatch(r"[0-9a-f]{40}", _text(_object(value.get("mergeCommit")).get("oid"))):
                raise ObservationError("invalid_pr_identity")
        if "statusCheckRollup" not in value:
            raise ObservationError("incomplete_observation")
        rollup = value["statusCheckRollup"]
        if rollup is not None:
            _array(rollup)
        value["reviewDecision"] = _optional_text(value.get("reviewDecision"))
        return value

    def read_checks(self, pr_url: str) -> list[dict[str, Any]]:
        if self._subprocess_query:
            value = _array(read_json(
                self.gh_bin,
                self.workspace,
                ["pr", "checks", pr_url, "--json", CHECK_FIELDS],
                timeout=self.timeout,
                accepted_exit_codes=(0, 1, 8),
                capture_provider_retry=True,
                provider_guard=self.provider_guard,
            ))
        else:
            value = _array(self.query([
                "pr", "checks", pr_url, "--json", CHECK_FIELDS,
            ]))
        rows = []
        for item in value:
            item = _object(item)
            link = item.get("link")
            state = _text(item.get("state"), limit=64)
            bucket = _text(item.get("bucket"), limit=64)
            # gh v2.97.0 leaves STARTUP_FAILURE in its default pending bucket.
            # Preserve the actual failed state instead of reporting a false
            # pending check or allowing the runtime validator to reject it.
            if state.upper() == "STARTUP_FAILURE" and bucket.lower() == "pending":
                bucket = "fail"
            rows.append({
                "workflow_name": _optional_text(item.get("workflow"), limit=256),
                "check_name": _text(item.get("name"), limit=256),
                "event": _optional_text(item.get("event"), limit=256),
                "bucket": bucket,
                "state": state,
                "link": None if link in (None, "") else _text(link),
            })
        return rows

    def _rest_pages(self, endpoint: str) -> list[dict[str, Any]]:
        pages = _array(self.query(["api", "--paginate", "--slurp", endpoint]))
        if not pages:
            raise ObservationError("incomplete_observation")
        rows = []
        for page in pages:
            rows.extend(_object(row) for row in _array(page, limit=MAX_FEEDBACK_ITEMS))
            if len(rows) > MAX_FEEDBACK_ITEMS:
                raise ObservationError("observation_limit")
        return rows

    def _threads(self, repository: str, number: int) -> list[dict[str, Any]]:
        owner, name = repository.split("/")
        query_count = 0
        threads, page_cursors = [], set()
        count = 0
        cursor: str | None = None
        while True:
            if query_count >= MAX_FEEDBACK_ITEMS:
                raise ObservationError("observation_limit")
            args = [
                "api", "graphql", "-f", "query=" + THREAD_QUERY,
                "-F", "owner=" + owner, "-F", "name=" + name,
                "-F", "number=" + str(number),
                "-F", "endCursor=" + (cursor if cursor is not None else "null"),
            ]
            page = self.query(args)
            query_count += 1
            data = _graphql_data(page)
            pull = _object(_object(data.get("repository")).get("pullRequest"))
            nodes, more, cursor = _connection(pull.get("reviewThreads"))
            if more and cursor in page_cursors:
                raise ObservationError("incomplete_observation")
            if more:
                page_cursors.add(cursor)
            for node in nodes:
                node = _object(node)
                thread_id = _text(node.get("id"), limit=256)
                comments, next_page, next_cursor = _connection(node.get("comments"))
                comment_ids = [_text(_object(item).get("id"), limit=256) for item in comments]
                count += 1 + len(comment_ids)
                seen_cursors = set()
                while next_page:
                    if next_cursor in seen_cursors:
                        raise ObservationError("incomplete_observation")
                    if query_count >= MAX_FEEDBACK_ITEMS:
                        raise ObservationError("observation_limit")
                    seen_cursors.add(next_cursor)
                    query_count += 1
                    continuation = _graphql_data(self.query([
                        "api", "graphql", "-f", "query=" + COMMENT_QUERY,
                        "-F", "threadId=" + thread_id, "-F", "endCursor=" + next_cursor,
                    ]))
                    comments, next_page, next_cursor = _connection(
                        _object(continuation.get("node")).get("comments"),
                    )
                    comment_ids.extend(_text(_object(item).get("id"), limit=256) for item in comments)
                    count += len(comments)
                if count > MAX_FEEDBACK_ITEMS:
                    raise ObservationError("observation_limit")
                if len(comment_ids) != len(set(comment_ids)):
                    raise ObservationError("incomplete_observation")
                for key in ("isResolved", "isOutdated"):
                    if type(node.get(key)) is not bool:
                        raise ObservationError("invalid_observation")
                threads.append({
                    "kind": "review_thread", "id": thread_id,
                    "resolved": node["isResolved"], "outdated": node["isOutdated"],
                    "path": _text(node.get("path")), "subject_type": _text(node.get("subjectType")),
                    "current_line": node.get("line"), "current_start_line": node.get("startLine"),
                    "original_line": node.get("originalLine"),
                    "original_start_line": node.get("originalStartLine"),
                    "comment_ids": sorted(comment_ids),
                })
            if not more:
                break
        return threads

    def read_feedback(self, pr_url: str) -> list[dict[str, Any]]:
        repository, number = parse_pr_url(pr_url)
        comments = self._rest_pages(f"repos/{repository}/issues/{number}/comments?per_page=100")
        reviews = self._rest_pages(f"repos/{repository}/pulls/{number}/reviews?per_page=100")
        review_comments = self._rest_pages(f"repos/{repository}/pulls/{number}/comments?per_page=100")
        threads = self._threads(repository, number)
        thread_by_comment = {}
        for thread in threads:
            for comment_id in thread["comment_ids"]:
                if comment_id in thread_by_comment:
                    raise ObservationError("incomplete_observation")
                thread_by_comment[comment_id] = thread["id"]
        items = [_feedback_item("issue_comment", item) for item in comments]
        items.extend(_feedback_item("review", item) for item in reviews)
        items.extend(
            _feedback_item("review_comment", item, thread_id=thread_by_comment.get(item.get("node_id")))
            for item in review_comments
        )
        items.extend(threads)
        if len(items) + len(thread_by_comment) > MAX_FEEDBACK_ITEMS:
            raise ObservationError("observation_limit")
        identities = [(item["kind"], item["id"]) for item in items]
        if len(identities) != len(set(identities)):
            raise ObservationError("incomplete_observation")
        return sorted(items, key=lambda item: (item["kind"], item["id"]))

    def observe_pr(self, pr_url: str) -> dict[str, Any]:
        first = self.read_pr(pr_url)
        if first["state"] != "OPEN":
            return {"pr": first, "checks": [], "feedback_items": []}
        # gh pr checks exits with an unstructured error when no checks exist.
        # Structured PR absence avoids parsing localized stderr as policy.
        checks = self.read_checks(pr_url) if first["statusCheckRollup"] else []
        feedback = self.read_feedback(pr_url)
        last = self.read_pr(pr_url)
        if last["state"] == "MERGED":
            return {"pr": last, "checks": checks, "feedback_items": feedback}
        if any(first[key] != last[key] for key in IDENTITY_FIELDS):
            raise ObservationError("source_changed")
        if first["statusCheckRollup"] != last["statusCheckRollup"]:
            raise ObservationError("source_changed")
        return {"pr": last, "checks": checks, "feedback_items": feedback}

    def observe_run(self, repository: str, run_id: int, *, attempt: int | None = None) -> dict[str, Any]:
        if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
            raise ObservationError("invalid_run_identity")
        if type(run_id) is not int or run_id < 1:
            raise ObservationError("invalid_run_identity")
        endpoint = f"repos/{repository}/actions/runs/{run_id}"
        first = _object(self.query(["api", endpoint]))
        selected_attempt = first.get("run_attempt")
        if (first.get("id") != run_id or type(selected_attempt) is not int
                or selected_attempt < 1 or (attempt is not None and selected_attempt != attempt)):
            raise ObservationError("source_changed")
        pages = _array(self.query([
            "api", "--paginate", "--slurp",
            f"{endpoint}/attempts/{selected_attempt}/jobs?per_page=100",
        ]))
        jobs, total = [], None
        for page in pages:
            page = _object(page)
            count = page.get("total_count")
            if type(count) is not int or count < 0 or (total is not None and count != total):
                raise ObservationError("incomplete_observation")
            total = count
            jobs.extend(_object(job) for job in _array(page.get("jobs")))
            if len(jobs) > MAX_CHECKS:
                raise ObservationError("observation_limit")
        if total is None or len(jobs) != total:
            raise ObservationError("incomplete_observation")
        ids = [job.get("id") for job in jobs]
        if any(type(identifier) is not int for identifier in ids) or len(ids) != len(set(ids)):
            raise ObservationError("incomplete_observation")
        last = _object(self.query(["api", endpoint]))
        if any(first.get(key) != last.get(key) for key in ("id", "run_attempt", "head_sha", "event", "head_branch")):
            raise ObservationError("source_changed")
        return {"run": last, "jobs": jobs, "checks": [run_job_check(last, job) for job in jobs]}


def run_job_check(run: dict[str, Any], job: dict[str, Any]) -> dict[str, Any]:
    status = _text(job.get("status")).upper()
    state = _text(job.get("conclusion")).upper() if status == "COMPLETED" else status
    buckets = {
        "SUCCESS": "pass", "NEUTRAL": "skipping", "SKIPPED": "skipping",
        "FAILURE": "fail", "ERROR": "fail", "TIMED_OUT": "fail", "ACTION_REQUIRED": "fail",
        "STARTUP_FAILURE": "fail", "CANCELLED": "cancel",
        "PENDING": "pending", "QUEUED": "pending", "IN_PROGRESS": "pending",
        "WAITING": "pending", "REQUESTED": "pending", "EXPECTED": "pending", "STALE": "pending",
    }
    if state not in buckets:
        raise ObservationError("invalid_observation")
    return {
        "workflow_name": _optional_text(run.get("name"), limit=256),
        "check_name": _text(job.get("name"), limit=256),
        "event": _optional_text(run.get("event"), limit=256),
        "bucket": buckets[state], "state": state,
        "link": _text(job.get("html_url")) if job.get("html_url") else None,
    }
