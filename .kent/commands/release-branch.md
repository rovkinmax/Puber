---
description: Resolve a deterministic Puber release intent
---

# Release Branch

Do not switch, reset, rebase, fetch, or mutate a checkout from this command.
The release-intent script reads the current task worktree, validates the exact
non-default branch and semantic version intent, and emits a schema-4 operation
carrier. Version edits and commits belong to the approved implementation/ship
lane only.
