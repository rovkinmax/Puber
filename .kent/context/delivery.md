# Delivery Context Budget

Read `AGENTS.md`, `.kent/project-contract.md`,
`.kent/workflow-profile.toml`, exact incoming delivery state, and only the
procedure mapped to the current node.

Load PR, cleanup, or release commands only when that node owns the action. Do
not reread implementation recipes, design sources, broad review reports, or
runtime instructions without a cited blocker.

For Cleanup, `.kent/commands/cleanup-task.md` owns retention and the single
final event/seal. Validate/reuse an existing seal without append; a blocker
does not create a new ledger event. Release retains its own preparation helper.
Other delivery stages record current branch/PR/run/merge/release state,
authority for external actions and the exact instruction files read.
