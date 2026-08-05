# Delivery Context Budget

Read `AGENTS.md`, `.kent/project-contract.md`,
`.kent/workflow-profile.toml`, exact incoming delivery state, and only the
procedure mapped to the current node.

Load PR, cleanup, or release commands only when that node owns the action. Do
not reread implementation recipes, design sources, broad review reports, or
runtime instructions without a cited blocker.

Ledger entry: current branch/PR/run/merge/release state, authority for external
actions, cleanup preservation, and exact instruction files read.
