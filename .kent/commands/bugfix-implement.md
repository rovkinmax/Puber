---
description: Implement one approved bugfix plan step
---

# Bugfix Implementation

1. Read `AGENTS.md`, `.kent/project-contract.md`, the selected `plan.md`, and current task comments.
2. Select exactly one ready unchecked plan step and inspect preserved changes before editing.
3. Reproduce the defect or execute the plan's deterministic proxy before changing code when feasible.
4. Fix the proven root cause with the smallest coherent change. Do not broaden into unrelated cleanup.
5. Add or update focused regression tests and run the step's verification. In any worktree use `./tools/agentw`.
6. Mark the step `[x]` only after its focused checks pass.
7. Do not run nested final reviewers, commit, or push. The generated workflow owns review and delivery.

The generated Implement node owns transition parameters and continuation.
