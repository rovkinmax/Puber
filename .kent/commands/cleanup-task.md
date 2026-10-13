---
description: Conservative Puber task cleanup report with retained release proof
---

# Cleanup Task

Inspect only the exact Task worktree, branch, publication proof, and runtime
carrier. Select the Engineering or Release protocol below from the actual
Workflow identity; do not mix their retention or checkpoint ownership.
Classify the ledger and runtime inventory before any mutation. Unknown or
unsafe entries, foreign ownership and conflicting evidence require preservation.

## Engineering

Use the existing evidence command and runtime-contract validators named by the
profile; this profile has no preparation helper. Follow the incoming Cleanup
seal/recovery instruction. An existing terminal seal allows only read-only
validation and reuse of its original request, operation reports, cleanup report
and marker. A final Cleanup event without a valid seal is partial history:
preserve it, do not append/reseal or reconstruct missing proof.

For a genuinely new Cleanup, retire an accepted-plan cache before the single
final event/seal:

1. Inspect `.kent/runtime/<task-short-id>/plan-contract.json` as a regular,
   privately owned, non-symlink file beneath the exact original Task root.
   Verify its schema, Task identity and normalized-plan digest using the
   existing plan-contract rules. Preserve its exact original bytes; a
   reserialized object is not an exact copy.
2. Retain those bytes, reversibly encoded, with their raw SHA-256, original
   Task/workspace identity and applicable original authority/review references
   in a native Task comment. The cache is not itself human approval. Do not
   invent missing approval, review counts or ScopeHash. Keep secrets out of
   comments; if safe complete retention is unavailable, preserve and block.
3. Read the exact new comment back by its real ID through the supported Task
   comment interface; decode and verify the retained bytes and digest. A
   comment containing only a path/digest inside this worktree does not survive
   worktree deletion and is insufficient.
4. Recheck the original file identity and bytes, then remove only that
   confirmed cache using descriptor-relative no-follow access. Use opened
   directory descriptors rooted at the canonical Task root, `O_NOFOLLOW`,
   `fstat`/`stat(..., follow_symlinks=False)` and `unlink(..., dir_fd=...)`;
   compare device/inode, ownership, private regular-file mode and exact bytes
   immediately before unlink, then fsync the parent directory. Do not remove
   any other runtime entry or introduce a preparation helper. Carry the
   original retention comment ID and digest in the cleanup report and evidence.

If interrupted before the final event, reuse the original verified comment:
when the cache remains, compare exact bytes before retiring it; when already
retired, reuse the retained bytes/proofs without recreating the cache. Missing
proof, changed bytes or unsafe identity blocks. A sealed ledger with a remaining
cache also blocks; do not modify sealed history to make cleanup pass.

Only after retention and all resource/publication obligations are settled,
retain the exact seal request and its actual operation/redaction reports in
the Task record and read them back. Preserve the incoming route's publication
or closure report/reason in this packet; absent carriers remain explicitly
not-applicable. Then record one final ordinary event and seal.
Use real current native Session/Run/Step identities. Do not append/seal merely
to report a blocker. Validate and preserve the exact returned terminal marker.
Retain and read back the final cleanup report/marker before leaving the root.
If retention fails after sealing, preserve the root and recover the original
seal output from the current Session; never create another event or seal.
Leave the Task worktree before the normal Janitor transition; this Agent never
deletes its own worktree or branch.

Diagnose a technical failure within existing authority before requesting user
action. If the permitted route cannot resolve it, preserve resources and name
the concrete missing capability or external action; do not interpret
`cleanup_needs_user_action` approval as permission to bypass missing evidence.

## Release

Use the existing `workflow-release-cleanup` procedure and its same-owner
contract. Engineering cache retirement is not a Release preflight bypass:
an unexpected `plan-contract.json` remains an unknown-entry blocker.
Before reading or requiring the release checkpoint, classify the full
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

## Janitor boundary

The schema-4 Janitor may delete only resources conclusively safe for the
selected cleanup mode; it never touches the primary checkout or live Kent
state. Preserve unrelated, dirty, unpushed, unmerged, and unclear resources.
