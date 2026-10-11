---
description: Conservative Puber task cleanup report with retained release proof
---

# Cleanup Task

Inspect only the exact Task worktree, branch, publication proof, and runtime
carrier. Before reading or requiring the release checkpoint, classify the full
Task runtime inventory and evidence history. Unknown entries, unsealed cleanup
state, partial/conflicting seals, unsafe checkpoint identity, or disagreement
between a seal and checkpoint block before writing a receipt, sealing, or
deleting anything.

For a new terminal cleanup, retain the exact checkpoint bytes (when present),
original input carrier and proofs, source-envelope digest, canonical report
prefix, and exact seal request in the bounded ignored
`build/kent-workflow/<task-short-id>/` receipt. Add a durable Task-comment
reference containing only the receipt path and SHA-256, then read it back
through the supported Task-comment interface before the final evidence seal.
The receipt is evidence retention, not user approval; never include Script
lease tokens or credentials. Keep the checkpoint until the single final seal
and completed receipt have both been read back.

A valid prior seal and a missing profile checkpoint permit only read-only
replay of the original retained input. Do not reconstruct missing proof from
the marker or mutable workspace. Preserve the original prefix, marker, carrier,
and source binding. Any missing, partial, conflicting, or unknown retention
state blocks before seal or deletion.

The schema-4 Janitor may delete only resources conclusively safe for the
selected cleanup mode; it never touches the primary checkout or live Kent
state. Preserve unrelated, dirty, unpushed, unmerged, and unclear resources.
