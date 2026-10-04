---
description: Produce an implementation-ready plan for a reproducible defect
---

# Bugfix Planning

Plan a defect fix without editing production code.

1. Read `AGENTS.md`, `.kent/project-contract.md`, the task body, and current task comments.
2. Create or reuse `.todo/bugfix-<slug>/` without any global pointer file.
3. Record expected behavior, actual behavior, reproduction conditions, affected surfaces, and source evidence.
4. Establish the narrowest deterministic reproduction available: a failing test, command, log signature, or explicit
   runtime scenario. If reproduction requires unavailable access or a product decision, record the blocker instead of
   guessing.
5. Inspect the relevant implementation and comparison baseline, then separate proven facts from root-cause hypotheses.
6. Write `.todo/bugfix-<slug>/plan.md` with unchecked, independently verifiable writer-owned steps covering reproduction
   evidence, the minimal root-cause fix, regression tests, and deterministic verification.
7. For each behavior or fixture change, follow
   `.kent/commands/mock-scenario-policy.md` and inventory a stable behavior ID,
   anticipated source paths, applicability and rationale. Applicable behaviors
   need a mock seam, selected regression test IDs, assertion/fixture paths,
   concrete assertions and a fixed harness reference. For non-applicable
   behavior, record the concrete harness limitation and suitable
   alternative-check test IDs. Leave results pending until implementation
   evidence records actual execution; implementation reconciles the inventory
   against the final source delta.
8. When behavior can change, add a separate `## Workflow-owned verification` section describing the focused runtime
   Smoke scope and prerequisites without an implementation checkbox. Gate and Smoke own that work after verification.
9. Do not edit production files, commit, push, or invoke another workflow.

The generated Plan node owns routing and completion. This file is only the project bugfix planning procedure.
