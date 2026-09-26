---
description: Read-only compliance review for Puber delivery and release outputs
---

# Compliance Review

Review final task delivery against the approved scope, exact changed paths,
verification results, and no-effect boundaries. Attest only to evidence
observed; report missing or ambiguous proof. Do not edit, commit, push, merge,
tag, publish, dispatch, rerun, or mutate Kent state.

## CI evidence

Require CI evidence only after its producer has run, and bind it to the exact
PR and observed head/base. `ci_contract` is the producer-validated PR identity,
not an expected-check packet. Validate the dynamic `ci_report` when returned by
its producer; do not require it when unavailable or fabricate it. Use
`pr_feedback_cursor` only after the producer initializes it. Do not require or
fabricate these fields on an initial handoff before `ci_prepare`.

Classify every effective check observed: failed extras are failures,
incomplete observations are not green, and pending observations are not user
actions. Verify the explicit `verification_summary`, concrete rebase strategy,
and actual PR head/base facts.

## Puber Release-only checks

Apply this section only to tasks in the Puber Release workflow. For Release,
review the exact S05 allowlist, schema-4 graph identity, deterministic runtime
carriers, the 18/51/51 CI-tail transport, Java-21 pinned PR checks, and
no-effect boundaries. For release preparation, independently validate the closed profile
and preparation report digests, candidate/base/head OIDs,
packaging pass states, and that the prospective PR diff is exactly
`app/build.gradle.kts` plus the changed generated profile files. If there is no
profile diff, do not invent or stage profile changes. Reject any unrelated path
or report/checkpoint drift.

`.kent/commands/release.md` is authoritative for Release-only signing,
checkpoint, packaging, CI, and publication requirements. Keep Release graph
and topology checks within that workflow and consistent with the source
identity declared in `.kent/project-contract.md`. Do not apply those release
requirements to ordinary task delivery. Release publication is a separate
approval-gated operation and is never part of PR Checks.
