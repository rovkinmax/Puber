# MCP Bridge

Kent sessions may not expose native MCP tool names. Use the project wrappers instead:

```bash
.kent/adapters/mcp/mcp-list.sh <server> --schema
.kent/adapters/mcp/mcp-call.sh <server.tool> [arguments] --raw-dir ".todo/<task>/mcp"
```

## Config Resolution

The wrapper resolves config in this order:

1. process environment `MCP_CONFIG_PATH`
2. `~/.kent/mcp.<workspace-name>.env`, for example `~/.kent/mcp.Puber.env`
3. `~/.kent/mcp.env`
4. local `.mcp.json`
5. `mcporter` default/global discovery

Env files are parsed as plain `KEY=VALUE`; they are not shell-sourced.

For worktrees, point `~/.kent/mcp.Puber.env` at the main checkout MCP config when project-bound MCP access is needed.

## Raw Output

Save raw source data under ignored local paths:

```text
.todo/<feature-or-task>/mcp/<server>/<tool>-<timestamp>.<ext>
.todo/_mcp-raw/<server>/<tool>-<timestamp>.<ext>
```

## Mutation

Use read-only tools first. Require explicit user approval and `--allow-mutate` for:

- mobile taps, text input, recorder playback, visual baseline writes, or generated tests;
- JetBrains rename/reformat/run actions;
- Firebase create/update/delete/deploy/auth/messaging actions;
- any tool whose name or `action=` implies create/update/delete/write/send/publish/deploy/execute/run.

Do not use `jetbrains.build_project`; use Gradle commands for builds.
