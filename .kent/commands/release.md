---
description: Prepare and publish a Puber release through the schema-4 control plane
---

# Release

Use the non-default `Puber Release` graph for release work. Preparation is a
strict two-phase operation:

1. `prepare` creates or reuses `release/<version>` at the exact current
   `origin/master` commit and creates no commit or PR.
2. `profile_generation` runs
   `tools/generate-baseline-profile.sh` with the exact serial from the leased
   TV-emulator resource.
3. `finalize_release` validates both production packaging commands, applies or
   reuses the version bump, stages only the version file and actually changed
   generated profiles, and records a digest-bound preparation checkpoint.

Compliance and Ship must validate that checkpoint against the exact branch
head before any non-force push or PR create/update. Publication is performed
only by `.kent/scripts/workflow-puber-release-publish` after the approved merge
transition; it rechecks both the approved PR head and the merged target blobs
before tag creation. Cleanup seals the report digests and removes only the
validated terminal profile checkpoint before unchanged Janitor admission.

Any lease, mock harness, network-isolation, profile output, packaging, report,
or branch-identity failure is fail-closed and must not present the PR as
merge-ready or reach tag publication.

The graph is source-only and does not apply or relink live Kent state. Pull
request CI is not a publication context. It runs only the required `detekt`,
`unit-tests`, and `build` jobs with Java 21.

## Revision 90 closed safety contract

Revision 90 requires `puber_release_profile_checkpoint_v2` and the closed
`puber_release_preparation_report_v2`. Read/write checkpoints only through the
shared descriptor-relative `O_NOFOLLOW` store in
`.kent/scripts/workflow-puber-release-intent`; Cleanup uses its in-process
receipt-matched delete. Never accept/reuse/delete v1 or carry checkpoint paths
or receipts on graph edges.

Pre-PR packaging is `PUBER_RELEASE_SIGNING_MODE=debug_validation`, never
production signing. Use an exact allowlisted replacement environment containing
only HOME, PATH, JAVA_HOME, ANDROID_HOME and that mode; reject
`app/keystore.properties`, `app/release.jks` and all production signing inputs.
The unchanged tracked `app/debug.jks` must bind Git blob
`109bfc97479cd17724a3ba75a8d3b5ca9df22f52`, content SHA-256
`91c981cee70e84338ff5c7938ae8110f8c0a2378a7a936c245133b4d5ee7620f` and certificate
`6b29181257cb520329553691b7a48c9b1123899950105127b3dd67012c49a83e`; the APK must
have exactly that signer. Report fields are exactly the existing preparation
fields plus `packaging_signing_mode="debug_validation"`,
`packaging_artifact_publishable=false`, `production_signing_used=false`,
`validation_signing_source="tracked_app_debug_jks"`, `validation_signing_blob_oid`,
`validation_signing_sha256`, `validation_signer_certificate_sha256` and
`packaging_artifact_signer_certificate_sha256`, with the exact pins above.
Reject v1, extra/missing fields, wrong boolean types or any key/signer drift.
Discard validation APK/build outputs on every exit: never stage, retain,
upload or publish them. A validation report proves PR readiness only.

Post-merge tag approval remains separate. GitHub preflight/build alone receive
production mode and `RELEASE_KEYSTORE_BASE64`, `STOREPASS`, `KEYALIAS`, with
key password `STOREPASS`; `KEYPASS` is forbidden. Require the alias and APK
signer to equal production certificate
`3e0ddb2c5d39953d278f8cce813ff07a6b74059f1f9caa8fd752602e2bb8b61a`, with no debug
fallback, partial or ambiguous credentials. Cleanup traps remove transient
signing material and original signed output. Upload gets no signing inputs;
only Release creation gets `GH_TOKEN=${{ github.token }}`. There are no job-level
secrets/mode, no external secret-name attestation and no production credentials
in task/preparation worktrees. Verify the manifest-bound production effect job;
debug-validation proof cannot itself authorize artifact publication.

Finalization calls `read_profile_checkpoint(root, task_short_id, expected=...)`
and `write_profile_checkpoint(root, task_short_id, value)` in the shared helper;
it never constructs or writes a checkpoint pathname. Compute the v2 report
only after both allowlisted packaging commands pass, including Gradle's exact
APK-signer check. Canonicalize with `canonical_json`, bind `digest_json`, and
update the same completed v2 checkpoint with the exact `release_head_oid` and
nested report/output hashes. Use `try/finally` to discard `app/build` outputs
on success, packaging failure or cancellation before declaring readiness.
Cleanup alone requests `with_receipt=True` and calls
`delete_profile_checkpoint(root, task_short_id, receipt)` after evidence sealing.
The receipt stays process-local. A schema/parent/receipt mismatch interrupts
without removing or reusing the stale or replacement checkpoint.

## CI and merged-target handoff

