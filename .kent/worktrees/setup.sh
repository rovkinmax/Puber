#!/usr/bin/env bash
set -euo pipefail

mkdir -p "$HOME/.gradle-agents"

cat >&2 <<'EOF'
[kent worktree setup] Gradle worktree policy:
  - use ./tools/agentw <task> for Gradle commands in Kent worktrees
  - avoid direct ./gradlew in Kent worktrees unless GRADLE_USER_HOME is explicit
  - agent Gradle home: ~/.gradle-agents
EOF

if [[ ! -f "$HOME/.kent/mcp.Puber.env" ]]; then
  cat >&2 <<'EOF'
[kent worktree setup] ~/.kent/mcp.Puber.env is not configured.
Set MCP_CONFIG_PATH there if this worktree needs project-bound Puber MCP access.
If unset, MCP wrappers fall back to mcporter default/global discovery:
MCP_CONFIG_PATH=/absolute/path/to/main/Puber/.mcp.json
EOF
fi

if [[ ! -x "./tools/agentw" ]]; then
  cat >&2 <<'EOF'
[kent worktree setup] ./tools/agentw was not found or is not executable.
Do not run heavy Gradle tasks until Gradle isolation is configured.
EOF
fi
