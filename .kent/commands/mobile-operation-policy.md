# Puber mobile operation policy

Resource ownership and operation authorization are separate requirements.
Use the exact endpoint of the selected Puber resource lease; never discover
or substitute a device after acquisition. Check ownership before every
product-owned install, diagnostic or other direct device operation.

Smoke uses `smoke.persistent` and Release uses `release.clean`. The new
persistent Smoke TV does not inherit the old TV's authentication. Missing
authentication or login authority blocks the affected scenario. Do not
transfer credentials or authentication state automatically.

Safe navigation does not authorize playback-progress, account, subscription,
purchase or other server-visible changes. Those actions require explicit
Puber task authority. Appsome permissions are not Puber permissions.

Install only through the preservation installer. Never uninstall, clear app
data, downgrade or replace a signer without separate explicit authority.
Synthetic Release instrumentation uses only its benchmark-owned loopback
backend and synthetic authentication; it must not use authenticated Smoke
state, production services or real credentials.

Keep credentials, receipts, broad UI dumps, screenshots, raw authenticated
responses and broad logs out of Git and workflow evidence. Retain only
scoped, sanitized observations through the qualified evidence procedure.
Do not interpret possession of a lease as permission to expose private data.

Unknown ownership, an ambiguous acquisition, lease loss, uncertain command
stop or unconfirmed cleanup blocks success and automatic replay. Preserve
partial outputs and private recovery records. Never forge legacy ownership
or convert legacy checkpoints into resource-lease ownership.
