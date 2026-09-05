---
description: Commit, push, and create the single Puber task pull request
---

# Ship PR

Ship only the reviewed task branch. Verify the exact branch, base, changed
paths, local source checks, and no-effect audit before the one non-force push.
For a release, validate the profile/preparation checkpoint against the exact
`release_head_oid` immediately before push and again after push or PR
create/update. The resulting PR diff must contain the version bump and only
actually changed generated profiles; never amend or force-push and never
overwrite unrelated user changes. If the branch head changed, return to
`prepare` so the full profile pipeline runs again.
Never merge, tag, publish, dispatch, rerun, or invoke release automation from
this command. The PR must report the schema-4 S05 checks and any deferred
full Gradle gate; S10 remains a later serial slice on the same branch.

## Revision 90 closed safety contract

The exact signing pins, preparation-report v2 fields and admission rules are
owned by `.kent/commands/release.md`. Require
`puber_release_profile_checkpoint_v2` and `puber_release_preparation_report_v2`;
reject v1, missing/extra fields and all identity drift. All checkpoint reads,
writes and receipt-matched terminal deletes use the shared descriptor-relative
`O_NOFOLLOW` store in `.kent/scripts/workflow-puber-release-intent`.

Pre-PR `debug_validation` proves production-variant packaging with the unchanged
tracked debug key and exact APK signer, but produces a non-publishable artifact.
Reject production inputs; discard validation APK/build outputs on every exit.
Compliance and Ship bind the key Git blob, content and certificate identities
as well as the exact branch/report digests. Publish additionally binds the
manifest-closed production effect job; a validation report alone is never
artifact-publication authority.

GitHub Release requires `production`, exact stable alias/APK certificate pins,
and fail-closed secret preflight. Only preflight/build receive the three signing
secrets; upload receives none, and only Release creation gets `GH_TOKEN`.
Production credentials never enter task/preparation worktrees. Missing or
ambiguous signing sources cannot fall back to debug. There is no external
secret-name attestation. Revision 90 retains 17/46/46 topology, UUID, non-default
status and existing approval gates; it is source-only, with no live rollout or
restart. Pre-live failure restores all 24 preimages together. After live rollout,
never restore unsafe revision 89: disable new admissions/tag approvals first,
then use a separately reviewed safe revision or forward revision 91.
