# Mock-scenario evidence policy

This policy applies when a task changes behavior or fixtures in a way that
requires mock-backed or hermetic scenario coverage. The feature, bugfix,
refactor, migration, dependency, and test procedures refer here rather than
copying these rules.

## Plan and packet

For every changed behavior, record a stable behavior ID, changed source paths,
applicability and rationale, mock seam when applicable, selected test IDs,
changed assertion/fixture paths, concrete assertions, fixed harness reference,
and the eventual actual result. A fixture-only change must identify a
demonstrated consuming scenario. Reviewers own semantic adequacy.

Store the bounded `mock-scenario-evidence-v1` JSON packet in the ignored
`build/kent-workflow/` tree. Its closed schema is:

```json
{
  "schema": "mock-scenario-evidence-v1",
  "task_short_id": "PUB-84",
  "work_kind": "feature",
  "baseline": "<full immutable git commit>",
  "source_delta_sha256": "<sha256 of sorted source path/mode/content records>",
  "behaviors": [{
    "id": "stable-behavior-id",
    "source_paths": ["relative/changed/source"],
    "applicability": "applicable",
    "rationale": "why this behavior is or is not mock-backed",
    "mock_seam": "mock or fixture seam",
    "selected_test_ids": ["test.Class#selectedCase"],
    "assertion_paths": ["relative/changed/test-or-fixture"],
    "assertions": ["observable assertion"],
    "harness_ref": "fixed, descriptive harness command reference",
    "result": "pending",
    "alternative_checks": []
  }]
}
```

The complete `work_kind` enum is `feature`, `bugfix`, `refactor`, `migration`,
`dependency`, or `test`. `applicability` is exactly `applicable` or
`not_applicable`. `result` is `pending` before execution, then `passed` or
`failed`; unavailable execution blocks without issuing a receipt.

For `not_applicable`, provide a concrete harness limitation and suitable
alternative checks. Set `mock_seam` to `null`; list the selected synthetic
alternative-check test IDs in both `selected_test_ids` and
`alternative_checks`, so every alternative is executed and receipted.
Applicable behaviors require a mock seam and no alternative checks. The helper alone changes `pending` into the observed
`passed` or `failed` result for either applicability kind. `unavailable` is a
blocked result. Duplicate JSON keys, unknown keys
or enums, unsupported work kinds, malformed references, path escapes,
symlinks, excessive inputs, stale source digests, missing selected cases, and
skipped selected cases are rejected. Packet fields are data only: never execute
commands or paths read from a packet.

The source digest covers sorted changed and new uncommitted source paths,
including file mode and bytes. Generated build/evidence/log outputs and local
feature notes are excluded; changed source and test fixtures are not.

## Execution and handoff

Use `workflow-mock-scenario-evidence capture` immediately before running the
selected fixed harness. It records packet intent, source identity, existing
JUnit XML identities/digests, and log identity. Run the harness independently;
the helper never launches a packet-specified command. Then use `receipt` with
the actual process exit code and bounded JUnit/log outputs.

The helper CLI accepts only workspace-relative evidence paths below
`build/kent-workflow/`:

```text
workflow-mock-scenario-evidence validate-packet --workspace ROOT --task ID --work-kind KIND --baseline COMMIT --packet RELATIVE_PATH
workflow-mock-scenario-evidence capture --workspace ROOT --task ID --work-kind KIND --baseline COMMIT --packet RELATIVE_PATH --results-dir RELATIVE_DIR --log RELATIVE_PATH --output RELATIVE_PATH
workflow-mock-scenario-evidence receipt --workspace ROOT --capture RELATIVE_PATH --packet RELATIVE_PATH --results-dir RELATIVE_DIR --log RELATIVE_PATH --exit-code N --output RELATIVE_PATH
workflow-mock-scenario-evidence serialize-envelope --workspace ROOT --task ID --work-kind KIND --baseline COMMIT --packet RELATIVE_PATH --execution RELATIVE_PATH --narrative TEXT
workflow-mock-scenario-evidence validate-envelope --workspace ROOT --task ID --work-kind KIND < ENVELOPE_JSON
```

Capture accepts only `pending` behavior results; receipt derives and writes
the actual packet results. `serialize-envelope` emits the final envelope on
stdout. `validate-envelope` accepts that envelope on stdin and validates its
packet, receipt, source, JUnit, and log bindings. Command output is bounded;
exit status is `0` for pass, `1` for selected-test failure, and `2` for blocked
or invalid evidence.

An execution receipt uses the closed `mock-scenario-execution-v1` schema and
binds the pre/post source identity, packet, selected test IDs, completion,
JUnit XML identities/digests, and actual pass/fail/skip results. Use a fresh,
contained empty results directory, or let capture snapshot existing XML and
prove each selected case was newly produced. Reused unchanged XML cannot issue
a successful receipt. Source drift, changed evidence after receipt, stale or
missing logs, absent/disabled/unselected/skipped cases, and unavailable
execution block. A failed selected case fails even if a build or unrelated
tests pass.

Immediately before Implement/Fix verifier handoff, refresh evidence against
the final source delta, validate the packet and receipt, and serialize the
closed `mock-scenario-review-context-v1` envelope. The envelope contains
non-empty narrative plus relative packet and execution-receipt paths and
digests. Intermediate review-context narratives may remain plain text.
Missing/stale envelopes are writer-recoverable: recover the durable pointer,
refresh final-diff evidence, serialize again, and retry; they do not require
new user approval.

When no application behavior is mock-backed, record per-behavior
non-applicability and run suitable alternative checks, such as the local
synthetic policy/parser/wrapper scenarios for this tooling change. Device
execution is not a substitute and is not implicitly authorized.
