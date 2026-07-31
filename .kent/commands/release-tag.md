---
description: Create and push a new release tag on master branch
---

# Release Tag

Prepares Russian release notes, then creates a new release tag on the confirmed
master commit without switching to it.

## Usage
```
/prompt:release-tag
```

## What it does

1. Fetches and updates local master branch from origin (without checkout)
2. Gets the latest release tag (format: vX.Y.Z)
3. Increments the minor version (e.g., v1.2.0 -> v1.3.0)
4. Builds concise user-facing Russian release notes for the delivered range
5. Creates the new tag on master
6. Pushes the tag to origin
7. After release automation succeeds, applies and verifies the Russian notes on
   the GitHub Release

## Steps to execute

1. Run `git fetch origin master:master` to update master without switching
2. Run `git tag --sort=-v:refname | head -1` to get the latest tag
3. Parse the version and increment minor version (reset patch to 0)
4. Resolve the previous release tag and summarize
   `<previous-tag>..<target-commit>` in Russian. Exclude the version-bump PR and
   internal release chores. Save the text under the ignored task workspace as
   `release-notes-ru.md`.
5. Run `git tag <new_version> <target-commit>` to create the tag.
6. Run `git push origin <new_version>` to push the tag.
7. Wait for the exact release run with
   `gh run watch <run-id> --exit-status --interval 30`.
8. Apply the prepared notes:

   ```bash
   gh release edit <new_version> --notes-file <release-notes-path>
   ```

9. Read the release back with `gh release view` and verify the Russian body.

## Example output

```
Master updated: 277edba1f -> 667ccc039
Latest tag: v1.2.0
Created and pushed: v1.3.0
```
