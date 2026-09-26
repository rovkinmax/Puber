---
description: Commit, push, and create the single Puber task pull request
---

# Ship PR

## Common delivery

Ship only the reviewed task branch. Verify the exact branch, base, changed
paths, local source checks, and no-effect audit before the one non-force push.
Do not overwrite unrelated user changes; never amend or force-push commits. Do
not merge, tag, publish, dispatch, rerun, or invoke release automation from
this command.

Follow the carrier declared by each generated transition. Do not infer or add
CI fields before their producer has produced them. Bind any CI evidence to the
exact PR and observed head/base.

## Puber Release-only requirements

Apply this section only when shipping a task in the Puber Release workflow.
Validate the profile/preparation checkpoint against the exact
`release_head_oid` immediately before push and again after push or PR
create/update. The resulting PR diff must contain the version bump and only
actually changed generated profiles. The initial `ship_pr_ci_watch` handoff
carries actual `pr_head_oid`, observed `pr_base_oid`, complete release proof,
existing PR identity, and explicit `verification_summary`. Do not require or
fabricate `ci_contract`, `ci_report`, or `pr_feedback_cursor` on this first
edge: `ci_prepare` observes the PR, derives the validated identity-only
contract, and initializes the cursor before handing off to `ci_watch`. See
`.kent/commands/release.md` for the exact release proof requirements.

After `ci_prepare`, subsequent CI transitions carry the validated
`ci_contract`, dynamic `ci_report` only when available from its producer, the
acknowledged feedback cursor, actual PR head and base, explicit
`verification_summary`, and—on Release tasks—the complete release proof.
`pr_base_oid` must remain the observed PR base.
A changed release head requires `prepare` and the profile/finalization pipeline
again; changed CI policy goes through `ci_prepare`. Resolve the profile merge
policy using the published `workflowkit.merge_strategy` resolver and fresh
GitHub repository capabilities, branch protection and active branch rules.
Carry only its unique concrete result (currently `rebase`), never infer it
from admin bypass. The CI adapter independently revalidates that resolution.
The Release PR must report the schema-4 S05 checks, the concrete resolved
`rebase` strategy, and any deferred full Gradle gate.

### Revision 90 closed safety contract

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
secret-name attestation. Semantic source revision 90 uses prepared native
identity 88 and the approved 18/51/51 topology. Preserve all existing IDs,
Publish inputs, and approval gates; no CI transition creates tag/publication
authority. This source change has no live rollout or restart.
