# Puber Engineering current workflow source

This source candidate is derived from the frozen native v13 Engineering graph
and the Puber profile through the retained Kit runtime pinned in
`.kent/workflow-profile.toml`. It is an unactivated source proposal; building or
checking it does not inspect or mutate native Kent state.

The Backlog entry now targets `engineering_source_admission`, a Script that
qualifies the actual native Task/Node invocation and published source using the
existing Kit bootstrap. Only successful qualification emits
`engineering_source_admitted` to the existing Plan node. Admission failures
exit nonzero with a bounded generic diagnostic; they do not emit a transition.
The Script does not initialize task storage, choose an execution target, run
providers, or perform resource, build, publication, or other native effects.

All original node, transition-group, and edge identities remain stable. The
candidate retains every native approval flag and adds the twelve current Kit
recovery transitions. `prepare_pr_fix` remains approval-gated. The current
Plan prompt and fresh-Session handoff are retained after the Script; the stale
legacy initial-admission header and direct Backlog-to-Plan route are absent.

Use the builder's `--check` mode to verify exact deterministic bytes. The
candidate and frozen fixture contain no live source-admission receipt or
execution-target selector. Native projection, review, publication, installation,
and activation remain separate gated work.
