# Security

DevForge is self-hosted software that holds a team's documentation and runs its
own authentication, so the security of a deployment is mostly the operator's.
This file covers both halves: how to report a flaw, and what an operator has to
get right.

## Reporting a vulnerability

Report privately through GitHub's
[security advisories](https://github.com/ensui-dev/devforge/security/advisories/new).
Please do not open a public issue for anything exploitable.

Include what you did, what happened, and what you expected. A failing request is
worth more than a description of one. You will get an acknowledgement; this is a
personal project, so expect days rather than hours.

## What an operator must get right

### Change `DEVFORGE_JWT_SECRET`

The default in `application.properties` is the literal string
`dev-only-insecure-signing-key-change-me-please`. It is public in this repository,
so anyone who has read the source can mint valid tokens for a deployment still
using it. Generate your own:

```bash
openssl rand -base64 48
```

The application refuses to start on a secret shorter than 32 characters. Rotating
it invalidates every issued token.

### Finish setup immediately

`POST /api/setup` is open until it succeeds once, then closed permanently. That
protects a running instance — the endpoint can never mint an administrator on a
configured one — but it means a deployment left reachable before its operator
finishes can be claimed by whoever gets there first. There is no recovery path in
the product. Bring an instance up on a closed port, or complete setup at once.

### Keep more than one instance administrator

The settings screen refuses to remove the last administrator, because an instance
with none can never be reconfigured by anything inside the product. That guard
does not help if the single administrator loses their password. Appoint a second.

### Know what publishing exposes

Publishing a workspace makes its non-internal documents readable by anyone with
the link, with no account. Boards, tasks, and the member list stay private.
Documents marked internal are filtered in SQL, not in the client. An operator can
switch public documentation off instance-wide, which takes every published site
offline at once.

## Design decisions relevant to security

- **Tokens are HMAC-signed JWTs** with a 12-hour life and no refresh or
  revocation. Signing out discards the token client-side; it stays valid until it
  expires. Rotating the signing secret is the only way to invalidate tokens early.
- **Passwords are hashed with bcrypt** at the default cost.
- **Non-members get 404, not 403**, for workspaces they cannot see, so the API
  cannot be used to test whether a workspace exists.
- **Login gives one error** for an unknown address and a wrong password alike, so
  it cannot be used to enumerate accounts.
- **Public reads cannot reach private data structurally**, not by filtering after
  the fact: the repository method used by public endpoints cannot return an
  unpublished workspace, and internal documents are excluded in the query.
- **CORS is off by default.** In the shipped Compose setup nginx proxies `/api`
  from the same origin, so there is no cross-origin request to allow.

## Known gaps

These are deliberate omissions, not oversights, but they are things an operator
should know before exposing an instance publicly:

- **No rate limiting** on authentication. Put this behind a reverse proxy or WAF
  that provides it if the instance is internet-facing.
- **No email verification.** Registration trusts the address given. On a public
  instance use `RESTRICTED` or `CLOSED` registration if that matters.
- **No password reset.** An administrator can create accounts and issue a
  temporary password; nobody can reset their own.
- **No audit log.** Changes are not attributed beyond the `updated_at` and
  version columns.
