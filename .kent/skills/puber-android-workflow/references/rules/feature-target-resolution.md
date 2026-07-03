# Feature Target Resolution

Feature workflow commands must use an explicit `.todo/<feature>` workspace. There is no implicit global feature.

## Target Sources

Resolve a feature workspace in this order:

1. Explicit local path in the user prompt or workflow prompt, such as `.todo/my-feature` or `/absolute/path/.todo/my-feature`.
2. Explicit `--workspace <path>` argument when a command supports flags.
3. Explicit feature name in the user prompt, normalized to the existing `.todo/<feature>` directory name.
4. Kent workflow task context when the workflow prompt names the feature workspace.
5. If none is available, stop and ask for the feature name or `.todo/<feature>` path.

Do not infer a target from any global pointer file. Do not create or update global feature pointer files.

## Matching Rules

- Exact `.todo/<name>` path wins.
- Exact directory name match wins.
- If multiple `.todo/` directories partially match the prompt, list candidates and ask.
- If no directory exists and the command is a creation flow such as feature-start, derive a kebab-case name from the task
  title/body or ask the user.
- If no directory exists and the command is implementation, audit, review, or smoke, stop and ask for an existing target.

## Metadata

When a command creates a workspace, write target identity into `meta.json`:

```json
{
  "name": "<feature>",
  "createdAt": "<yyyy-mm-dd>",
  "currentStep": 0
}
```

When running inside Kent Desktop workflow tasks, also add task identifiers when they are available:

```json
{
  "kentTaskId": "<task id>",
  "kentTaskShortId": "<short id>",
  "workflow": "<workflow name>"
}
```
