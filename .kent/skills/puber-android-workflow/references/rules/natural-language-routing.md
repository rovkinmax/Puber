# Natural Language Routing

Recognize common workflow requests even when the user does not type a slash command.

## Feature Workflow

- If the user shares a Figma URL or says "start feature", run the feature-start flow.
- If the user says "next step", "let's code", "implement step N", or similar, run feature implementation.
- If the user says "review", "audit", or "check against design", run review.
- If the user asks "status", "where are we", or "progress", read `.todo/.current` and summarize current plan progress.
- If `.todo/.current` exists, mention the active feature at the start of a feature-related session.

## Build Workflow

- If the user pastes a build error, diagnose root cause before proposing broad changes.
- If the user asks for a device smoke test, prefer the smoke-test workflow and MCP/mobile rules when available.

## Constraints

- Do not change `.todo/.current` during status, review, or implementation unless explicitly requested.
- Do not load every recipe eagerly. Load only recipes relevant to the current workflow.
- Prefer Kent subagents for focused research or review instead of broad context-heavy searches.
