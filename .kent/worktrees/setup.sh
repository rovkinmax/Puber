#!/usr/bin/env bash
set -euo pipefail

mkdir -p "$HOME/.gradle-agents"

git_common_dir="$(git rev-parse --path-format=absolute --git-common-dir 2>/dev/null || true)"
main_workspace=""
if [[ "$git_common_dir" == */.git ]]; then
  main_workspace="${git_common_dir%/.git}"
fi

if [[ -n "$main_workspace" && "$PWD" != "$main_workspace" && ! -e local.properties ]]; then
  main_local_properties="$main_workspace/local.properties"
  sdk_property=""
  if [[ -f "$main_local_properties" ]]; then
    sdk_property="$(grep -m1 '^sdk\.dir=' "$main_local_properties" || true)"
  fi

  if [[ -n "$sdk_property" ]]; then
    umask 077
    printf '%s\n' "$sdk_property" > local.properties
    cat >&2 <<'EOF'
[kent worktree setup] Created local.properties with sdk.dir only.
Project API secrets were not copied; provide them through environment variables when a task requires runtime access.
EOF
  else
    cat >&2 <<'EOF'
[kent worktree setup] Android sdk.dir is unavailable.
Configure sdk.dir in the primary workspace local.properties before running Android builds here.
EOF
  fi
fi

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
