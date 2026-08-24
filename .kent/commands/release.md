---
description: Prepare and publish a Puber release through the schema-4 control plane
---

# Release

Use the non-default `Puber Release` graph for release work. Release intent is
validated by `.kent/scripts/workflow-puber-release-intent`; publication is
performed only by `.kent/scripts/workflow-puber-release-publish` after the
approved merge transition; release automation is watched by
`.kent/scripts/workflow-wait-github-release`; cleanup is conservative and
report-first.

The graph is source-only and does not apply or relink live Kent state. Pull
request CI is not a publication context. It runs only the required `detekt`,
`unit-tests`, and `build` jobs with Java 21.
