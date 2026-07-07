---
description: Prepare and publish a Puber release through one workflow
---

# Release

Human-facing Puber release command. Use this for backlog tasks such as "make next minor release from master".

## Defaults

- Base branch: `origin/master`, unless the task explicitly names another base.
- Version bump: next **minor** by default.
- Patch release only when the task explicitly says patch/hotfix.
- Major release only when the task explicitly says major.

## Flow

1. Fetch `origin/master` and tags.
2. Read `currentVersion` from `app/build.gradle.kts` on the base branch.
3. Compute the target version:
   - minor default: `X.Y.Z` -> `X.(Y+1).0`
   - patch explicit: `X.Y.Z` -> `X.Y.(Z+1)`
   - major explicit: `X.Y.Z` -> `(X+1).0.0`
4. Create or reuse `release/<version>` from the fetched base.
5. Update `currentVersion` in `app/build.gradle.kts`.
6. Commit `Bump version to <version>`.
7. Verify with compile checks. If release signing secrets are unavailable, report that production packaging could not be
   locally proven, but do not block the version-bump PR solely for missing local signing secrets.
8. Run Compliance Review.
9. Create or update a PR for `release/<version>`.
10. Monitor CI/checks.
11. After approval, verify the PR is merged into `origin/master`, create tag `v<version>` on the master commit, and push
    the tag.
12. Monitor release automation when available.
13. Cleanup conservatively.

## Safety Rules

- Never push directly to `master`.
- Never merge the PR.
- Never create or push the release tag before the version bump is present on `origin/master`.
- If the PR is not merged, publication must block with a clear `blocker_reason`.
- If a tag already exists locally or remotely and does not point to the intended commit, block.
