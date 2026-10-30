---
description: Run Puber Android TV Smoke with native identity and resource-lease custody
---

# Puber Smoke

Follow `.kent/commands/smoke-test-resource-lease.md` and the authoritative
`.kent/commands/mobile-operation-policy.md`.

The active source selects the project-owned `puber_resource_lease` and
`android_tools` adapters for both consumers. Smoke uses `smoke.persistent`
with genuine native Project/Task/Session identity; Release uses synchronous
`release.clean` command ownership without Session impersonation.

Persist the exact run binding before acquisition dispatch. Use only the
returned endpoint, recheck before product-owned device work, and require
terminal cleanup before PASS. Retained legacy checkpoints require a separate
compatibility disposition; there is no legacy runtime fallback.

The new persistent TV does not inherit authentication. Preserve the existing
TV and all historical ownership/checkpoint records. Historical Smoke examples
under `tools/fixtures/mobile-resource-migration/` are audit/test fixtures,
not executable current instructions.

This is a source-only successor. Installed tooling, private per-workspace
configuration, actual TV qualification, legacy admission exclusion and native
activation require the separately reviewed activation procedure.
