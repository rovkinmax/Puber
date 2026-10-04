---
description: Implement one approved bugfix plan step
---

# Bugfix Implementation

1. Read `AGENTS.md`, `.kent/project-contract.md`, the selected `plan.md`, and current task comments.
2. Select exactly one ready unchecked writer-owned plan step and inspect preserved changes before editing.
3. Reproduce the defect or execute the plan's deterministic proxy before changing code when feasible.
4. Fix the proven root cause with the smallest coherent change. Do not broaden into unrelated cleanup.
5. For each behavior or fixture change, follow
   `.kent/commands/mock-scenario-policy.md` and reconcile the plan's behavior
   inventory against the final source delta. Add or update selected
   mock-backed regression scenarios and concrete assertions; a fixture-only
   change must identify a demonstrated consuming scenario.
6. Capture evidence immediately before the fixed harness, run the harness
   independently, then issue a receipt with its actual exit code, selected
   JUnit results and bounded log. Never execute packet-provided commands. For
   non-applicable behavior, execute every selected alternative check. Missing,
   stale, unavailable, skipped or missing selected-test output blocks; a
   selected test failure fails.
7. Mark the step `[x]` only after its focused checks pass.
8. Do not execute runtime Smoke or another workflow-owned review/delivery item. If a legacy plan renders one as an
   unchecked step, leave it unchanged, carry its scope into `review_context`, and advance to verification when no
   writer-owned step remains.
9. Immediately before the Implement/Fix verifier handoff, recover durable
   evidence paths, refresh and validate the packet and receipt against the final
   source delta, then serialize the closed review envelope into `review_context`.
   Missing or stale evidence is writer-recoverable: refresh and retry without a
   new approval.
10. Do not run nested final reviewers, commit, or push. The generated workflow owns review and delivery.

The generated Implement node owns transition parameters and continuation.
