# Contributing

## Getting it running

```bash
docker compose up -d postgres          # PostgreSQL 16 on :5432
cd backend && ./mvnw spring-boot:run   # API on :8080
cd frontend && npm install && npm run dev   # app on :5173, proxies /api
```

The dev server proxies `/api` to the backend, so there is no cross-origin traffic
and CORS stays off. The first page load redirects to `/setup`, because a fresh
database has no instance.

## Running the tests

```bash
cd backend  && ./mvnw test    # unit, integration (Testcontainers), ArchUnit
cd frontend && npm test       # Vitest + Testing Library
cd frontend && npm run lint && npm run build
```

The backend integration tests start a real PostgreSQL 16 container, so Docker
must be running. They share one context and one container; `DatabaseCleaner`
empties every table between classes.

## Architecture rules are executable

The backend is a modular monolith. Each feature module — `identity`, `workspace`,
`document`, `task`, `instance` — owns its data and publishes a `contract` package.
Everything else in it is private.

```
com.devforge.<module>.contract          published; depended on by other modules
com.devforge.<module>.api               controllers
com.devforge.<module>.application       services and DTOs
com.devforge.<module>.domain            entities and repository interfaces
com.devforge.<module>.infrastructure    adapters
```

`ArchitectureTest` enforces this, so a boundary violation is a failing build
rather than a review comment. It will fail you for importing another module's
service, returning another module's entity, letting a contract reference an
internal type, or putting a repository outside a `domain` package.

**Cross-module references are held by UUID, not by JPA association.** A document
records a `workspaceId`, not a `Workspace`. Foreign keys still enforce integrity
at the database level; the object graph simply does not span modules.

## Testing conventions

Tests here are expected to state a property and fail when it breaks. Two habits
matter more than coverage:

**Name the behaviour, not the method.** `refusesToRunTwice` and
`switchingItOffHidesDocumentationThatWasAlreadyPublished` say what the system
guarantees. `testSetup2` does not.

**Verify the guard actually catches the regression.** A test that passes whether
or not the code is correct is worse than none, because it reads like protection.
Before trusting a new test, break the thing it covers and watch it fail. Several
tests in this repository carry a comment explaining the specific bug they were
written against — a vacuous CSS assertion that inspected an empty string, a cache
key that made pagination request rows that did not exist. Those comments are the
point; keep them when you touch the code.

**Prefer making the unsafe case impossible over filtering it afterwards.** The
public documentation endpoints do not fetch workspaces and then check whether they
are published; they use a repository method that cannot return an unpublished one.
That pattern is worth following.

## Frontend conventions

- TanStack Query for server state; every key lives in `shared/api/queryKeys.ts`.
  **Include everything that changes the response in the key** — page size is in
  there because leaving it out made four screens share one cache entry and break
  pagination.
- Design tokens live in `src/styles/tokens.css`. Use them; do not hard-code
  colours. Both themes are defined, and the viewer's toggle must win over
  `prefers-color-scheme` in both directions.
- Components take their copy as props rather than reaching for a global string
  table. The instance's name, mark, and accent come from `useInstance()`, because
  none of them are constants in self-hosted software.

## Commits and pull requests

Explain why in the commit message; the diff already shows what. Keep the test
suite green — CI runs the backend suite, the frontend suite, lint, the build, and
both Docker images on every push and pull request.

If you change the database, add a Flyway migration under
`backend/src/main/resources/db/migration`. Never edit one that has been released:
migrations are applied at startup on deployments you do not control.

## Reporting security issues

Not here. See [SECURITY.md](SECURITY.md).
