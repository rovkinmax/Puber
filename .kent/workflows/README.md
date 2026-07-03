# Puber Kent Workflows

Kent workflow graphs live in the Kent server database. JSON exports, when present, are audit snapshots, not the source of
truth. The current CLI can inspect and edit workflows, but does not import these snapshots as canonical definitions.

## Portable Pattern

Do not link Appsome workflow instances directly into Puber. Reuse the graph family and transition contract, then create a
Puber workflow instance whose prompts call `.kent/project-contract.md`.

The intended layering is:

```text
Kent Desktop workflow graph
  -> stable project contract params and command names
      -> Puber .kent/commands/*
          -> puber-android-workflow recipes, rules, adapters, and agents
```

## Recommended Workflow Families

- `Puber Feature Delivery`: plan -> implement -> audit -> fix loop -> optional smoke -> optional PR/CI -> cleanup.
- `Puber Refactor With Audit`: plan and audit -> implement -> review/fix loop -> optional PR/CI -> cleanup.
- `Puber Bugfix Investigation`: reproduce/diagnose -> fix or report-only -> verify -> optional PR/CI -> cleanup.
- `Puber Release Preparation`: prepare version branch and local verification; pushes require approval.
- `Puber Release Publication`: publish an already prepared release; tags/releases require approval.
- `Puber Dependency Update`: update dependency or tooling version -> focused compile/tests -> fallout review.

## Authoring Rules

- Use `default` as node assignee unless Kent workflow validation can see project-local roles.
- Delegate to project roles inside prompts, for example `kent run --agent implementation-worker ...`.
- Every edge to `blocked` must require `blocker_reason`.
- Every successful terminal path should pass through `cleanup`, but cleanup is conservative by default.
- Pass explicit `workspace_path` and `plan_path`; never rely on `.todo/.current`.
- Keep prompts project-neutral where possible: "run the project feature planning command" rather than naming another
  repository's skill path, Jira project, module graph, or release process.

## Workflow Smoke Test Checklist

Before making a workflow default for the project:

- Create a dummy task that reaches planning and emits `workspace_path`/`plan_path`.
- Exercise a blocked path and verify `blocker_reason` is visible.
- Exercise an implementation continuation path and verify params are re-emitted.
- Exercise cleanup in conservative mode and verify `cleanup_report`.
- Validate with `kent workflow validate "<workflow>" --mode execution`.
