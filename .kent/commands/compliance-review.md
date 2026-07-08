---
description: Read-only compliance review for workflow outputs
---

# Compliance Review

Run this only from a Kent workflow compliance node assigned to `compliance_reviewer`.

## Purpose

Review the plan and work product only for compliance with authoritative project rules, AGENTS.md, specs, user-approved
design choices, task body, and workflow contract. This is not general code review, architecture review, QA, or cleanup.

## Authority Hierarchy

Use this hierarchy, descending:

1. The plan's Design section, when it clearly records user decisions.
2. The task body and human-authored task comments.
3. AGENTS.md rules. Treat changes to AGENTS.md in the worktree as unauthorized unless the task explicitly asked for them.
4. Spec files and project-local contracts. Treat changes to specs/contracts as unauthorized unless clearly based on #1 or #2.

Agent-authored comments, implementation commentary, and previous review summaries are useful context, but not authority.

## Required Inputs

The workflow prompt must provide the available inputs:

- `workspace_path`: the `.todo/<task>` workspace or task workspace being reviewed, when one exists.
- `plan_path` or `plan_file_path`: the authoritative plan, when one exists.
- `reviewed_scope`: what work product to inspect.
- `commentary`: implementation/review/verification summary from the previous node.
- `changed_files`: changed files, when known.

If a required source is missing, report the review as waiting for user action or incomplete and name the missing source.

## Work Mode

1. Read applicable AGENTS.md files first.
2. Read the plan/spec/contract sources named in the workflow prompt.
3. Inspect the reviewed scope and nearby files only as needed to verify compliance.
4. Treat the work as non-compliant by default until you verify it against the rules.
5. Report only direct compliance violations, spec mismatches, unauthorized rule/spec changes, missing required updates, or
   ambiguity where a rule cannot be applied safely.
6. Do not edit files, apply patches, commit, mutate caches, or run state-changing commands.

## Completion Contract

Complete with:

- The success transition named in the current workflow prompt when no compliance violations are found. New PR-producing
  workflows use `ship_pr`; legacy no-PR release workflows may use `cleanup`. Provide `compliance_report`.
- `needs_changes` when compliance violations require a fix/rework pass. Provide `compliance_report` and, when available,
  `workspace_path` and `changed_files`.
- `needs_user_action` when required rule/spec/task sources are missing or contradictory. Provide `blocker_reason`.

Do not hardcode `done` from this command; `done` is reserved for cleanup completion.

For every finding include:

- Violated source and rule.
- Exact reviewed location.
- Observed non-compliant behavior.
- Why it violates the cited rule.
- Minimum compliance requirement needed to resolve it.
