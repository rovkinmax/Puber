---
description: Resolve a deterministic Puber release intent
---

# Release Branch

Do not switch, reset, rebase, fetch, or mutate a checkout from this command.
The release-intent script reads the current task worktree, validates the exact
non-default branch and semantic version intent, and emits a schema-4 operation
carrier. `prepare` may create or reuse only `release/<version>` at the exact
remote master OID; it must leave the checkout clean and create neither a
version commit nor a PR. Profile generation and finalization are prerequisites
for the later compliance and Ship stages.
