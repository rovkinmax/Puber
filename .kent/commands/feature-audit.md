# Feature Audit

Run a read-only audit of an explicit feature plan or implementation.

This is the Kent alias for the existing Puber review workflow. Use `.kent/commands/feature-review.md` as the detailed
review procedure, but keep this command read-only unless the user explicitly asks for fixes.

## Runtime Rules

- Follow `.kent/skills/puber-android-workflow/SKILL.md`.
- Load `references/rules/feature-target-resolution.md`, `references/rules/workflow.md`, `references/rules/mcp.md`, and
  only recipes relevant to changed files.
- Use `kent run --agent compose-reviewer --workspace "$PWD" "<prompt>"` for Compose-heavy review.
- Use `kent run --agent domain-model-reviewer --workspace "$PWD" "<prompt>"` for data/domain mapping review.
- Do not edit files, commit, or push.

## Output

Return:

- scope audited;
- findings sorted by severity;
- exact file paths and symbols;
- verification commands recommended;
- whether `/prompt:feature-fix` is needed.
