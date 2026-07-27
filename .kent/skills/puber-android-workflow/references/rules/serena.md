# Serena Semantic Workflow

Use Serena as an optional semantic/LSP layer for Kotlin code structure and targeted symbol lookup. Kent still owns shell,
text search, file reads, patch edits, Gradle, and final verification.

## Invocation

Serena is available through the Kent mcporter adapter, not as native `serena.*` tools:

```bash
MCPORTER_CALL_TIMEOUT=180000 \
  ~/.kent/bin/kent-mcp-call serena.<tool> [arguments] \
  --output json \
  --raw-dir .todo/<feature-or-task>/mcp \
  >/dev/null
```

Record the MCP call-log length immediately before the call, then resolve and
read the exact successful `rawOutputPath` through the parent skill's persisted
response handoff. Do not emit the raw Serena response into Kent's shell
transcript or guess its timestamped filename.

Use `MCPORTER_CALL_TIMEOUT=180000` for first or large-project calls. Kotlin LSP startup can take several minutes.

The global adapter serializes Serena calls per checkout/worktree and cleans
orphan Kotlin LSP processes for that root. Do not bypass it with parallel raw
`mcporter` or direct Serena server processes.

## Useful Tools

- `serena.get_symbols_overview relative_path=<file.kt>`: first call for unfamiliar Kotlin files.
- `serena.find_symbol name_path_pattern=<Name/member> relative_path=<file.kt> depth=1 include_body=false`: inspect
  symbol structure.
- `serena.find_symbol ... include_body=true`: read only the exact symbol body needed.
- `serena.find_referencing_symbols name_path=<symbol> relative_path=<file.kt>`: check semantic callers before changing
  contracts, then cross-check with `rg`.
- `serena.find_implementations name_path=<symbol> relative_path=<file.kt>`: inspect interface implementations.
- `serena.get_diagnostics_for_file relative_path=<file.kt>`: focused diagnostics after edits.
- `serena.rename_symbol`: use only when semantic rename is safer than text edits.

Line numbers returned by Serena are 0-based. Treat empty Kotlin reference results as incomplete unless `rg` confirms no
usages.

## Reliability Rules

- Prefer Serena for symbol overview, symbol bodies, declaration lookup, and focused diagnostics.
- Prefer `rg` for strings, resources, routes, actions, ViewState fields, mapper methods, DI bindings, and config.
- Always cross-check references with `rg` before changing shared contracts.
- Final authority is Gradle compile/tests and relevant smoke checks.
