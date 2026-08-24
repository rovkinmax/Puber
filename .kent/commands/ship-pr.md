---
description: Commit, push, and create the single Puber task pull request
---

# Ship PR

Ship only the reviewed task branch. Verify the exact branch, base, changed
paths, local source checks, and no-effect audit before the one non-force push.
Never merge, tag, publish, dispatch, rerun, or invoke release automation from
this command. The PR must report the schema-4 S05 checks and any deferred
full Gradle gate; S10 remains a later serial slice on the same branch.
