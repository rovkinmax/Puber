# Adapters

Adapters contain project-specific policy and resource coordination used by the
global Kent Engineering Kit adapters.

Included:

- `mcp/policy`: project-specific MCP mutation classification. MCP calls use
  `~/.kent/bin/kent-mcp-call` and `~/.kent/bin/kent-mcp-list`.
- `mobile/`: shared device locks and evidence auditing for runtime Smoke.

Rules:

- Keep credentials outside the repository.
- Keep known-safe persisted MCP responses under `.todo/` or another ignored
  path and resolve them through the MCP call log.
- Require explicit approval and `--allow-mutate` for external writes, device input, baseline updates, and other mutating
  actions.
- Prefer small service-specific adapters when a direct REST/CLI integration is safer than MCP.