Release source revision 90 is distinct from prepared native revision 88. Native
88 admission is valid only following a separately gated rollout from a freshly
verified live 87 preimage; no source command applies that rollout.

`ci_prepare` obtains a real initial/retry CI packet from the shared preparer.
Carry flat release identity, explicit `verification_summary`, CI history and
feedback cursor. Re-read the complete checkpoint with `expected=...`; never
transport the entire checkpoint or synthesize an initial CI report. Build,
Detekt and UnitTests remain mandatory. Policy commit P is not the actual PR
base: preserve actual PR head/base for the unchanged seven Publish inputs.
Materialize publication approval commentary using actual version, tag and
merged T before asking for tag authority.

The original Task's native execution lock stays K. Preparation produces H;
the existing completed checkpoint and exact K-to-H release diff admit H without
rewriting Task metadata. The current controller source/runtime binding and
cleanup checkpoint owner are H. The Kit current-execution context describes H,
not the native initial lock K. Before each tag attempt or retry, independently
preflight actual Git T, read its source captures, prepared version/profiles and
owning signing inputs,
and validate its production job against H policy. Compare the complete
normalized production workflow, including all jobs, with authorized H policy.
Do not require unrelated Gradle/buildSrc trees or raw profile/spec text to equal
H; T is observed against the authorized H policy, not allowed to weaken it.
Require the merged PR and current master to equal T at the pre-tag gate. No
local production build or assertion of general H/T tree equivalence is implied.

Monitor revalidates immutable H/T and the same v1 publication report against
the H-owned preparation checkpoint before observation and before a notes edit.
Its separate GitHub runtime binding describes the actual run/attempt/head/ref
T, not a fictitious Task T. Master may advance after publication. Keep the v1
report digest/continuity and checkpoint seal, receipt-bound deletion and
post-delete validation ordering. Missing T/source objects block with zero
fetch/tag effects; object acquisition needs separate operator authorization.
No Q, persisted target proof, new report schema or authority store is introduced.

### Current Cleanup Agent and observable-outcome policy

Cleanup is a real `delivery-operator` Agent. Its deterministic helper accepts
exactly `task_id`, `incoming_transition` and `parameters`; `_kent` is forbidden.
The caller's inherited `KENT_SESSION_ID` is captured before helper imports
scrub the environment. Native Task current_nodes.session_id, current_scripts=[]
and explicit worktree status must identify that same Agent A on H. Historical
idle Sessions on H are normal and are never enumerated to select an owner.

The route hint does not authorize weaker cleanup. The helper independently
resolves version/tag/branch from Task/checkpoint/H and discovers all matching
PRs across all states, local/remote tag, Releases, production runs, notes and
existing evidence with bounded complete reads. Unknown visibility, errors,
truncation, partial publication or unfinished preparation/resource obligations
block before sealing or checkpoint deletion. It does not claim to read a
historical incoming-edge approval or that no attempt ever happened.

An existing terminal seal permits only exact original outcome/owner recovery.
A merged PR or any publication indicator requires full publication/release
proof and fresh read-only run/tag/artifact/notes verification; Cleanup never
edits Release notes. A unique CLOSED unmerged PR with no publication indicators
permits closed-PR resource cleanup. With no matching PR, cancellation-style
resource cleanup requires complete fresh absence of all publication indicators
and no unfinished obligations. A reason string or claimed human comment is not
additional authority. Existing native entry approval gates remain unchanged.

After a successful helper result, A retains the exact output, closes only its
own background/tool processes, leaves H through the supported worktree command,
verifies primary, then completes to the stock Janitor. Other idle Sessions are
retargeted by native Delete under its admission gate; active/background work
blocks deletion. No bulk Session moves are allowed. Ensure an ordinary bounded
Task evidence entry exists using the existing evidence command before invoking
the helper; that entry is observation, not cancellation/publication authority.

`task_janitor_blocked` uses continue_session/previous_target. Native Starter
reuses A and rebinds it to H before the retry. A must equal the sealed owner;
a replacement is rejected. Revalidate the exact seal/report and removed-checkpoint
state at H, then A leaves again. Never require the executing retry caller to
remain on primary, reconstruct a deleted checkpoint or downgrade sealed proof.

Original cancellation and CLOSED-PR calls may recover an existing seal after
an interrupted API recheck or lost helper result, even after the release
checkpoint was deleted. Recovery recomputes the same observable outcome and
canonical report and must match the existing seal's exact caller, runtime,
publication/preparation digests and report hash; it never appends another seal
or invents a Janitor carrier. Changed outcomes or callers remain blocked.

A generic fix/smoke checkpoint is not a completion receipt: its supported
schema only validates progress arrays and text fields, not resource release.
Cleanup preserves and blocks on either checkpoint until its owning stage
settles and clears it, including when `remaining=[]`. No generic checkpoint is
deleted as a side effect of release cleanup.
