# Puber Kent Project Contract

This file contains only Puber-specific deltas for the shared Kent Engineering
Kit. Global lifecycle, authority, writer, review, recovery, evidence, PR,
waiting, and cleanup semantics come from the installed kit roles and generated
workflow.

## Context Sources

- Repository rules and gotchas: `AGENTS.md`.
- Profile and node budgets:
  `.kent/workflow-profile.toml` and `.kent/context/*.md`.
- Android TV workflow index:
  `.kent/skills/puber-android-workflow/SKILL.md`.
- Mobile lease/evidence adapters: `.kent/adapters/mobile/`.
- MCP policy: `.kent/adapters/mcp/policy`.

The active manifest is the read allowlist. Load a rule or recipe only when its
trigger matches the current node and task.

## Planning And Identity

- Feature artifacts live under `.todo/<feature>/`; `plan.md` owns writer-step
  progress and `meta.json` stores identity/source metadata only.
- Kent keeps `PUB-*` as task identity.
- After Plan, a same-repository GitHub issue resolves the branch to
  `issue-<number>`. Source URL wins, followed by same-repository task-body
  issue URLs.
- Cross-repository issues, missing identity, or non-issue tasks keep the Kent
  branch. Existing local or remote collisions block without ref reuse.
- A PR that fully resolves the same-repository issue uses `Fixes #<number>`;
  partial and cross-repository relationships are non-closing links.

## Build

- Main compile task: `:app:compileDevDebugKotlin`.
- The primary checkout may use `./gradlew`; every worktree uses
  `./tools/agentw`.
- Worktree SDK setup writes only `sdk.dir`; KinoPub/TMDB credentials are never
  copied.
- Deterministic workflow verification uses the command selected by the profile.
- Tasks changing behavior or scenario fixtures follow
  `.kent/commands/mock-scenario-policy.md`. Before an Implement/Fix verifier
  handoff, recover the durable evidence paths, refresh the packet and execution
  receipt against the final source delta, then serialize the closed review
  envelope into `review_context`. Missing or stale evidence is writer-recoverable:
  refresh it and retry without a new approval. The compile verifier validates
  the envelope before running the fixed compile command; invalid or unavailable
  evidence blocks rather than admitting compile success.

## Android TV Smoke

- Conditional routing follows `.kent/commands/smoke-policy.md`; execution
  follows `.kent/commands/smoke-test.md`.
- Follow `.kent/commands/mobile-operation-policy.md` and the selected
  resource-lease Smoke procedure. Physical device use requires separate
  authorization and a separately qualified profile; never substitute an endpoint.
- Smoke uses `smoke.persistent` with genuine native Project/Task/Session binding;
  Release uses synchronous `release.clean` command ownership. Recheck custody
  before product-owned operations. UI/DPAD delegates to shared `android_tools`;
  no legacy acquisition/resumption or Mobile MCP fallback is selected.
- Build a fresh task APK, but preserve app data by installing only through
  `.kent/adapters/mobile/android-apk-install-preserve`. Unknown signer,
  downgrade, signer mismatch, or install failure blocks the replacement.
  Never uninstall, clear package data, allow downgrade, or replace a signer
  without separate explicit authority.
- A resumed Smoke run may reuse a checkpointed install only when the APK
  SHA-256 and required authenticated state still match. Runtime proof covers
  visible behavior, focus/navigation, integration, restoration, and liveness.
- Playback-progress, account, subscription, or server-visible mutations require
  explicit task or comment authority.

## MCP And Release

- Puber has no Jira adapter; generated tasks must not assume Jira.
- MCP calls use `~/.kent/bin/kent-mcp-call` and
  `~/.kent/bin/kent-mcp-list`.
- Credentials, broad/raw UI, logs, network state, and private MCP config stay
  outside Git and evidence.
- Merge strategy is resolved from repository rules.
- Release remains a separate workflow: version PR, merge, tag approval,
  Russian user-facing notes, automation monitoring, and GitHub Release
  verification.

## S05 Release Control Plane

The non-default `Puber Release` source is semantic revision 90 with prepared
native deployment identity 88 and schema 4. Its CI-tail transport is exactly
18 nodes/51 transition groups/51 edges; the prior 17/46/46 source remains an
audit snapshot only. Its canonical identity is
`10d8adb2-c74c-4ef0-8b5c-311cb5cd0459`; source updates preserve that UUID and
name. `release_intent_gate`, `ci_prepare`, `ci_watch`, `merge_watch`, and
`task_janitor` are deterministic script nodes. The three CI nodes use the
project adapter and fail closed with an identity-only `ci_contract`, a dynamic
`ci_report` when produced, and an acknowledged `pr_feedback_cursor`. The
initial ship handoff precedes CI packet creation; `ci_prepare` observes the
exact PR, derives the contract, and initializes the cursor. CI handoffs retain
actual PR head/base, the concrete `rebase` strategy, and explicit
`verification_summary`.
Release preparation is ordered as `prepare -> profile_generation ->
finalize_release -> compliance`; CI is ordered through `ci_prepare ->
ci_watch -> waiting_pr`, with already-merged recovery entering the existing
merge watcher. No release PR, merge, tag, or GitHub Release may bypass profile
generation and finalization.

Release intent, tag publication, GitHub Release watching, and cleanup are
separate operations. Publication requires an explicit approved transition and
never runs from pull-request CI. The PR workflow is source-only Gradle CI: it
uses Java 21, pinned actions, and only `detekt`, `unit-tests`, and `build`; it
has no signing, release, tag, publication, deployment, or credential effect.

