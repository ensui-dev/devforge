# DevForge

A documentation and delivery tool for software teams. It combines an
interconnected knowledge base — architecture, decisions, procedures, the stack —
with kanban delivery tracking, and links the two so that work points at the
documents it depends on instead of restating them.

The distinguishing idea is the **typed reference graph**. Documents do not merely
cross-link; each edge has a meaning (`DEPENDS_ON`, `IMPLEMENTS`, `SUPERSEDES`,
`DOCUMENTS`, `RELATED`), and every edge is visible from both ends. So "what breaks
if I change this page?" is a query, not a search.

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

Register an account in the UI, create a workspace, and you are its owner.

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
- **Search maintained by the database.** `documents.search_vector` is a generated
  `tsvector` column with a GIN index, so results can never drift from content, and
  cost scales with the number of matches rather than the number of documents.
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

## Configuration

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
| `PORT` | `8080` | |

## Not built yet

Deliberate omissions, in rough priority order:

- **Refresh tokens.** Access tokens last 12 hours with no rotation, so a long
  session eventually ends with a redirect to sign-in.
- **Email invitations.** Members are added by email address, but the person must
  already have registered.
- **Document history.** Optimistic locking prevents lost updates, but previous
  revisions are not retained.
- **A rendered graph view.** Connections are listed per document; there is no
  whole-workspace visualisation.
- **Rate limiting** on the authentication endpoints.
