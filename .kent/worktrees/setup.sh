#!/usr/bin/env bash
set -euo pipefail

mkdir -p "$HOME/.gradle-agents"

payload="${KENT_WORKTREE_PAYLOAD_JSON:-}"
if [[ ! -t 0 ]]; then
  stdin_payload="$(cat || true)"
  if [[ -n "$stdin_payload" ]]; then
    payload="$stdin_payload"
  fi
fi

json_value() {
  local expression="$1"
  [[ -n "$payload" ]] || return 0
  jq -er "$expression // empty | strings" <<<"$payload" 2>/dev/null || true
}

payload_source_workspace="$(json_value '.source_workspace_root')"
payload_branch_name="$(json_value '.branch_name')"
payload_worktree_root="$(json_value '.worktree_root')"

source_workspace="${KENT_WORKTREE_SOURCE_WORKSPACE_ROOT:-${1:-$payload_source_workspace}}"
branch_name="${KENT_WORKTREE_BRANCH_NAME:-${2:-$payload_branch_name}}"
worktree_root="${KENT_WORKTREE_ROOT:-${3:-$payload_worktree_root}}"
worktree_root="${worktree_root:-$(pwd -P)}"

cat >&2 <<EOF
[kent worktree setup] Context:
  source workspace: ${source_workspace:-unavailable}
  branch: ${branch_name:-unavailable}
  worktree: $worktree_root
EOF

if [[ -x "./tools/configure-worktree-sdk" ]]; then
  ./tools/configure-worktree-sdk "$source_workspace"
else
  cat >&2 <<'EOF'
[kent worktree setup] ./tools/configure-worktree-sdk was not found or is not executable.
Android builds may fail until sdk.dir is configured in this worktree.
EOF
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
