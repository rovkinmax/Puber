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

## Android TV Smoke

- Conditional routing follows `.kent/commands/smoke-policy.md`; execution
  follows `.kent/commands/smoke-test.md`.
- Use a TV emulator selected by exact serial. Physical TV/device use requires
  explicit authorization.
- Acquire the shared lease before build, install, launch, input, logs, or MCP.
  On resume, reuse a still-owned checkpoint token through the adapter's
  `resume` operation.
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
project adapter and fail closed while carrying the flat expected-check packet,
CI history/cursor, actual PR head/base, concrete `rebase` strategy, and
explicit `verification_summary`.
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
secret-name attestation. Semantic source revision 90 uses prepared native
identity 88 and the approved 18/51/51 topology while preserving existing node
IDs, Publish inputs, and approval gates. It is source-only, with no live
rollout or restart. The prepared native identity is not evidence that the live
counter changed; adoption remains separately gated.

## Cleanup Agent boundary

The existing Cleanup node is a normal `delivery-operator` Agent, not a Script.
Its current native Session is the owner; other idle Task Sessions on H are
not candidates to select or bulk-move. `.kent/commands/release.md` owns the
closed helper input and observable-outcome policy. Incoming route hints cannot
lower publication/resource proof. Same-owner previous_target retries reactivate
at H; the Agent verifies the seal, leaves its own root, and then hands the
unchanged carrier to stock Janitor/native deletion admission.
