---
description: Create and push a new release tag on master branch
---

# Release Tag

Creates a new release tag on master branch without switching to it.

## Usage
```
/prompt:release-tag
```

## What it does

1. Fetches and updates local master branch from origin (without checkout)
2. Gets the latest release tag (format: vX.Y.Z)
3. Increments the minor version (e.g., v1.2.0 -> v1.3.0)
4. Creates the new tag on master
5. Pushes the tag to origin

## Steps to execute

1. Run `git fetch origin master:master` to update master without switching
2. Run `git tag --sort=-v:refname | head -1` to get the latest tag
3. Parse the version and increment minor version (reset patch to 0)
4. Run `git tag <new_version> master` to create the tag
5. Run `git push origin <new_version>` to push the tag

## Example output

```
Master updated: 277edba1f -> 667ccc039
Latest tag: v1.2.0
Created and pushed: v1.3.0
```
