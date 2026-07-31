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
11. After approval, verify the PR is merged into `origin/master`.
12. Resolve the previous release tag and final target commit, then prepare
    concise user-facing release notes in Russian from the delivered changes.
    Save them under the ignored task workspace, for example
    `.todo/<task>/release-notes-ru.md`. Exclude release-only chores and rewrite
    technical commit/PR titles as user-visible changes.
13. Create tag `v<version>` on the master commit and push the tag.
14. Monitor release automation until terminal state.
15. After successful publication, apply the prepared Russian notes to the
    GitHub Release with `gh release edit <tag> --notes-file <path>` and verify
    the resulting release body before cleanup.
16. Cleanup conservatively.

## CI And Release Monitoring

- Pending, queued, or in-progress checks are not a blocker and never justify
  `needs_user_action`.
- Resolve the exact PR or Actions run once. Use
  `gh pr checks <pr> --watch --interval 30` for PR checks or
  `gh run watch <run-id> --exit-status --interval 30` for release automation.
- Let the blocking watcher wait until terminal state, then re-read authoritative
  status and classify green, failed, or canceled.
- A green release run is not complete until the GitHub Release exists and its
  final body contains the prepared Russian release notes.
- Ask the user only for authentication/access, ambiguous run identity,
  contradictory policy, or another actual decision. Do not ask the user to
  wait or approve another poll.

## Safety Rules

- Never push directly to `master`.
- Never merge the PR.
- Never create or push the release tag before the version bump is present on `origin/master`.
- If the PR is not merged, publication must block with a clear `blocker_reason`.
- If a tag already exists locally or remotely and does not point to the intended commit, block.
