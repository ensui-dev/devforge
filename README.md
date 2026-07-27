# DevForge

A documentation and delivery tool for software teams. It combines an
interconnected knowledge base — architecture, decisions, procedures, the stack —
with kanban delivery tracking, and links the two so that work points at the
documents it depends on instead of restating them.

The distinguishing idea is the **typed reference graph**. Documents do not merely
cross-link; each edge has a meaning (`DEPENDS_ON`, `IMPLEMENTS`, `SUPERSEDES`,
`DOCUMENTS`, `RELATED`), and every edge is visible from both ends. So "what breaks
if I change this page?" is a query, not a search.

DevForge is open source under the [MIT licence](LICENSE) and built to be
self-hosted. A fresh deployment configures itself through a first-run setup
screen — name, mark, accent, registration policy, and the account that will
administer it — so nothing about an instance is baked into the build. See
[Self-hosting](#self-hosting).

## Stack

| Layer | Technology |
|-------|------------|
| Frontend | React 19, TypeScript, Vite, TanStack Query, React Router |
| Backend | Java 21, Spring Boot 4, Spring Data JPA, Spring Security (JWT), Flyway |
| Database | PostgreSQL 16 |
| Testing | JUnit 5, Mockito, AssertJ, Testcontainers, ArchUnit, Vitest, Testing Library |

## Getting started

### 1. Start PostgreSQL

```bash
docker compose up -d postgres
```

If port 5432 is already in use on your machine, pick another and tell the backend
where to look:

```bash
DEVFORGE_DB_PORT=5433 docker compose up -d postgres
export DEVFORGE_DB_URL=jdbc:postgresql://localhost:5433/devforge
```

### 2. Run the backend

```bash
cd backend
./mvnw spring-boot:run
```

- API: `http://localhost:8080`
- Interactive API docs: `http://localhost:8080/swagger-ui.html`
- Health: `http://localhost:8080/actuator/health`

### 3. Run the frontend

```bash
cd frontend
npm install
npm run dev
```

App: `http://localhost:5173`. The dev server proxies `/api` to the backend, so
there is no cross-origin traffic and CORS stays switched off.

The first time you open the app it redirects to `/setup`, because no one has
claimed the instance yet. Work through the four steps, and the account you create
at the end is signed in and holds the instance settings. Create a workspace and
you are its owner.

### Everything in containers

```bash
cp .env.example .env
openssl rand -base64 48        # paste as DEVFORGE_JWT_SECRET in .env
docker compose --profile full up --build
```

Serves the app at **`http://localhost:3000`** — with nginx proxying `/api` to the
backend over the compose network. Compose reads `.env` automatically.

> **Type the `http://` prefix.** Nothing here serves HTTPS. Firefox and Chrome
> upgrade bare `localhost:3000` to `https://`, which fails with
> `SSL_ERROR_RX_RECORD_TOO_LONG` (Firefox) or `ERR_SSL_PROTOCOL_ERROR` (Chrome).
> If your browser has cached the upgrade, open a private window, or clear the
> HSTS entry for `localhost` in `about:networking#hsts` / `chrome://net-internals/#hsts`.

## Pages

| Route | Needs an account | What |
|-------|------------------|------|
| `/` | no | Homepage: what DevForge is, features, how it works |
| `/docs` | no | This instance's handbook, or the directory of published workspaces |
| `/docs/:handle` | no | Everything one owner has published |
| `/docs/:handle/:workspace/:page` | no | Any published workspace's documentation |
| `/login`, `/register` | no | |
| `/app` | yes | Your workspaces |
| `/workspaces/:id/…` | yes | Documents, boards, team, settings |

The homepage and handbook share a header, so the documentation is one click away
from anywhere and vice versa.

## The handbook

DevForge documents itself. The pages live in
[devforge-docs](https://github.com/ensui-dev/devforge-docs), a submodule at `docs/`,
as markdown with the same front matter the sync module reads — so the handbook is
edited, reviewed and merged exactly like the code it describes:

```bash
git clone --recurse-submodules https://github.com/ensui-dev/devforge.git
# already cloned without it
git submodule update --init
```

`scripts/seed_handbook.py` reads `docs/handbook/` and creates a **DevForge Handbook**
workspace from it — 30 pages, 54 typed references, and a learning board whose tasks
cite the pages that explain them:

```bash
python3 scripts/seed_handbook.py            # create or update it
python3 scripts/seed_handbook.py --publish  # and make it public
```

The script is idempotent: run it again after editing the markdown and it updates
existing pages **in place**, so their ids survive and every reference and task
citation pointing at them stays intact.

The pages were string literals in that script until the submodule existed, which
meant the only way to read the handbook was to run a seeding script against a live
instance. Keeping a second copy there now would be worse than inconvenient — it
would overwrite edits made in the repository — so the script has no content of its
own, and says so if the submodule has not been checked out.

**`/docs` renders that workspace live.** Edit a page in the app and reload the
docs — no build, no deploy, no export step. The Connections panel on each page is
the real reference graph, so *The typed reference graph* shows backlinks nobody
wrote by hand.

It is therefore both the manual and a worked example of what the product is for.

## Publishing documentation

Any workspace can publish its documentation as a public site inside the app — the
handbook is just the first one to do it.

An admin publishes from **Settings → Public documentation**. The workspace's pages
then become readable at `/docs/{handle}/{workspace-slug}`, by anyone, with no
account. Only documentation is exposed: boards, tasks, and the team list stay
private.

### Namespaced like a repository

Every account gets a **handle** — URL-safe, unique, derived from the email address
at registration and suffixed if taken (`ada`, `ada-2`). It namespaces the workspaces
that account owns, so slugs only have to be unique *per owner*:

```
/docs/acme/nokia        and        /docs/globex/nokia
```

Both teams get the obvious name. Before this, workspace slugs were unique across the
whole instance, so the first team to take `nokia` blocked everyone else.

`/docs/{handle}` lists everything one owner has published. In-app routes are
unaffected — they address workspaces by id, not by slug. Links written before the
change still work: an unambiguous `/docs/{slug}` redirects to its canonical path, and
an ambiguous one falls back to the directory rather than guessing.

### Opt-out, with the state made obvious

Publishing exposes every page **except** those marked internal. That is the useful
default — a 30-page handbook should not need 30 decisions — but it means a page
written later is public as soon as it is saved. So the state is never hidden:

- The navigation rail carries a **Documentation is public** banner on every screen
  in a published workspace.
- Each document shows a **Public** or **Internal** badge beside its type.
- The editor has a *Keep this page internal* control that says what will happen.
- Settings counts both figures, and the publish confirmation names how many pages
  it is about to expose.

Mark a page internal and it stays private whether or not the workspace is
published.

### How this stays contained

The public endpoints are the only unauthenticated view of workspace content, so
containment is structural rather than a check someone has to remember:

- `WorkspaceLookup.findPublished` **cannot return an unpublished workspace** — a
  private one is never loaded, so there is nothing to leak.
- Every public document read goes through a repository method that filters
  `internal = false` **in SQL**, not in Java.
- References resolve through the same public-only lookup, so a public page never
  reveals the title of an internal page it links to.

Each of those is pinned by an integration test.

`/docs` with no owner opens this instance's own handbook, set as `handle/slug`
under **Instance → Public documentation → Handbook path**. Leave it blank and
`/docs` lists every published workspace instead.

An operator can switch public documentation off entirely. That takes every
published site offline at once and refuses new publications — nothing is deleted,
and publishing works again the moment it is switched back on.

## Architecture

The backend is a modular monolith. Each feature module owns its data and publishes
a narrow contract; nothing else about it is visible from outside.

```
backend/src/main/java/com/devforge/
├── identity/     Users, registration, login, JWT issuance
├── workspace/    Workspaces, team membership, roles
├── document/     Documentation pages and the typed reference graph
├── task/         Boards, columns, tasks, document citations
└── shared/       Entity base, error handling, security, config
```

Every module has the same internal shape:

| Package | Holds | Visible to |
|---------|-------|------------|
| `contract` | Interfaces and records the module publishes | Any module |
| `api` | REST controllers | Nobody (entry points) |
| `application` | Services, DTOs, use cases | Its own module |
| `domain` | Entities, repositories, domain services | Its own module |
| `infrastructure` | Technical adapters | Its own module |

### How modules stay decoupled

Cross-module calls go through published interfaces, never through another module's
services or entities:

- `WorkspaceAccess` — the single method every workspace-scoped operation calls to
  authorise a request. Resolving the workspace *is* the permission check, so
  authorisation cannot be enforced in one module and forgotten in another.
- `DocumentDirectory` — resolves documents for the `task` module, always scoped by
  workspace, which makes it impossible to cite a document belonging to another team.
- `UserDirectory` — resolves users for membership and task assignment.

Cross-module references are held **by identifier, not by association**. A document
stores a `workspaceId`, not a `Workspace`. The foreign key still exists in the
database, so integrity and cascading deletes are unaffected, but the object graph
does not span modules — which is why a document test needs no workspace
persistence at all.

These rules are enforced by tests rather than convention: `ArchitectureTest` fails
the build if a module reaches into another's internals, if a contract references
internals, or if layering within a module is violated.

### Notable design decisions

- **Aggregate boundaries.** `Board` owns its columns, so the "positions are
  contiguous from zero" invariant has exactly one owner. `Task` is a separate
  aggregate referencing its column by id, because a board may hold thousands of
  tasks and moving one should not load them all.
- **Ordering as a pure function.** `TaskOrdering` does the position arithmetic with
  no persistence or Spring involved, so every reorder, cross-column move, and
  delete-compaction case is exhaustively unit tested.
- **Search maintained by the database.** `documents.search_vector` and
  `search_simple` are generated `tsvector` columns with GIN indexes, so results can
  never drift from content and cost scales with the number of matches rather than
  the number of documents. Four ways to match, ranked by confidence: a stemmed
  whole word, so "authenticate" finds "authentication"; an unstemmed prefix, so a
  word finds its page before it is finished; a substring of a title, which is the
  only one that can find a fragment inside a word; and trigram similarity, which
  is what survives a typo.
- **Bounded query counts.** A whole board — columns, tasks, assignees, and cited
  documents across three modules — is assembled in four queries by
  `BoardAssembler`, regardless of size.
- **Optimistic locking everywhere.** Every entity carries a `@Version`; a lost
  update returns `409` rather than silently overwriting a teammate's edit.
- **Errors say what happened.** One `ApiErrorResponse` shape, with per-field
  messages for validation failures. Unmapped exceptions become a logged `500` —
  deliberately *not* a `400` — so a server bug never masquerades as the caller's
  mistake.

## Permissions

A workspace is only reachable by its members. Roles are ranked, and every
capability is monotonic.

| Role | Can |
|------|-----|
| `VIEWER` | Read documents and boards |
| `MEMBER` | Also create and edit documents, boards, and tasks |
| `ADMIN` | Also manage the team and delete boards |
| `OWNER` | Also rename and delete the workspace |

Two invariants are enforced: a workspace always keeps at least one owner, and
nobody may grant a role above their own or act on a member ranked above them.

A non-member receives `404`, not `403`, for anything inside a workspace — a `403`
would confirm the workspace exists and let an outsider enumerate other teams.

## API

All routes require `Authorization: Bearer <token>` except registration and login.

### Authentication

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/auth/register` | Create an account, returns a token |
| `POST` | `/api/auth/login` | Exchange credentials for a token |
| `GET` | `/api/auth/me` | Describe the authenticated user |
| `GET` | `/api/users?q=` | Find users to add to a workspace |

### Workspaces and team

| Method | Path | Role |
|--------|------|------|
| `GET` | `/api/workspaces` | — |
| `POST` | `/api/workspaces` | — |
| `GET` | `/api/workspaces/{id}` | `VIEWER` |
| `PUT` | `/api/workspaces/{id}` | `ADMIN` |
| `DELETE` | `/api/workspaces/{id}` | `OWNER` |
| `GET` | `/api/workspaces/{id}/members` | `VIEWER` |
| `POST` | `/api/workspaces/{id}/members` | `ADMIN` |
| `PUT` | `/api/workspaces/{id}/members/{userId}` | `ADMIN` |
| `DELETE` | `/api/workspaces/{id}/members/{userId}` | `ADMIN`, or yourself |

### Documents

| Method | Path | Role |
|--------|------|------|
| `GET` | `/api/workspaces/{id}/documents?documentType=&page=&size=` | `VIEWER` |
| `GET` | `/api/workspaces/{id}/documents/search?q=&page=&size=` | `VIEWER` |
| `GET` | `/api/workspaces/{id}/documents/{docId}` | `VIEWER` |
| `GET` | `/api/workspaces/{id}/documents/by-slug/{slug}` | `VIEWER` |
| `POST` | `/api/workspaces/{id}/documents` | `MEMBER` |
| `PUT` | `/api/workspaces/{id}/documents/{docId}` | `MEMBER` |
| `DELETE` | `/api/workspaces/{id}/documents/{docId}` | `MEMBER` |
| `GET` | `/api/workspaces/{id}/documents/{docId}/references` | `VIEWER` |
| `POST` | `/api/workspaces/{id}/documents/{docId}/references` | `MEMBER` |
| `DELETE` | `/api/workspaces/{id}/documents/{docId}/references/{refId}` | `MEMBER` |

Listing a document's references returns outgoing links **and** backlinks, each
labelled with its direction. A reference can only be deleted from the document that
declared it.

Document types: `GENERAL`, `CODE`, `PROCEDURE`, `TECHNOLOGY`, `TECH_STACK`,
`ARCHITECTURE`, `API`, `RUNBOOK`, `DECISION`.

### Boards and tasks

| Method | Path | Role |
|--------|------|------|
| `GET` | `/api/workspaces/{id}/boards` | `VIEWER` |
| `GET` | `/api/workspaces/{id}/boards/{boardId}` | `VIEWER` |
| `POST` | `/api/workspaces/{id}/boards` | `MEMBER` |
| `PUT` | `/api/workspaces/{id}/boards/{boardId}` | `MEMBER` |
| `DELETE` | `/api/workspaces/{id}/boards/{boardId}` | `ADMIN` |
| `POST` | `/api/workspaces/{id}/boards/{boardId}/columns` | `MEMBER` |
| `PUT` | `/api/workspaces/{id}/boards/{boardId}/columns/{columnId}` | `MEMBER` |
| `PATCH` | `/api/workspaces/{id}/boards/{boardId}/columns/{columnId}/position` | `MEMBER` |
| `DELETE` | `/api/workspaces/{id}/boards/{boardId}/columns/{columnId}` | `MEMBER` |
| `POST` | `/api/workspaces/{id}/boards/{boardId}/tasks` | `MEMBER` |
| `PUT` | `/api/workspaces/{id}/boards/{boardId}/tasks/{taskId}` | `MEMBER` |
| `PATCH` | `/api/workspaces/{id}/boards/{boardId}/tasks/{taskId}/position` | `MEMBER` |
| `DELETE` | `/api/workspaces/{id}/boards/{boardId}/tasks/{taskId}` | `MEMBER` |
| `POST` | `/api/workspaces/{id}/boards/{boardId}/tasks/{taskId}/documents` | `MEMBER` |
| `DELETE` | `/api/workspaces/{id}/boards/{boardId}/tasks/{taskId}/documents/{docId}` | `MEMBER` |

Editing a task never changes its placement — column and position move through the
dedicated `position` endpoint, so an ordinary edit cannot silently reorder a board.
Columns may carry a work-in-progress limit, checked when a task arrives.

### Instance

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/public/instance` | Branding and registration policy. No session; how the client renders its own header |
| `POST` | `/api/setup` | First-run setup. Refuses once the instance is configured |
| `GET` | `/api/instance` | Full settings, including operational ones (instance admin) |
| `PUT` | `/api/instance` | Change the settings (instance admin) |
| `POST` | `/api/instance/users` | Create an account regardless of registration mode (instance admin) |
| `GET` | `/api/instance/admins` | List this instance's operators (instance admin) |
| `PUT` | `/api/instance/users/{id}/admin` | Grant or revoke instance administration (instance admin) |

### Git sync

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/workspaces/{id}/sync` | How this workspace syncs (workspace admin) |
| `PUT` | `/api/workspaces/{id}/sync` | Point it at a repository (workspace admin) |
| `POST` | `/api/workspaces/{id}/sync/run` | Sync now and report what happened |
| `POST` | `/api/workspaces/{id}/sync/secret` | Generate a webhook secret, returned once |
| `POST` | `/api/workspaces/{id}/sync/rotate-url` | Mint a new webhook URL |
| `DELETE` | `/api/workspaces/{id}/sync` | Disconnect and forget the credentials |
| `POST` | `/api/public/sync/{webhookId}` | Webhook endpoint; verified by HMAC signature |
| `GET` | `/api/workspaces/{id}/git` | The hosted repository's clone path and size |

The webhook is unauthenticated because a git host has no session. An HMAC-SHA256
signature over the raw request body is what authorises it, accepted as either
GitHub's `X-Hub-Signature-256` or Forgejo/Gitea's bare-hex header. Anything that does
not verify gets a 404 — distinguishing "no such webhook" from "wrong secret" would
confirm that a workspace syncs.

### Git hosting

DevForge serves the smart-HTTP protocol at `/git/{owner-handle}/{workspace-slug}.git`,
so a workspace can be cloned and pushed to like any other repository. Cloning needs
`VIEWER`, pushing needs `MEMBER`, and there is no anonymous access — documentation is
published at `/docs`, and a repository is not a second public surface.

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/api/me/git-tokens` | The signed-in account's git credentials |
| `POST` | `/api/me/git-tokens` | Issue one; the secret is returned exactly once |
| `DELETE` | `/api/me/git-tokens/{id}` | Revoke one |

Git speaks HTTP Basic and nothing else, so authentication is a token rather than an
SSH key, presented as the password. Only a SHA-256 digest is stored — a plain digest
rather than bcrypt, because the secret is 256 random bits, and a work factor defends
low-entropy secrets while costing time on every request a clone makes.

Syncing is two-way. A push is imported through the same planner the webhook path
uses, and an edit made in the interface becomes a commit authored by whoever made it.
The two cannot loop: a change whose origin is a sync is never committed back. The
commit runs after the edit's transaction commits, so git trouble can leave the
repository behind but cannot make a page fail to save.

## Testing

### Backend

```bash
cd backend
./mvnw test
```

Requires Docker: integration tests start PostgreSQL 16 through Testcontainers,
pinned to the same image the deployment uses.

- Unit tests cover each service with its collaborators stubbed, plus the domain
  logic (`TaskOrdering`, `Board`, `BaseEntity`) directly.
- Integration tests drive the real HTTP stack against a real database, including
  authentication, role enforcement, and cross-workspace isolation.
- `ArchitectureTest` enforces the module boundaries described above.

### Frontend

```bash
cd frontend
npm test          # Vitest + Testing Library
npm run lint      # oxlint
npm run build     # typecheck, then bundle
```

Component and hook tests mock the API layer rather than `fetch`, so they assert on
behaviour instead of URLs. `applyMoveLocally` is tested against the same cases as
the backend's `TaskOrdering`, because the optimistic drag-and-drop update has to
agree with the server or the board would visibly snap back.

## Self-hosting

DevForge is designed to be run by whoever uses it. Everything that makes one
deployment different from another lives in its database, not in the build — so
the same image serves a public instance, a company's private one, and a
single-person notebook.

### Bring it up

```bash
git clone <your fork or this repository>
cd devforge
cp .env.example .env
openssl rand -base64 48        # paste as DEVFORGE_JWT_SECRET in .env
docker compose --profile full up --build -d
```

Open `http://localhost:3000` (type the `http://` — see the note above). An
unclaimed instance redirects every route to `/setup` and shows nothing else.

### First-run setup

Setup runs **once**. It refuses forever afterwards, so a deployment that is
briefly reachable before you finish configuring it cannot be claimed by whoever
gets there second, and the endpoint can never be used to mint an administrator on
a running instance. There is no recovery path in the product if someone else
completes it first: bring the instance up on a closed port, or finish setup
immediately.

The four steps:

| Step | What it decides |
|------|-----------------|
| **Identity** | Name, tagline, and public address. The name appears in the header, on the sign-in screen, and in the browser tab. |
| **Appearance** | A mark (a character or an uploaded image) and an accent colour. The accent replaces one design token; nothing else in the interface moves. |
| **Access** | Whether people may sign themselves up, and from which email domains. |
| **Operator** | The first administrator. This account owns the instance settings and is the only way to add people on a closed instance. |

Everything except the operator account can be changed later, from
**Instance settings** in the workspace header.

### Registration modes

| Mode | Who can create an account | Suits |
|------|---------------------------|-------|
| `OPEN` | Anyone who can reach the deployment | A public instance |
| `RESTRICTED` | Anyone with an email at a listed domain | A company instance on a public address |
| `CLOSED` | Nobody — the operator creates every account | A private or single-team instance |

`RESTRICTED` matches the domain exactly; subdomains are not included. An instance
cannot be left in `RESTRICTED` with no domains listed, because it would accept
nobody — the server refuses those settings outright.

On a closed instance, **Instance → Operators → Add operator** creates accounts
directly, bypassing the registration mode. It is also how a colleague already on
the instance is appointed as a second administrator.

### Keep a second operator

An instance whose only administrator loses their password cannot be reconfigured
by anything inside the product. The settings screen therefore refuses to remove
the last administrator, and says so. Appoint a second one early.

### What an operator controls

| Setting | Effect |
|---------|--------|
| Name, tagline, mark, logo image, accent | Branding, applied everywhere the client renders |
| Registration mode and allowed domains | Who may create an account |
| Allow public documentation | Master switch for published documentation. Off takes every published site offline at once |
| Handbook path | Which published workspace `/docs` opens by default, as `handle/slug` |
| Public address | Used to build absolute links; never shown to visitors |

Branding is read from `GET /api/public/instance`, which needs no session — the
sign-in screen has to know the instance's name before anyone has signed in. That
endpoint deliberately omits operational settings such as the public address.

### Upgrading

Flyway migrations run at startup, so an upgrade is a redeploy. The instance
settings row survives it, along with everything else in the database.

**One requirement worth knowing about**: search installs the `pg_trgm` extension,
which is what makes it survive a typo. From PostgreSQL 13 onwards that is a
*trusted* extension, so the account owning the database can install it without
superuser rights — which is the case for the compose file here and for any
ordinary self-hosted Postgres. On a managed database that both withholds
superuser and does not offer `pg_trgm`, the migration fails and the application
will not start; enable the extension for the database first.

**There are two things to back up**, and it used to be one. Everything DevForge
stores lives in PostgreSQL — including uploaded logos, deliberately, so that a dump
captures an instance whole. Hosted git repositories are the exception: they are
packfiles, written by a protocol that expects a filesystem.

```bash
pg_dump -U devforge devforge > devforge.sql   # everything except repositories
tar czf devforge-git.tgz "$DEVFORGE_GIT_ROOT"
```

The compose file in this repository keeps them in a named volume, which `tar` on
the host cannot see:

```bash
docker run --rm -v devforge_git:/data -v "$PWD:/out" alpine \
    tar czf /out/devforge-git.tgz -C /data .
```

A repository can be reconstructed by pushing again, so losing one is recoverable
where losing the database is not — but only if someone still has a clone.

## Configuration

These are the settings that must exist before the application can start — the
database it talks to and the key it signs tokens with. Everything an operator
would recognise as a *product* setting (name, mark, registration policy, whether
documentation is public) lives in the database and is set through
[first-run setup](#first-run-setup), not here.

Every value has a working development default, so the app runs with no setup at
all. Copy `.env.example` to `.env` and generate a secret before anyone else can
reach the instance:

```bash
cp .env.example .env
openssl rand -base64 48        # paste as DEVFORGE_JWT_SECRET
```

`.env` is gitignored. Docker Compose reads it automatically; running the backend
directly with `./mvnw` does **not**, so export the variable in that shell:

```bash
export $(grep -v '^#' .env | xargs)
cd backend && ./mvnw spring-boot:run
```

Rotating `DEVFORGE_JWT_SECRET` invalidates every issued token, so everyone signs
in again.

| Variable | Default | Notes |
|----------|---------|-------|
| `DEVFORGE_DB_URL` | `jdbc:postgresql://localhost:5432/devforge` | |
| `DEVFORGE_DB_USERNAME` | `devforge` | |
| `DEVFORGE_DB_PASSWORD` | `devforge` | |
| `DEVFORGE_JWT_SECRET` | a development placeholder | **Must be replaced in any deployment.** Validated at startup; the application refuses to boot on a secret shorter than 32 characters. |
| `DEVFORGE_CORS_ORIGINS` | empty | Comma-separated. Only needed if the client is served from another origin; CORS is off when empty. |
| `DEVFORGE_DB_PORT` | `5432` | Host port for the compose database. |
| `DEVFORGE_GIT_ROOT` | `/var/lib/devforge/git` | Where hosted git repositories live. Back this up alongside `pg_dump`. |
| `DEVFORGE_GIT_ENABLED` | `true` | Serve git over HTTP. Switch off on an instance that only syncs from an external remote. |
| `PORT` | `8080` | |

## Not built yet

Deliberate omissions, in rough priority order:

- **Refresh tokens.** Access tokens last 12 hours with no rotation, so a long
  session eventually ends with a redirect to sign-in.
- **Email invitations.** Members are added by email address, but the person must
  already have registered.
- **A rendered graph view.** Connections are listed per document; there is no
  whole-workspace visualisation.
- **Rate limiting** on the authentication endpoints.
- **No retention policy** for history. Revisions and audit rows accumulate
  indefinitely. Both tables stay small — audit rows are a few hundred bytes, and a
  document body is stored once per distinct content, so a restore or a reverted edit
  adds none — but nothing prunes them. Deleting a document removes its revisions;
  its audit entries are kept deliberately.
- **Importing git history.** A sync applies the state of a ref rather than replaying
  commits into revisions.
- **Committing back to an external remote.** Edits become commits in the repository
  DevForge hosts, never in a repository somewhere else — following a remote stays
  one-way.
- **Password reset.** An operator can create accounts and hand out a temporary
  password, but nobody can reset their own.
- **SMTP.** Nothing sends email, so registration is not verified and there are no
  invitations.