The tracked graph is a source artifact only. S05 never applies, relinks, or
mutates a live Kent Workflow, Task, default, canonical state, or project link.

## Current Release successor source candidate

The profile selects `.kent/workflows/puber-release-current.json` at proposed
native version 99/source revision 93. This source-only proposal requires a
revalidated native-98 preimage and a reviewed graph-only source-ingress update
with unchanged metadata; it does not claim publication or activation. It
preserves 18 nodes and Script/Agent
kinds, with 53 transition groups and 53 edges after the S4 CI-recovery
transitions are added. The 20 ordinary Script routes retain their
business-only `entry_transition` hints; the typed failure/retry/user-action
routes have no synthetic route hint. Existing unrelated approval gates remain
unchanged; the CI failure becomes a typed Agent handoff, while the separate
CI Monitor user-action route remains approval-gated. It adds explicit
`pr_base_branch` continuity, carries the original release `operation_id`
through publication/cleanup, and forwards exact merged target T to the
existing Publish approval path. The actual PR target branch is compared by
name; it is not inferred from the mutable base OID. Native completion uses each
transition group's `transition_id`.

CI preparation failure carries a closed typed diagnostic, never fabricated CI
packets or a blocker carrier. The project wrapper validates the actual child
producer and exact child-input digest, captures its final native output with
explicit child/retry producer references, and preserves original input bytes.
The CI Monitor can retry only through the existing qualified Agent packer and
the exact validated post-PR v2 context. The retry Script authenticates the
native graph contract, restores the full Release carrier from referenced
producer ancestry, rechecks the completed checkpoint, and preserves all
existing context bytes. Only the exact initial PR route may use the shared
preparer's established absent-cursor value when cursor and CI packets are
actually absent. Malformed values are not normalized into retry context.

This remains a non-attested source candidate. The profile binds the
`kit_runtime_loader` version `1.0.0` and retained published Kit cohort K by
its exact root, commit, and `refs/remotes/origin/main`; the shared checkpoint,
evidence, Janitor, CI-preparation wrapper, and bootstrap copies are
source-bound in the Release manifest. The Release helper and Puber Janitor
adapter require the invocation's native qualified handle and fail closed when
it is unavailable; environment Kit selection and the former root-only profile
field are not production fallbacks.

`runtime_attested = false` describes the tracked source proposal; a profile
binding alone does not claim native runtime admission. For final published
Puber source C, the canonical native operator projects the compact
Script-ingress reference from the reviewed source receipt and exact target,
creates the corresponding private immutable frame, and validates the
complete native target. That frame is not fabricated by the source builder or
committed into the project graph. Until the separately reviewed native
projection and readback, no live Workflow, Task, default, or runtime
activation is claimed. The v90 and 5af-named v95/source-91 graph/spec/manifest
snapshots remain historical and byte-stable. Any live effect requires its
separate explicit authority and compatibility checks.

The current source selects project-owned `puber_resource_lease` and
`android_tools` delegates, not backported Kit-managed templates. It preserves
the exact Kit pin and command versions. The authoritative operation policy
is `.kent/commands/mobile-operation-policy.md`.

Release checkpoints use `puber_release_profile_checkpoint_v3` in the existing
protected store, retaining business/report/preparation identity and terminal
cleanup proof without receipt secrets. The profile report and preparation
report schemas remain unchanged. Legacy v2 checkpoints stay diagnostic/recovery
inputs and are never converted or reused as new ownership.

The historical Script-lock wrapper remains byte-stable but is not selected.
The retired lock permits only validated status/cleanup. Current source
selection does not prove host-wide legacy exclusion; retained consumers and
native admission remain separately reviewed activation gates.

Cleanup classifies Task runtime inventory and seal history before checkpoint
reads or retention. Before final sealing it retains exact checkpoint bytes,
original input/proofs, source-envelope digest, canonical report prefix, and
seal request in an ignored bounded
`build/kent-workflow/<task-short-id>/` receipt referenced and read back from
the durable Task record. Unknown, partial, conflicting, or unsealed state
preserves all bytes and blocks before seal or deletion. A valid prior seal with
an absent checkpoint allows only read-only reuse of that retained original
input; missing proof is never reconstructed from a marker.

## Historical Revision 90 closed safety contract

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
identity 88 and the approved 18/51/51 topology while preserving existing node
IDs, Publish inputs, and approval gates. It is source-only, with no live
rollout or restart. The prepared native identity is not evidence that the live
counter changed; adoption remains separately gated.

## Cleanup Agent boundary

Engineering accepted-plan retention and the no-helper terminal evidence
protocol are owned by `.kent/commands/cleanup-task.md`. Retain the exact
accepted-plan cache bytes in a readback-verified Task comment before retiring
that cache and sealing. Recovery reuses original proof without append/reseal.
This does not change Release's separate same-owner retention protocol or
permit it to retire an unknown accepted-plan cache.

The existing Cleanup node is a normal `delivery-operator` Agent, not a Script.
Its current native Session is the owner; other idle Task Sessions on H are
not candidates to select or bulk-move. `.kent/commands/release.md` owns the
closed helper input and observable-outcome policy. Incoming route hints cannot
lower publication/resource proof. Same-owner previous_target retries reactivate
at H; the Agent verifies the seal, leaves its own root, and then hands the
unchanged carrier to stock Janitor/native deletion admission.
