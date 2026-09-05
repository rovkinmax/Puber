---
description: Read-only compliance review for Puber release outputs
---

# Compliance Review

Review the exact S05 allowlist, schema-4 graph identity, deterministic runtime
carriers, Java-21 pinned PR checks, and no-effect boundaries. Do not edit,
commit, push, merge, tag, publish, dispatch, rerun, or mutate Kent state.
For release preparation, independently validate the closed profile and
preparation report digests, candidate/base/head OIDs, packaging pass states,
and that the prospective PR diff is exactly `app/build.gradle.kts` plus the
changed generated profile files. A no-profile-diff report must not invent or
stage profile changes. Reject any unrelated path or report/checkpoint drift.
Release publication is a separate approval-gated operation and is never part
of PR Checks.

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
