# MCP Bridge

Kent uses `mcporter` wrappers instead of direct `mcp__...` tool calls.

## Config

`mcp-call.sh` and `mcp-list.sh` resolve MCP config in this order:

1. process environment `MCP_CONFIG_PATH`
2. `~/.kent/mcp.<workspace-name>.env`, for example `~/.kent/mcp.Puber.env`
3. `~/.kent/mcp.env`
4. local `.mcp.json`
5. `mcporter` default discovery: project `config/mcporter.json`, global `~/.mcporter/mcporter.json`, and supported editor imports

Env files are parsed safely as plain `KEY=VALUE`; they are not shell-sourced. Initial supported key:

```text
MCP_CONFIG_PATH=/absolute/path/to/.mcp.json
```

For Puber worktrees, set `MCP_CONFIG_PATH` in `~/.kent/mcp.Puber.env` to point at the main checkout `.mcp.json`.
If none of the project-bound config sources exists, wrappers omit `--config` and let `mcporter` use default/global
discovery.

## Usage

```bash
.kent/adapters/mcp/mcp-list.sh figma --schema --timeout 5000
.kent/adapters/mcp/mcp-call.sh figma.get_design_context nodeId="<node-id>" --output markdown --raw-dir ".todo/<feature>/mcp"
.kent/adapters/mcp/mcp-list.sh mobile --schema --refresh --timeout 30000
```

`mcporter` must be installed globally and available in `PATH`. The wrappers do not fallback to `npx`.

`mcp-list.sh <server> --schema` caches successful schema output in `build/mcp-cache/<server>-schema.json`.
Use `--refresh` after server upgrades or config changes.

## Raw Output

`mcp-call.sh` saves raw stdout by default:

```text
.todo/<feature-or-task>/mcp/<server>/<tool>-<yyyyMMdd-HHmmss>.<ext>
build/test-artifacts/mcp/<server>/<tool>-<yyyyMMdd-HHmmss>.<ext>
.todo/_mcp-raw/<server>/<tool>-<yyyyMMdd-HHmmss>.<ext>
```

Pass `--raw-dir <dir>` from workflow commands. If omitted, output goes to `.todo/_mcp-raw/`.

Raw outputs and logs are local/private. Do not force-add them to git.

## Mutating Tools

Known mutating calls require `--allow-mutate`, including mobile input/flow, JetBrains rename/reformat/run actions,
Firebase create/update/delete/deploy/auth/messaging actions, and any tool/action that writes external state.

Do not use `jetbrains.build_project`; use Gradle commands for builds and diagnostics.
