# MCP Bridge

Use the global Kent Engineering Kit adapter:

```bash
~/.kent/bin/kent-mcp-list <server> --schema
~/.kent/bin/kent-mcp-call <server.tool> [arguments]
```

Do not call raw `mcporter` or project-local wrapper copies.

## Config and Policy

Config resolution is worktree-aware: global, primary-project, optional
worktree, and process environment settings take precedence over current or
primary `.mcp.json` and mcporter default discovery. Env files are parsed as
plain `KEY=VALUE`; never shell-source them.

The project-local `.kent/adapters/mcp/policy` classifies known Puber tools.
Unknown operations inherit the global fail-closed mutation policy.
The default `mobile` server is configured globally by the kit and must not be
duplicated in project `.mcp.json`.

## Output Safety

Normal command stdout remains in Kent's shell transcript. Use:

- `--quiet` when only command success matters;
- `--digest-output` for opaque liveness or bounded structural evidence;
- assertions for known expected literals;
- `--hash-matches` with `--marker-present` for opaque identity sets;
- `--raw-dir <dir> >/dev/null` only for a known-safe scoped response that must
  be read in full.

For a persisted response, record `.todo/_mcp-log/mcporter-calls.jsonl` length
before the call. Then resolve exactly one newly appended successful record for
the expected server/tool and read its non-empty `rawOutputPath`. Do not guess
timestamped filenames or continue before inspecting the exact artifact.

Never persist unexpected authenticated UI, broad device logs, network payloads,
credentials, or headers.

## Mobile

Mobile MCP is not the current Puber device interface. Follow
`.kent/commands/smoke-test-resource-lease.md` and
`.kent/commands/mobile-operation-policy.md`. UI/DPAD uses the project
`android_tools` delegate with exact resource-lease endpoint/custody.
Product-owned installation and scoped diagnostics retain their qualified
adapters and require a fresh ownership check. No retired discovery or raw
fallback is authorized by this rule. Other MCP integrations are unchanged.

## Mutation

Use read-only tools first. Require explicit approval and `--allow-mutate` for
device input, JetBrains rename/reformat/open actions, Serena edits, external
writes, and any unknown operation. Do not use `jetbrains.build_project`; run
Gradle directly.
