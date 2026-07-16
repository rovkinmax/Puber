# Runtime Smoke Decision Policy

Use this procedure only in the post-verification Gate. Implementation must not
start, skip, or ask about Smoke on its own.

## Smoke Required

Choose `smoke_required` when the change affects or may affect:

- TV UI, focus behavior, D-pad input, navigation, overlays, or visible state;
- ViewModel state, screen wiring, dependency injection, startup, or deep links;
- playback, networking, persistence, authentication, permissions, or runtime
  feature flags;
- packaged Android resources, manifest behavior, build variants, or application
  configuration;
- a crash, regression, or acceptance criterion that requires runtime evidence;
- a runtime path whose impact is uncertain after deterministic verification.

Provide an evidence-based `smoke_rationale` and a focused `smoke_scope` naming
the screens, remote-control actions, states, and failure signals to exercise.

## Delivery Ready Without Smoke

Choose `delivery_ready` only when there is positive evidence that the change
cannot affect a runtime artifact or user-observable behavior. Typical examples
are documentation, Kent workflow assets, agent prompts, repository metadata, or
tests that do not change production sources.

A behavior-preserving internal refactor may skip Smoke only when its runtime
surface is unchanged and focused deterministic tests cover the affected logic.
Formatting, compile success, or lack of an available emulator is not sufficient
evidence by itself.

Provide `smoke_rationale` with the concrete evidence for skipping.

## Resource Failures

Decide whether Smoke is required before checking emulator or device
availability. If required resources are unavailable, keep `smoke_required`; the
Smoke node must use `needs_user_action`. Never downgrade to `delivery_ready`
because a device, account, lock, or external service is unavailable.
