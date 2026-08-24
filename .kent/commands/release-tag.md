---
description: Publish an approved Puber release tag
---

# Release Tag

Only the approval-gated `.kent/scripts/workflow-puber-release-publish` may
create and push the release tag. It requires the exact workflow/task/transition
carrier, target commit, tag absence, and explicit authorization. Pull-request
CI, release monitoring, and cleanup never create tags or mutate GitHub Releases.
