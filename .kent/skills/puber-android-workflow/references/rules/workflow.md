# Kent Workflow

Use `.kent/commands/` as the active command set. Treat `.claude/` as legacy reference unless the user explicitly asks to
change it.

## Commands

- `/prompt:feature-start`: initialize context, design/spec intake, and planning.
- `/prompt:feature-implement`: implement the next approved plan step.
- `/prompt:feature-review`: review implementation against plan/design/spec.
- `/prompt:feature-fix`: apply review fixes.
- `/prompt:refactor-start`: plan and execute an approved refactor with checks.
- `/prompt:migration-start`: plan and execute a migration.
- `/prompt:dependency-update`: update dependencies with verification.
- `/prompt:smoke-test`: run device/emulator smoke checks.
- `/prompt:test-coverage`: add or improve focused tests.

## Gradle

- Main checkout: use `./gradlew`.
- Kent worktrees: use `./tools/agentw`.
- Main compile check: `./gradlew :app:compileDevDebugKotlin`.
- Worktree compile check: `./tools/agentw :app:compileDevDebugKotlin`.
- For noisy errors, filter with:

```bash
./gradlew :app:compileDevDebugKotlin 2>&1 | grep -E "e: |error:|FAILURE|What went wrong" -A3
```

## Worktrees

- Kent worktrees must live under `.kent/worktrees/`.
- Do not create sibling worktrees such as `../Puber-<task>`.
- Kent `/wt create` runs `.kent/worktrees/setup.sh`.
- Manual `git worktree add` does not run setup automatically; use `.kent/worktrees/<name>` explicitly and run setup if
  needed.

## Local State

- `.todo/` is local task memory and should remain ignored.
- Raw MCP output should go under `.todo/<task>/mcp/` or `.todo/_mcp-raw/`.
- Do not force-add local secrets, `.mcp.json`, generated raw outputs, or worktree directories.
