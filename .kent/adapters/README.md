# Adapters

Adapters are project-local wrappers around external tools. They give Kent agents stable commands, raw-output storage, and
mutation safety without depending on native MCP tool namespaces being available in every session.

Included:

- `mcp/`: `mcporter` bridge for MCP servers such as Figma, Notion, mobile testing, Firebase, JetBrains, or Serena.

Rules:

- Keep credentials outside the repository.
- Save raw outputs under `.todo/` or another ignored path.
- Require explicit approval and `--allow-mutate` for external writes, device input, baseline updates, and other mutating
  actions.
- Prefer small service-specific adapters when a direct REST/CLI integration is safer than MCP.
