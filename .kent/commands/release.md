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
