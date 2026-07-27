#!/usr/bin/env python3
"""
Populates a DevForge instance with the DevForge Handbook: the platform's own
documentation, written as DevForge content.

This is deliberately dogfooding. The handbook is not only what a new user reads —
it is a worked example of the thing DevForge is for. The documents carry typed
references to each other, and a delivery board links onboarding tasks to the pages
that explain them, so a reader sees the reference graph doing real work rather
than a screenshot of it.

Usage:
    python3 scripts/seed_handbook.py                      # create or update
    python3 scripts/seed_handbook.py --publish            # and publish it
    python3 scripts/seed_handbook.py --base http://localhost:3000

Idempotent. Run it once to create the handbook, and again after editing the content
below to bring an existing one up to date: pages are updated in place, so their ids
survive and every reference and task citation pointing at them stays intact.
"""

from __future__ import annotations

import argparse
import json
import sys
import urllib.error
import urllib.request
from typing import Any

# --------------------------------------------------------------------------
# HTTP plumbing
# --------------------------------------------------------------------------


class ApiError(RuntimeError):
    def __init__(self, status: int, body: str, method: str, path: str) -> None:
        super().__init__(f"{method} {path} -> {status}: {body[:300]}")
        self.status = status


class Client:
    def __init__(self, base: str) -> None:
        self.base = base.rstrip("/")
        self.token: str | None = None

    def call(self, method: str, path: str, body: Any = None) -> Any:
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, method=method)
        request.add_header("Content-Type", "application/json")
        if self.token:
            request.add_header("Authorization", f"Bearer {self.token}")

        try:
            with urllib.request.urlopen(request) as response:
                raw = response.read()
        except urllib.error.HTTPError as error:
            raise ApiError(error.code, error.read().decode("utf-8", "replace"), method, path)
        except urllib.error.URLError as error:
            raise SystemExit(
                f"Could not reach {self.base} ({error.reason}).\n"
                "Start the stack first:  docker compose --profile full up -d"
            )

        return json.loads(raw) if raw else None

    def sign_in(self, email: str, display_name: str, password: str) -> dict:
        """Registers the account, falling back to logging in if it already exists."""
        try:
            result = self.call(
                "POST",
                "/api/auth/register",
                {"email": email, "displayName": display_name, "password": password},
            )
            print(f"  registered {email}")
        except ApiError as error:
            if error.status != 409:
                raise
            result = self.call("POST", "/api/auth/login", {"email": email, "password": password})
            print(f"  signed in as {email}")

        self.token = result["accessToken"]
        return result["user"]


# --------------------------------------------------------------------------
# Handbook content
# --------------------------------------------------------------------------

# Every endpoint below was taken from the running service's OpenAPI document
# rather than from memory, so the reference pages cannot drift from the API.

DOCUMENTS: list[dict[str, str]] = [
    {
        "slug": "welcome",
        "title": "Welcome to DevForge",
        "type": "GENERAL",
        "content": """\
# Welcome to DevForge

DevForge is where a software team keeps **what it knows** next to **what it is
doing**. Documentation lives beside the delivery board, and the two are linked, so
a task always points at the pages that explain it.

## Why another tool

A wiki tells you what the team wrote down. A board tells you what the team is
doing. Neither tells you *what breaks if this changes* — and that is the question
that actually costs teams time.

DevForge answers it with a **typed reference graph**. Documents do not merely
link to each other; every link carries a meaning:

| Relationship | Reads as |
|---|---|
| `DEPENDS_ON` | This page relies on that one |
| `IMPLEMENTS` | This realises that design |
| `DOCUMENTS` | This describes that subject |
| `SUPERSEDES` | This replaces that page |
| `RELATED` | Loose association |

Every link is visible from **both** ends. Open a page and you see what it relies
on *and* what relies on it. That second list is the one a wiki never gives you.

## Sharing what you write

A workspace can publish its documentation as a public site at
`/docs/your-handle/your-slug` — readable by anyone, no account needed, while your
boards and team list stay private. This handbook is one of those sites.

## It is open source

DevForge is free software under the MIT licence, at
<https://github.com/ensui-dev/devforge>. This instance is one deployment of it,
not a service you are renting — no paid tier, no telemetry, and self-hosting is
the intended way to use it. A fresh deployment configures itself through a
first-run setup screen: name, mark, accent, who may sign up, and the account that
will administer it.

See **DevForge is open source**, then **Self-hosting DevForge**.

## Where to go next

- New here? Read [Quickstart](#) — it takes about five minutes.
- Want the concept first? Read **The typed reference graph**.
- Wiring up a client? Start at **API reference: authentication**.

> This handbook is itself a DevForge workspace. Every page you are reading was
> created through the API, and the **Connections** panel on the right of each
> document is real. Click through it.
""",
    },
    {
        "slug": "quickstart",
        "title": "Quickstart: your first workspace",
        "type": "PROCEDURE",
        "content": """\
# Quickstart: your first workspace

About five minutes, start to finish.

## 1. Sign in

Open **{{instance.url}}** and create an account.

> Running a local stack from source instead? It is at `http://localhost:3000` —
> type the `http://`, because nothing local serves HTTPS and browsers otherwise
> try `https://` and fail with `SSL_ERROR_RX_RECORD_TOO_LONG`.

## 2. Create a workspace

Click **New workspace**. A workspace is one project or team: it owns its own
documents, boards, and members, and is invisible to everyone outside it.

The slug fills itself in from the name. It is used in links, so keep it short.

You become the workspace **owner** automatically.

## 3. Write your first document

Go to **Documents → New document**. Give it a title, pick a type, and write in
Markdown. The editor shows a live preview underneath as you type.

Start with something structural — an architecture overview or a decision record.
Later pages can then point at it.

## 4. Link two documents

Create a second document, then open it and click **Link document** in the
Connections panel. Choose a relationship and a target.

Now open the *first* document. The link is there too, phrased from its side — this
is the backlink, and you never had to create it.

## 5. Track the work

Go to **Boards → New board**. Every board starts with Backlog, In Progress,
Review, and Done; rename or reorder them however you like.

Add a task, open it, and use **Linked documents** to attach the page it depends
on. The card now shows that connection on the board itself.

## 6. Invite the team

**Team → Add member**, by email address. They need a DevForge account already.
Pick a role — see **Roles and permissions** for what each one can do.

## 7. Publish it, if you want to

**Settings → Public documentation** makes your pages readable by anyone with the
link. Boards and your team list stay private. See **Publishing your
documentation**.

## What next

- **Tutorial: linking documents together** goes deeper on the graph.
- **Use case: onboarding a new engineer** shows a realistic setup.
""",
    },
    {
        "slug": "reference-graph",
        "title": "The typed reference graph",
        "type": "ARCHITECTURE",
        "content": """\
# The typed reference graph

The core idea of DevForge. Worth understanding properly.

## The problem it solves

Documentation rots because nobody knows what a change affects. You edit the
authentication design, and three runbooks, two ADRs, and a service README quietly
become wrong. Nothing tells you, because a plain hyperlink carries no meaning and
points only one way.

## Edges have types

A reference in DevForge is a **directed, typed edge** between two documents in the
same workspace.

```
[Event ingestion pipeline] --DEPENDS_ON--> [Kafka topic conventions]
```

Read it forwards from the source. The five types:

- **`DEPENDS_ON`** — the source relies on the target. Change the target and the
  source may become wrong. This is the edge that answers "what breaks?"
- **`IMPLEMENTS`** — the source realises a design or spec described by the target.
- **`DOCUMENTS`** — the source describes the subject the target defines.
- **`SUPERSEDES`** — the source replaces the target, which is kept for history.
- **`RELATED`** — loose association, no dependency implied.

## Every edge is visible from both ends

This is the part that matters. Creating one edge gives you two views:

| Viewed from | Panel | Reads as |
|---|---|---|
| The source | *This document* | "Depends on Kafka topic conventions" |
| The target | *Referenced by* | "Required by Event ingestion pipeline" |

You create the link once, from the page that knows about the dependency. The
other page gets its backlink automatically. Nobody has to remember to update it.

## Rules the platform enforces

- A document cannot reference itself.
- Both ends must be in the same workspace — you cannot link across teams even
  with a valid document id.
- The same pair may hold several edges *of different types*, but not a duplicate
  of the same type.
- A link can only be removed from the document that declared it. Backlinks show
  no delete control, because the edge is not theirs to remove.
- Deleting a document removes every edge touching it.

## Using it well

Prefer `DEPENDS_ON` when you mean "this could invalidate that". It is the edge
you will traverse when planning a change.

Use `SUPERSEDES` instead of deleting a decision record. The history is usually
the valuable part.
""",
    },
    {
        "slug": "document-types",
        "title": "Document types",
        "type": "GENERAL",
        "content": """\
# Document types

Every document has a type. It drives filtering, the tag shown on cards, and — more
importantly — it tells a reader what kind of thing they are about to read.

| Type | Use it for |
|---|---|
| `ARCHITECTURE` | System structure, boundaries, data flow |
| `DECISION` | An ADR: context, options considered, the choice, consequences |
| `API` | An interface contract: endpoints, payloads, error semantics |
| `CODE` | How a module, class, or algorithm works |
| `PROCEDURE` | A repeatable process: release steps, onboarding, review |
| `RUNBOOK` | Operating and recovering the system under pressure |
| `TECHNOLOGY` | One library or tool, and how this project uses it |
| `TECH_STACK` | The overall set of technologies, and why |
| `GENERAL` | Anything that does not fit above |

## Choosing well

The useful test: **what would a reader want from this page at 3am during an
incident?** If the answer is "steps I can follow", it is a `RUNBOOK`. If it is
"why is it built this way", it is `ARCHITECTURE` or `DECISION`.

Do not over-think it. Types are filters, not a taxonomy to be defended — and a
document's type can be changed at any time from the editor.

## Type is not visibility

A document's type says what kind of thing it is. Whether anyone outside the team
can read it is a separate setting — the *internal* flag, covered in **Publishing
your documentation**. A `RUNBOOK` is not automatically private, and a `GENERAL`
page is not automatically public.

## Filtering

The Documents screen has a filter chip per type. Filtering is disabled while a
search is active, because search already ranks across everything.
""",
    },
    {
        "slug": "roles-and-permissions",
        "title": "Roles and permissions",
        "type": "PROCEDURE",
        "content": """\
# Roles and permissions

A workspace is reachable only by its members. Roles are **ranked**, and every
capability is cumulative — a higher role can do everything a lower one can.

| Role | Can |
|---|---|
| `VIEWER` | Read documents and boards |
| `MEMBER` | Also create and edit documents, boards, and tasks |
| `ADMIN` | Also manage the team, delete boards, edit settings, publish documentation |
| `OWNER` | Also delete the workspace |

## Two rules that protect the team

**A workspace always keeps at least one owner.** The last owner cannot leave or
demote themselves. Promote someone else first.

**Nobody may exceed their own authority.** An admin cannot grant the owner role,
and cannot remove or re-role someone ranked above them. Otherwise `ADMIN` would be
a quiet path to `OWNER`.

## Publishing is an admin action

Making documentation public needs `ADMIN`. **Seeing** whether a workspace is
published needs only `VIEWER` — anyone writing pages in a published workspace has
to know that is what they are doing.

## Why a non-member sees "not found"

Ask for a workspace you are not in and you get **404**, not **403**. A 403 would
confirm the workspace exists, which lets an outsider enumerate other teams'
projects by guessing. The API refuses to distinguish "does not exist" from "not
yours".

This is also why the app hides controls you cannot use: the interface reflects
your role, and the backend enforces it independently.

## Adding someone

**Team → Add member**, by email. They must already have a DevForge account —
there are no email invitations yet.

Anyone can leave a workspace themselves; removing *another* person requires
`ADMIN`.
""",
    },
    {
        "slug": "tutorial-writing-documents",
        "title": "Tutorial: writing and editing documents",
        "type": "PROCEDURE",
        "content": """\
# Tutorial: writing and editing documents

## Creating

**Documents → New document.** You provide:

- **Title** — what it is called.
- **URL slug** — fills in from the title; used in links. Lowercase letters,
  numbers, and hyphens only.
- **Type** — see **Document types**.

The document is created empty. You write the body in the editor.

## Writing

Click **Edit** on a document. The body is Markdown, and a **live preview** renders
underneath as you type — so you can check a table or code fence before saving.

Supported: headings, lists, tables, blockquotes, fenced code blocks with language
hints, inline code, links, images, and horizontal rules.

````markdown
## Retry policy

Consumers retry with exponential backoff:

```java
Retry.backoff(3, Duration.ofMillis(200))
```

| Attempt | Delay |
|---|---|
| 1 | 200ms |
| 2 | 400ms |
````

Nothing is saved until you press **Save changes**. **Cancel** restores the
document as it was.

## Naming this instance in a page

A page that says "open http://localhost:3000" is wrong everywhere except the
machine it was written on — and DevForge is self-hosted, so most pages are read
somewhere else. Write the address as a variable instead:

| Variable | Resolves to |
|---|---|
| `{{instance.url}}` | the address this page is being served from |
| `{{instance.name}}` | this instance's configured name |

So `Open {{instance.url}}/app` renders as **{{instance.url}}/app** here, and as
their own address for anyone reading the same page on another instance. That is
what lets this handbook be seeded onto any deployment and stay correct.

Substitution happens when the page renders, not when it is saved, so the stored
content is portable. It is deliberately not a template language — there are no
conditionals or loops, an unrecognised `{{instance.something}}` is left visible so
a typo is obvious, and braces in code samples are untouched.

## A note on safety

Document bodies are rendered as HTML, and they are written by your teammates. Every
body is sanitised before it reaches the page, so a `<script>` tag in a document
cannot run. Ordinary links and images are untouched.

## Public or internal

If the workspace is published, the editor shows whether this page is public, and a
**Keep this page internal** control holds it back. An internal page stays private
whether or not the workspace is published.

Pages you create in a published workspace are public by default, so the badge
beside the document type always tells you which you are looking at. See
**Publishing your documentation**.

## Concurrent edits

If someone else saved while you were editing, your save is rejected with a
conflict rather than silently overwriting their work. Reload and reapply.

## Deleting

**Delete** on a document removes it, every reference edge touching it, and every
task citation of it. There is a confirmation step, and it cannot be undone.
""",
    },
    {
        "slug": "tutorial-linking",
        "title": "Tutorial: linking documents together",
        "type": "PROCEDURE",
        "content": """\
# Tutorial: linking documents together

This is the feature worth learning properly. See **The typed reference graph** for
the concept; this page is the mechanics.

## Creating a link

1. Open the document that *has* the dependency — the one that would become wrong.
2. In the **Connections** panel, click **Link document**.
3. Choose the **relationship**. Read it forwards: *this document → the one you
   pick*.
4. Filter and select the target. Documents you have already linked in this
   direction are not offered again.
5. **Add link**.

## Reading the panel

Connections splits into two groups:

- **This document** — edges you declared. Each has a remove control.
- **Referenced by** — backlinks. Phrased from your side, with no remove control,
  because the edge belongs to the other page.

So `A DEPENDS_ON B` appears on A as *"Depends on B"* and on B as *"Required by A"*.

## Worked example

Say you are changing how tokens are issued.

1. Open **Auth flow**, link `DEPENDS_ON` → **Token conventions**.
2. Open **Login runbook**, link `DEPENDS_ON` → **Auth flow**.
3. Now open **Token conventions**. Its *Referenced by* panel shows **Auth flow**.
   Open that, and its backlinks show **Login runbook**.

You have traced the blast radius of the change in two clicks, without searching.

## Removing a link

Use the **×** beside an edge under *This document*. If you want to remove a
backlink, open the document that declared it — the platform will not let you
delete it from the far end.
""",
    },
    {
        "slug": "tutorial-boards",
        "title": "Tutorial: running a delivery board",
        "type": "PROCEDURE",
        "content": """\
# Tutorial: running a delivery board

## Creating a board

**Boards → New board.** Every board is seeded with **Backlog**, **In Progress**,
**Review**, and **Done**. Rename, reorder, or replace them.

## Columns

Hover a column header for its controls:

- **‹ ›** move the column left or right. Siblings renumber automatically.
- **⋯** open settings: rename, set a work-in-progress limit, or delete.

A board must keep at least one column, so the last one cannot be deleted.

### Work-in-progress limits

Set a limit and the column header shows `3/5`. It turns amber at the limit and red
above it.

The limit is checked when a task **arrives** — creating or moving one in. It is
not checked when you lower the limit, so an over-full column can always be
drained rather than being frozen.

## Tasks

**New task**, or **+ Add task** at the foot of a column. A task carries a title,
description, priority, an assignee, and any number of linked documents.

Only `HIGH` and `CRITICAL` priorities show a badge — `MEDIUM` is the default and
badging it would add noise to every card.

An assignee must be a member of the workspace. Assigning work to someone who
cannot open the project is rejected.

## Moving tasks

**Drag a card** between or within columns. A teal insertion line shows exactly
where it will land. The move applies instantly and reconciles with the server; if
the server refuses — a WIP limit, say — the card snaps back.

Prefer the keyboard? Open the task and change its **Column** field. Same result.

Positions always stay contiguous. Delete a task from the middle of a column and
the rest close the gap.

## Editing versus moving

Editing a task never changes where it sits. Column and position move through a
separate action, so a routine edit cannot silently reorder your board.
""",
    },
    {
        "slug": "tutorial-linking-tasks",
        "title": "Tutorial: connecting tasks to documentation",
        "type": "PROCEDURE",
        "content": """\
# Tutorial: connecting tasks to documentation

The other half of the interconnection idea: work should point at knowledge instead
of restating it.

## The problem

A task description that repeats the spec goes stale the moment the spec changes.
Worse, the person doing the work reads the stale copy.

## Linking

Open any task. Under **Linked documents**, pick a document and click **Link**.

A task may cite as many documents as it needs — a design, an ADR, a runbook.
A document may be cited by as many tasks as need it.

Only documents in the same workspace are offered, and the platform rejects a
citation of a document from anywhere else.

## What you get

- The **card on the board** shows the linked documents as short traces, so you can
  see at a glance which work carries context and which does not.
- The task dialog links straight through to each document.
- Deleting a document cleans up its citations automatically, so a task never
  points at something that no longer exists.

## In practice

Write the task title as the *outcome*, and let the linked document carry the
*detail*:

> **Title:** Partition the orders topic
> **Linked:** Kafka topic conventions (`TECHNOLOGY`), Event ingestion pipeline
> (`ARCHITECTURE`)

Now when conventions change, the reference graph shows every task that cited them.
""",
    },
    {
        "slug": "tutorial-search",
        "title": "Tutorial: searching your knowledge base",
        "type": "PROCEDURE",
        "content": """\
# Tutorial: searching your knowledge base

## Using it

The search box sits at the top of the **Documents** screen. It searches **titles
and full body text** across the whole workspace, ranked by relevance, with title
matches weighted above body matches.

Results are paginated and update as you type. Clearing the box returns you to the
filtered list.

## What you can type

Search accepts ordinary web-search syntax:

| You type | It means |
|---|---|
| `retry policy` | Both words, anywhere |
| `"retry policy"` | That exact phrase |
| `retry -kafka` | Retry, excluding Kafka |
| `retry or backoff` | Either term |

Unbalanced quotes and stray operators are handled rather than rejected, so a
half-typed query never produces an error.

## How it stays correct

The search index is maintained by the database itself as part of each write, so it
can never drift from the content. Edit a document and the next search reflects it
immediately — there is no rebuild step and no background job to fall behind.

Searches are also scoped to the workspace you are in, so results never leak
between teams.

## Tips

- Search finds *body* text, so a term buried in a code block or table is
  findable.
- Filtering by type is disabled while searching, because ranking already spans
  every type.
- Cannot find a page? Try a word you would have written in the body rather than
  the title.
""",
    },
    {
        "slug": "publishing",
        "title": "Publishing your documentation",
        "type": "PROCEDURE",
        "content": """\
# Publishing your documentation

Any workspace can serve its documentation as a public site inside DevForge. The
handbook you are reading is one — it is an ordinary workspace that happens to be
published.

## Publishing

**Settings → Public documentation → Publish documentation.** You need the `ADMIN`
role or higher.

Your pages then become readable at:

```
/docs/your-handle/your-workspace-slug
```

by anyone with the link, with no account.

The first segment is **your handle** — a URL-safe name your account gets when you
register, derived from your email address. It namespaces everything you own, so two
teams can each publish a workspace called `nokia` without clashing. Your handle is
shown beside your name on the workspaces screen. Only documentation is exposed — **boards,
tasks, and your team list stay private**.

## What gets published

Everything except pages marked internal. That is the useful default: a twenty-page
handbook should not need twenty separate decisions.

The consequence worth understanding: **a page you write later is public as soon as
you save it.** So DevForge never lets that state hide.

| Where | What you see |
|---|---|
| Navigation rail, every screen | A *Documentation is public* banner |
| Beside each document's type | A **Public** or **Internal** badge |
| The document editor | *Keep this page internal*, with the effect spelled out |
| Settings | Counts of public and held-back pages |
| The publish confirmation | The exact number of pages about to go live |

## Holding a page back

Open the page, click **Edit**, and tick **Keep this page internal**. It stays
private whether or not the workspace is published, and it disappears from the
public contents immediately.

Use it for scratch notes, drafts, anything with credentials in it, and anything
written for the team rather than for readers.

> An internal page cannot leak through the reference graph either. If a public page
> links to an internal one, that edge is simply absent from the public site — the
> internal page's title is never shown.

## Going private again

**Settings → Make private.** The public URL stops resolving at once. Nothing is
deleted, and republishing later keeps the original publication date.

## The directory

`/docs` lists every workspace on the instance that has published. Your own
documentation appears there as soon as you publish it, with its page count.

## Rules worth knowing

- Publishing needs at least one page that is not internal. Publishing an empty site
  reads as broken, so DevForge refuses it and says why.
- Publishing is `ADMIN`; **seeing** whether a workspace is published is `VIEWER`,
  because anyone writing pages here needs to know they are writing in public.
- Your workspace slug is the second segment of the public URL. Renaming it in
  Settings changes the address, so avoid it once people have the link.
- Slugs only have to be unique **among your own** workspaces. Another team taking
  the obvious name no longer blocks you.
- `/docs/your-handle` lists everything you have published, like a profile page.
"""
    },
    {
        "slug": "api-public-docs",
        "title": "API reference: public documentation",
        "type": "API",
        "content": """\
# API reference: public documentation

The published-documentation endpoints. These are the only ones that need **no
token** — that is the point of them.

## Reading published documentation

| Method | Path | Auth |
|---|---|---|
| `GET` | `/api/public/docs` | none |
| `GET` | `/api/public/docs/{handle}` | none |
| `GET` | `/api/public/docs/{handle}/{workspaceSlug}` | none |
| `GET` | `/api/public/docs/{handle}/{workspaceSlug}/{documentSlug}` | none |
| `GET` | `/api/public/instance` | none |

### `GET /api/public/docs`

Every workspace that has published, with how many pages each offers:

```json
[{
  "name": "DevForge Handbook", "slug": "devforge-handbook",
  "description": "How DevForge works, written in DevForge.",
  "pageCount": 23, "publishedAt": "2026-07-25T15:12:00Z"
}]
```

### `GET /api/public/docs/{handle}`

Everything one owner has published, like a profile page:

```json
{
  "handle": "acme",
  "workspaces": [{ "name": "Platform", "slug": "platform", "ownerHandle": "acme",
                   "publicPath": "/docs/acme/platform", "pageCount": 12,
                   "description": "…", "publishedAt": "…" }],
  "movedTo": null
}
```

`movedTo` carries a path when the segment is **not** a handle but does resolve to
exactly one published workspace — a link written before slugs were namespaced.
Clients redirect to it rather than showing a 404. When two owners share the slug it
stays null, because there is no single right answer.

### `GET /api/public/docs/{handle}/{workspaceSlug}`

One workspace's table of contents. Pages marked internal are absent.

```json
{
  "name": "DevForge Handbook", "slug": "devforge-handbook",
  "ownerHandle": "handbook", "description": "…",
  "entries": [{ "id": "…", "title": "Welcome to DevForge",
                "slug": "welcome", "documentType": "GENERAL" }]
}
```

### `GET /api/public/docs/{handle}/{workspaceSlug}/{documentSlug}`

One page, with its body and the reference edges between **public** pages:

```json
{
  "id": "…", "title": "The typed reference graph", "slug": "reference-graph",
  "content": "# The typed reference graph\\n…",
  "documentType": "ARCHITECTURE",
  "references": [{ "referenceType": "DEPENDS_ON", "outgoing": false,
                   "relatedDocumentSlug": "tutorial-linking",
                   "relatedDocumentTitle": "Tutorial: linking documents together" }],
  "updatedAt": "…"
}
```

An unpublished workspace, or a page marked internal, returns **404** — the same
answer as something that does not exist, so neither can be probed.

### `GET /api/public/instance`

How this instance brands itself, whether it has been set up, and whether it
accepts registrations — everything a client needs before anyone signs in:

```json
{
  "configured": true,
  "name": "DevForge",
  "tagline": "Documentation and delivery, connected.",
  "logoMark": "⌁",
  "accentColor": null,
  "registrationMode": "OPEN",
  "allowedEmailDomains": [],
  "publicDocsEnabled": true,
  "handbookPath": "handbook/devforge-handbook"
}
```

Operational settings such as the instance's public address are deliberately not
included; those need an instance administrator and `GET /api/instance`.

## Controlling publication

These need a token, and `ADMIN` to change anything.

| Method | Path | Role |
|---|---|---|
| `GET` | `/api/workspaces/{workspaceId}/publication` | `VIEWER` |
| `PUT` | `/api/workspaces/{workspaceId}/publication` | `ADMIN` |

`GET` describes the state, including what publishing would expose:

```json
{
  "published": true,
  "publishedAt": "2026-07-25T15:12:00Z",
  "publicPath": "/docs/handbook/devforge-handbook",
  "publicPages": 23,
  "internalPages": 0
}
```

`PUT` takes `{ "published": true }` or `{ "published": false }` and returns the same
shape. Publishing is idempotent: doing it twice keeps the original date.

Rejections: publishing with no public pages → `400`; changing it without `ADMIN` →
`403`; a workspace you are not in → `404`.

## Marking a page internal

Through the ordinary document endpoints — `internal` is a field on the document,
not a separate call:

```json
{ "title": "Scratch notes", "slug": "scratch-notes", "content": "…",
  "documentType": "GENERAL", "internal": true }
```

It defaults to `false`, so an existing client keeps working unchanged.
"""
    },
    {
        "slug": "api-authentication",
        "title": "API reference: authentication",
        "type": "API",
        "content": """\
# API reference: authentication

Base URL **`{{instance.url}}/api`** — this instance, as you are reading it. Running
the backend directly from source instead? That is `http://localhost:8080/api`.

All endpoints accept and return JSON.

Every route requires `Authorization: Bearer <token>` **except** register and
login. Interactive docs: `/swagger-ui.html`.

## `POST /api/auth/register`

Creates an account and returns a usable token. → `201`

```json
{ "email": "ada@example.com", "displayName": "Ada Lovelace", "password": "password123" }
```

Password must be at least 8 characters. Email is stored case-folded, so
`Ada@Example.com` and `ada@example.com` are the same account. Duplicate → `409`.

## `POST /api/auth/login`

Exchanges credentials for a token. → `200`

```json
{ "email": "ada@example.com", "password": "password123" }
```

Wrong password and unknown address return the **same** `401` message, so the API
cannot be used to discover which addresses are registered.

## Response shape

Both endpoints return:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresAt": "2026-07-26T02:00:00Z",
  "user": { "id": "…", "email": "ada@example.com",
            "displayName": "Ada Lovelace", "handle": "ada" }
}
```

`handle` is derived from the address at registration and suffixed if taken
(`ada`, `ada-2`, …). It namespaces the workspaces this account owns.

Tokens are valid for 12 hours. There is no refresh endpoint yet — when a token
expires, sign in again.

## `GET /api/auth/me`

Describes the authenticated user. → `200`

## `GET /api/users?q=`

Finds users to add to a workspace. Matches display name or email, minimum two
characters, capped at 20 results. → `200`

## History and activity

| Method | Path | Purpose |
|---|---|---|
| `GET` | `/api/workspaces/{id}/documents/{docId}/revisions` | A document's revisions, newest first. Bodies omitted |
| `GET` | `/api/workspaces/{id}/documents/{docId}/revisions/{n}` | One revision in full |
| `POST` | `/api/workspaces/{id}/documents/{docId}/revisions/{n}/restore` | Restore it as a **new** revision |
| `GET` | `/api/workspaces/{id}/activity` | What changed in this workspace. `?action=` filters |
| `GET` | `/api/instance/activity` | Everything on the instance (instance admin) |

Reading either needs `VIEWER`; restoring needs `MEMBER`.

## Using a token

```bash
TOKEN=$(curl -s -X POST {{instance.url}}/api/auth/login \\
  -H 'Content-Type: application/json' \\
  -d '{"email":"ada@example.com","password":"password123"}' \\
  | python3 -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')

curl -s {{instance.url}}/api/workspaces -H "Authorization: Bearer $TOKEN"
```
""",
    },
    {
        "slug": "api-workspaces",
        "title": "API reference: workspaces and members",
        "type": "API",
        "content": """\
# API reference: workspaces and members

## Workspaces

| Method | Path | Role |
|---|---|---|
| `GET` | `/api/workspaces` | — |
| `POST` | `/api/workspaces` | — |
| `GET` | `/api/workspaces/{workspaceId}` | `VIEWER` |
| `PUT` | `/api/workspaces/{workspaceId}` | `ADMIN` |
| `DELETE` | `/api/workspaces/{workspaceId}` | `OWNER` |

`GET /api/workspaces` returns only workspaces you belong to, each with your role:

```json
[{
  "id": "…", "name": "Platform", "description": "Core services",
  "slug": "platform", "callerRole": "OWNER",
  "createdAt": "…", "updatedAt": "…"
}]
```

Create and update take:

```json
{ "name": "Platform", "description": "Core services", "slug": "platform" }
```

Slug must match `^[a-z0-9]+(?:-[a-z0-9]+)*$` and is unique **per owner**, not across
the instance — so another team's `platform` is no obstacle. A slug you already used
→ `409`. The creator is enrolled as `OWNER` automatically, and their handle
namespaces the workspace's public address.

`DELETE` removes every document, board, and task in the workspace. → `204`

## Publication

| Method | Path | Role |
|---|---|---|
| `GET` | `/api/workspaces/{workspaceId}/publication` | `VIEWER` |
| `PUT` | `/api/workspaces/{workspaceId}/publication` | `ADMIN` |

Described in full under **API reference: public documentation**.

## Members

| Method | Path | Role |
|---|---|---|
| `GET` | `/api/workspaces/{workspaceId}/members` | `VIEWER` |
| `POST` | `/api/workspaces/{workspaceId}/members` | `ADMIN` |
| `PUT` | `/api/workspaces/{workspaceId}/members/{memberUserId}` | `ADMIN` |
| `DELETE` | `/api/workspaces/{workspaceId}/members/{memberUserId}` | `ADMIN`, or yourself |

Add by email — the account must already exist, or `404`:

```json
{ "email": "grace@example.com", "role": "MEMBER" }
```

Change a role with `{ "role": "ADMIN" }`.

Rejections worth knowing:

- Granting a role above your own → `403`
- Acting on a member ranked above you → `403`
- Removing or demoting the last owner → `400`
- Adding someone already a member → `409`
""",
    },
    {
        "slug": "api-documents",
        "title": "API reference: documents and references",
        "type": "API",
        "content": """\
# API reference: documents and references

## Documents

| Method | Path | Role |
|---|---|---|
| `GET` | `/api/workspaces/{id}/documents` | `VIEWER` |
| `GET` | `/api/workspaces/{id}/documents/search` | `VIEWER` |
| `GET` | `/api/workspaces/{id}/documents/{documentId}` | `VIEWER` |
| `GET` | `/api/workspaces/{id}/documents/by-slug/{slug}` | `VIEWER` |
| `POST` | `/api/workspaces/{id}/documents` | `MEMBER` |
| `PUT` | `/api/workspaces/{id}/documents/{documentId}` | `MEMBER` |
| `DELETE` | `/api/workspaces/{id}/documents/{documentId}` | `MEMBER` |

### Listing

`GET …/documents?documentType=RUNBOOK&page=0&size=25`

`size` is capped at 100. Listings return an **excerpt**, not the full body, so
payload size does not grow with document length:

```json
{
  "content": [{ "id": "…", "title": "…", "slug": "…", "excerpt": "First 200 characters…",
                "documentType": "RUNBOOK", "createdAt": "…", "updatedAt": "…" }],
  "page": 0, "size": 25, "totalElements": 42, "totalPages": 2, "last": false
}
```

Fetch a single document by id or slug to get its `content`.

### Search

`GET …/documents/search?q=retry+policy&page=0&size=25`

Same page shape. Ranked, with titles weighted above bodies. Accepts phrases in
quotes, `-` to exclude, and `or`.

### Creating and updating

```json
{ "title": "Retry policy", "slug": "retry-policy",
  "content": "# Retry policy\\n\\n…", "documentType": "PROCEDURE" }
```

`internal` is optional and defaults to `false`. Set it to `true` to hold the page
back from the public site — see **API reference: public documentation**.

Slug is unique **per workspace**, so two teams may both have `overview`.
Duplicate within a workspace → `409`.

`PUT` replaces all four fields. A concurrent edit loses with `409`.

## References

| Method | Path | Role |
|---|---|---|
| `GET` | `/api/workspaces/{id}/documents/{documentId}/references` | `VIEWER` |
| `POST` | `/api/workspaces/{id}/documents/{documentId}/references` | `MEMBER` |
| `DELETE` | `/api/workspaces/{id}/documents/{documentId}/references/{referenceId}` | `MEMBER` |

Create with:

```json
{ "targetDocumentId": "…", "referenceType": "DEPENDS_ON" }
```

`GET` returns outgoing edges **and** backlinks together, each flagged:

```json
[{
  "id": "…", "referenceType": "DEPENDS_ON", "outgoing": true,
  "relatedDocumentId": "…", "relatedDocumentTitle": "Kafka topic conventions",
  "relatedDocumentSlug": "kafka-conventions", "relatedDocumentType": "TECHNOLOGY",
  "createdAt": "…"
}]
```

`outgoing: false` means this document is the *target* — a backlink.

Rejections: self-reference → `400`; duplicate of the same type → `409`; target in
another workspace → `404`; deleting from the target side → `404`.
""",
    },
    {
        "slug": "api-boards",
        "title": "API reference: boards and tasks",
        "type": "API",
        "content": """\
# API reference: boards and tasks

## Boards and columns

| Method | Path | Role |
|---|---|---|
| `GET` | `/api/workspaces/{id}/boards` | `VIEWER` |
| `GET` | `/api/workspaces/{id}/boards/{boardId}` | `VIEWER` |
| `POST` | `/api/workspaces/{id}/boards` | `MEMBER` |
| `PUT` | `/api/workspaces/{id}/boards/{boardId}` | `MEMBER` |
| `DELETE` | `/api/workspaces/{id}/boards/{boardId}` | `ADMIN` |
| `POST` | `/api/workspaces/{id}/boards/{boardId}/columns` | `MEMBER` |
| `PUT` | `…/columns/{columnId}` | `MEMBER` |
| `PATCH` | `…/columns/{columnId}/position` | `MEMBER` |
| `DELETE` | `…/columns/{columnId}` | `MEMBER` |

`GET /boards` returns summaries with `columnCount` and `taskCount` rather than
nested content. `GET /boards/{id}` returns the full board: columns in order, each
with its tasks in order.

Creating a board takes `{ "name": "Delivery" }` and seeds four default columns.

Column create/update takes `{ "name": "In Progress", "wipLimit": 3 }`. Omit or
null `wipLimit` for unlimited; it must be at least 1.

**Every column mutation returns the whole board**, because reordering renumbers
siblings and a partial response would leave the client with stale positions.

A board must keep one column: deleting the last → `400`.

## Tasks

| Method | Path | Role |
|---|---|---|
| `POST` | `…/boards/{boardId}/tasks` | `MEMBER` |
| `PUT` | `…/tasks/{taskId}` | `MEMBER` |
| `PATCH` | `…/tasks/{taskId}/position` | `MEMBER` |
| `DELETE` | `…/tasks/{taskId}` | `MEMBER` |
| `POST` | `…/tasks/{taskId}/documents` | `MEMBER` |
| `DELETE` | `…/tasks/{taskId}/documents/{documentId}` | `MEMBER` |

Create:

```json
{ "title": "Partition the orders topic", "description": "…",
  "columnId": "…", "priority": "HIGH",
  "assigneeId": "…", "linkedDocumentIds": ["…"] }
```

Only `title` and `columnId` are required. Priority defaults to `MEDIUM`. The task
is appended to the end of the column.

`PUT` edits title, description, priority, and assignee **only** — it never moves
the task.

### Moving

```
PATCH …/tasks/{taskId}/position
{ "columnId": "…", "position": 0 }
```

`position` is a zero-based index; values past the end place the task last rather
than erroring, so a drag past the bottom behaves as intended. Both affected
columns are renumbered so positions stay contiguous.

Rejections: assignee not a member → `400`; column on another board → `404`;
destination at its WIP limit → `400`.

### Document citations

`POST …/tasks/{taskId}/documents` with `{ "documentId": "…" }`. Duplicate → `409`;
document from another workspace → `404`. Both endpoints return the updated task.
""",
    },
    {
        "slug": "api-errors",
        "title": "API reference: errors and status codes",
        "type": "API",
        "content": """\
# API reference: errors and status codes

Every failure returns the same shape:

```json
{
  "timestamp": "2026-07-25T14:59:38Z",
  "status": 409,
  "error": "Conflict",
  "message": "Workspace slug already exists: platform",
  "path": "/api/workspaces"
}
```

Validation failures add a `fieldErrors` map, so a client can put each message
beside the input that caused it:

```json
{
  "status": 400, "error": "Bad Request", "message": "Validation failed",
  "path": "/api/workspaces",
  "fieldErrors": { "slug": "must be lowercase alphanumeric with hyphens" }
}
```

## What each code means

| Code | Meaning |
|---|---|
| `400` | The request broke a rule — validation, or a business constraint such as the last owner leaving |
| `401` | Missing, malformed, or expired token; or wrong credentials at login |
| `403` | You are a member but your role is too low for this action |
| `404` | It does not exist — **or** it is not yours (see below) |
| `409` | Conflicts with existing data: duplicate slug, duplicate link, or a concurrent edit |
| `500` | A defect on the server. It is logged with a stack trace |

## Two deliberate choices

**404 hides other teams.** Anything inside a workspace you are not a member of
returns `404`, never `403`. A 403 would confirm the resource exists.

**500 means a server bug, not your mistake.** Unmapped exceptions are logged and
returned as `500`. They are deliberately *not* folded into `400`, because doing
that makes every internal failure look like a client error and hides real defects.

## Concurrency

Every record carries a version. If someone saved while you were editing, your
write returns `409` with a message telling you to reload — rather than silently
overwriting their work.
""",
    },
    {
        "slug": "running-locally",
        "title": "Running DevForge locally",
        "type": "RUNBOOK",
        "content": """\
# Running DevForge locally

## Everything in containers

```bash
cp .env.example .env
openssl rand -base64 48        # paste as DEVFORGE_JWT_SECRET
docker compose --profile full up --build
```

App at **http://localhost:3000**. Compose reads `.env` automatically.

## For development

Database only, with the backend and frontend on your machine so both hot-reload:

```bash
docker compose up -d postgres

cd backend && ./mvnw spring-boot:run      # :8080
cd frontend && npm install && npm run dev # :5173
```

The Vite dev server proxies `/api` to the backend, so there is no cross-origin
traffic and CORS stays off.

> `./mvnw` does **not** read `.env` — only Compose does. Export the values first:
> `export $(grep -v '^#' .env | xargs)`

## Useful endpoints

| URL | What |
|---|---|
| `http://localhost:8080/swagger-ui.html` | Interactive API docs |
| `http://localhost:8080/v3/api-docs` | OpenAPI JSON |
| `http://localhost:8080/actuator/health` | Health, with liveness and readiness |

## Tests

```bash
cd backend && ./mvnw test     # needs Docker for Testcontainers
cd frontend && npm test && npm run lint && npm run build
```

## Configuration

| Variable | Default | Notes |
|---|---|---|
| `DEVFORGE_JWT_SECRET` | dev placeholder | **Change it.** Minimum 32 characters; the app refuses to start below that |
| `DEVFORGE_DB_URL` | `jdbc:postgresql://localhost:5432/devforge` | |
| `DEVFORGE_DB_USERNAME` / `_PASSWORD` | `devforge` | |
| `DEVFORGE_DB_PORT` | `5432` | Host port for the Compose database |
| `DEVFORGE_CORS_ORIGINS` | empty | Only needed if the client is on another origin |
| `DEVFORGE_HANDBOOK_SLUG` | `devforge-handbook` | Which published workspace `/docs` opens by default. Blank lists them all. Does **not** publish anything — that is per workspace |
| `PORT` | `8080` | |

Rotating the JWT secret invalidates every issued token, so everyone signs in
again.
""",
    },
    {
        "slug": "troubleshooting",
        "title": "Troubleshooting",
        "type": "RUNBOOK",
        "content": """\
# Troubleshooting

## "Secure Connection Failed" / `SSL_ERROR_RX_RECORD_TOO_LONG`

Your browser tried HTTPS. Nothing here serves TLS.

Type **`http://localhost:3000`** with the prefix. If the browser has cached the
upgrade, use a private window, or clear the `localhost` entry under
`about:networking#hsts` (Firefox) or `chrome://net-internals/#hsts` (Chrome).

## Backend will not start: "password authentication failed"

Something else is already using port 5432 — often another project's PostgreSQL.
Check with `docker ps`, then either stop it or move DevForge:

```bash
DEVFORGE_DB_PORT=5433 docker compose up -d postgres
export DEVFORGE_DB_URL=jdbc:postgresql://localhost:5433/devforge
```

## Backend will not start: JWT secret rejected

```
Binding validation errors on devforge.jwt
  secret: must be at least 32 characters
```

Working as intended. Generate a real one: `openssl rand -base64 48`.

## Everything returns 401

The token expired — they last 12 hours and there is no refresh yet. Sign in again.
The app clears an expired session and redirects automatically.

## A save returns 409

Someone edited the same record while you had it open. Reload and reapply your
change; the platform refuses to overwrite their work silently.

## Cannot add a member

They need a DevForge account with that exact address first. There are no email
invitations yet, so ask them to register.

## Cannot move a task into a column

The column is at its work-in-progress limit. Either finish something already in
it, or raise the limit in the column's **⋯** settings.

## Tests fail with a Docker error

Backend integration tests start PostgreSQL through Testcontainers. Docker must be
running and your user must be able to reach the socket.
""",
    },
    {
        "slug": "tech-stack",
        "title": "Tech stack",
        "type": "TECH_STACK",
        "content": """\
# Tech stack

| Layer | Technology | Why |
|---|---|---|
| Frontend | React 19 + TypeScript, Vite | Fast builds, strict typing across the API boundary |
| Data fetching | TanStack Query | Cache invalidation as a first-class concern |
| Routing | React Router | Nested routes match the workspace shell |
| Backend | Java 21, Spring Boot 4 | Records, pattern matching, mature ecosystem |
| Persistence | Spring Data JPA + Hibernate | Repositories with hand-written queries where it matters |
| Migrations | Flyway | The schema is versioned and reviewable |
| Database | PostgreSQL 16 | Generated columns and full-text search built in |
| Auth | Spring Security, HMAC-signed JWT | Stateless; no session store to operate |
| Docs | springdoc-openapi | The spec is generated from the code |

## Testing

| Tool | Covers |
|---|---|
| JUnit 5, Mockito, AssertJ | Services with collaborators stubbed |
| Testcontainers | Integration tests against real PostgreSQL 16 |
| ArchUnit | Module boundaries, enforced at build time |
| Vitest + Testing Library | Components and hooks |

## Notable choices

**PostgreSQL does the search.** A generated `tsvector` column with a GIN index
means the index is part of each write and can never drift from the content.

**Modules talk through contracts.** Each feature module publishes interfaces and
records; nothing else about it is visible. ArchUnit fails the build if a module
reaches into another's internals.

**References are held by id, not by association.** A document stores a
`workspaceId`, not a `Workspace` object. Foreign keys still enforce integrity, but
the object graph does not span modules.
""",
    },
    {
        "slug": "use-case-onboarding",
        "title": "Use case: onboarding a new engineer",
        "type": "GENERAL",
        "content": """\
# Use case: onboarding a new engineer

The usual failure: a new starter is handed a wiki with 200 pages and no idea which
40 matter, then asks the same six questions in Slack.

## The setup

Create a workspace per team. Add three documents first:

1. **Start here** (`GENERAL`) — what the team owns, in a page.
2. **Local environment** (`RUNBOOK`) — getting the system running.
3. **Architecture overview** (`ARCHITECTURE`) — the shape of the thing.

Link them: *Local environment* `DEPENDS_ON` *Architecture overview*, so a starter
who hits something confusing in setup can see what explains it.

## The onboarding board

Create a board named **Onboarding**. Rename the columns to **Week 1**,
**Week 2**, **Ongoing**.

Add a task per milestone, and **link each task to the document that explains it**:

| Task | Linked document |
|---|---|
| Get the stack running locally | Local environment |
| Read the architecture overview | Architecture overview |
| Ship a one-line change to production | Release procedure |
| Take a turn on support rota | Incident runbook |

Now the board *is* the reading list, in order, with the material attached.

## Why this works

The starter never asks "what should I read?" — the board answers it. And when a
procedure changes, you update one document; every task pointing at it is current
immediately, and the reference graph shows you which those are.

## Keep it honest

Give the new starter `MEMBER` and ask them to fix what they find wrong on their
first pass. They are the only person who will ever read the onboarding docs with
fresh eyes.
""",
    },
    {
        "slug": "use-case-decisions",
        "title": "Use case: a decision record trail",
        "type": "DECISION",
        "content": """\
# Use case: a decision record trail

Architecture decision records are only useful if you can find the one that
explains the code in front of you — and know whether it is still current.

## Write each decision as a document

Use the `DECISION` type and a consistent shape:

```markdown
## Context
What forced a choice.

## Options
What was considered, and the trade-offs.

## Decision
What was chosen.

## Consequences
What this makes easy, and what it makes hard.
```

## Link decisions to what they affect

- The decision `DOCUMENTS` the component it governs.
- The implementation notes `IMPLEMENTS` the decision.

Now open any architecture page and its backlinks show which decisions produced it.

## Superseding, not deleting

When a decision is reversed, **do not delete the old one**. Write the new record
and link it `SUPERSEDES` → the old one.

The old page keeps a backlink reading *"Superseded by …"*, so anyone who arrives
at it from an old commit message or Slack thread immediately sees it is stale and
where to go instead.

That trail — *why we did this, why we stopped* — is usually more valuable than the
current state alone.

## Connect it to delivery

When a decision creates work, link the tasks to the decision record. Six months
later, "why is this like this?" is answerable from the board.
""",
    },
    {
        "slug": "use-case-runbooks",
        "title": "Use case: runbooks that stay current",
        "type": "RUNBOOK",
        "content": """\
# Use case: runbooks that stay current

A runbook is read at 3am by someone stressed. It is also the document most likely
to be quietly wrong, because it describes a system that keeps changing.

## Write for the reader you will actually have

- Numbered steps, in order, with the command to run.
- State what "recovered" looks like, so they know when to stop.
- Put the diagnosis *before* the fix.
- No prose they have to parse under pressure.

## Link the runbook to the system it operates

Give every runbook a `DEPENDS_ON` edge to the architecture and technology pages
it assumes:

```
[Consumer lag runbook] --DEPENDS_ON--> [Kafka topic conventions]
                       --DEPENDS_ON--> [Event ingestion pipeline]
```

## The payoff

When someone changes topic partitioning, they open **Kafka topic conventions**,
and the *Referenced by* panel shows the consumer lag runbook. They know, before
merging, that a runbook needs updating.

Without the graph, that runbook stays wrong until the next incident finds it.

## Close the loop after an incident

Add a task to the board for each runbook correction the incident exposed, and link
the task to the runbook. The fix then gets tracked like any other work rather than
living in someone's memory.
""",
    },
    {
        "slug": "history-and-attribution",
        "title": "History and attribution",
        "type": "PROCEDURE",
        "content": """\
# History and attribution

Every change to a document is kept, and every change anywhere is attributed. Two
separate things, so they are worth separating.

## Document history

Open a document and press **History**. You get every revision it has had, newest
first, with who wrote each one and what has changed since.

Revision 1 is the document as created; every edit appends another. Picking one shows
a line-by-line diff against the live version — removals in red, additions in green,
each marked with `−` or `+` so the diff still reads without colour.

### Restoring never loses anything

Restoring revision 3 does not delete revisions 4 and 5. It writes a **new** revision
carrying revision 3's content, and records that it came from 3. So:

- the restore is itself visible in history
- you can undo a restore by restoring what you had before
- nothing anyone wrote is ever removed by someone else's restore

That is why the confirmation does not warn you that the action cannot be undone. It
can.

### Saving without changing nothing

Saving a document you have not edited adds no revision and no log entry. Git refuses
an empty commit for the same reason: an entry that records nothing makes the ones
that do harder to find. Renaming counts as a change even when the body is identical.

## The activity log

Every workspace has an **Activity** tab: what changed, who changed it, and when.
Visible to every member including viewers — it reveals nothing they cannot already
read, and knowing who last touched a page is part of reading it honestly.

Entries are never edited or deleted. Filter by kind if a busy workspace buries what
you are looking for.

An entry records only the fields that actually moved:

```
Ada Lovelace edited Event ingestion pipeline
  title: Design → Event ingestion pipeline
  length: 1841 → 2213
```

The actor and the target are stored as the names they had **at the time**. Rename an
account tomorrow and yesterday's entries still read correctly; delete it and they
still say who it was. A log that rewrote itself whenever the present changed would
not be a log.

Some entries have no actor at all. First-run setup happens before any account
exists, so it is attributed to *Setup* rather than to whoever was created by it.

## Instance-wide activity

Instance administrators get **Instance settings → Activity log**: everything on the
deployment, including events belonging to no workspace — setup, account creation, and
administration grants.

A workspace's entries survive its deletion, which is deliberate: with a cascade,
deleting a workspace would delete the evidence that it was deleted. They stop being
reachable through the workspace's own tab, because that workspace no longer exists,
and remain visible here.

## What is not kept

- **Nothing prunes either table.** Both stay small — a log entry is a few hundred
  bytes, and a document body is stored once per distinct content, so a restore or a
  reverted edit adds none — but there is no retention policy yet.
- **Deleting a document deletes its revisions.** The audit entry recording the
  deletion is kept.
- **Reads are not logged.** This records changes, not access.
""",
    },
    {
        "slug": "sync-from-git",
        "title": "Writing documentation in git",
        "type": "PROCEDURE",
        "content": """\
# Writing documentation in git

Keep your documentation as markdown in a repository, push, and let this workspace
follow it. Review documentation in a pull request like any other change.

## Setting it up

**Workspace → Settings → Sync from git.** Give it a repository URL, a branch, and the
folder your markdown lives in. Press **Sync now** — you should not have to push a
commit to find out whether the settings are right.

Then wire the webhook so every push syncs:

1. Copy the **webhook URL** from the settings panel.
2. Press **Generate secret** and copy the value. It is shown once — it is stored
   encrypted, so nothing can show it again.
3. In your git host, add a webhook with that URL, content type
   `application/json`, and that secret.

Deliveries with no signature, or the wrong one, are refused. Until a secret exists
every delivery is refused, because a delivery that cannot be verified is not
accepted.

## How files become documents

The filename is the slug, so `docs/runbooks/consumer-lag.md` becomes
`consumer-lag`. Directories are not folded into the slug: a URL should not bake in a
layout that will be reorganised later. Two files with the same name in different
folders collide, which the sync reports rather than letting one quietly overwrite the
other.

Front matter is optional:

```markdown
---
title: Event ingestion pipeline
type: ARCHITECTURE
internal: false
---

# Event ingestion pipeline
```

Every key can be left out. Without a `title` the first `#` heading is used, and
failing that the filename — most documentation already opens with its own title, and
repeating it in front matter is duplication that goes stale. Without a `type`, the
default from the settings panel applies.

Only `.md` and `.markdown` files are read. Anything else in the repository is ignored.

## When a file is deleted

You choose, because the safe answer depends on the repository:

| Setting | What happens |
|---|---|
| **Mark it internal** (default) | Withdrawn from published documentation, history kept |
| **Delete it** | Removed, along with its revisions |
| **Leave it alone** | For a repository holding only part of the documentation |

The default is the recoverable one deliberately. Deleting is what git means by a
removed file, but a mistyped folder makes *every* file look removed at once.

Which is why: **a sync that finds no documentation at all refuses to withdraw
anything.** A repository that is genuinely empty is indistinguishable from a wrong
folder, so the destructive reading is rejected and the message tells you to check the
path.

## Editing here as well as in git

You can do both. The repository wins on sync, and nothing is lost — the version
someone typed here stays in the document's **History** as a revision, so you can see
exactly what happened and restore it.

Every revision applied from a repository is marked as synced rather than edited, so
history answers "why did this page change?" and not merely "when".

## Private repositories

Add an access token with read access. It is encrypted before being stored, so a
database dump does not hand over a live credential, and it is never returned by the
API — the settings screen only tells you whether one is stored.

Rotating `DEVFORGE_JWT_SECRET` makes stored tokens unreadable. The sync then reports
that plainly and asks you to enter the token again.

## What this does not do yet

- **References are not read from front matter.** Typed links between documents are
  made in the interface. Authoring the reference graph in the repository is the
  obvious next step and is not built.
- **Nothing is pushed back.** Edits made here do not become commits.
- **There is no history import.** A sync applies the current state of a ref; it does
  not replay a repository's commits into revisions.
""",
    },
    {
        "slug": "open-source",
        "title": "DevForge is open source",
        "type": "GENERAL",
        "content": """\
# DevForge is open source

DevForge is free software under the **MIT licence**. The source is at
<https://github.com/ensui-dev/devforge>, and what runs on this instance is what is
in that repository.

## What that means here

You are reading this on one deployment. It is not a hosted service anybody is
renting to you — it is a copy of an open-source project, running on someone's
server. You can run your own, and nothing is withheld from it.

| | |
|---|---|
| **Licence** | MIT — use it commercially, fork it, relicense your changes |
| **No paid tier** | Nothing is gated behind a licence key or an edition |
| **No telemetry** | No analytics, no phone-home, no CDN. The application makes no outbound request of any kind |
| **Your data** | One PostgreSQL database you control; `pg_dump` is a complete backup |

The no-telemetry claim is checkable, which is the point of shipping the source:
the frontend loads no external asset and declares no analytics dependency, and the
backend has no HTTP client at all.

## Run your own copy

```bash
git clone https://github.com/ensui-dev/devforge.git
cd devforge
cp .env.example .env
openssl rand -base64 48        # paste as DEVFORGE_JWT_SECRET
docker compose --profile full up -d
```

First boot opens a setup wizard. See **Self-hosting DevForge** for what it asks
and why, and **First-run setup** for the one step that cannot be repeated.

## Contributing

Issues and pull requests are welcome at
<https://github.com/ensui-dev/devforge/issues>.

Two things are worth knowing before you send a change. The module boundaries are
enforced by ArchUnit, so importing another module's internals fails the build
rather than review. And tests here are expected to fail when the thing they cover
breaks — before trusting a new one, break the code and watch it go red. Several
tests carry a comment naming the specific bug they were written against; those
comments are the point.

`CONTRIBUTING.md` in the repository has the rest.

## Reporting a security problem

Privately, through GitHub security advisories rather than a public issue. The
repository's `SECURITY.md` also lists what an operator has to get right — changing
the token signing key, finishing setup promptly, and keeping more than one
administrator.
""",
    },
    {
        "slug": "self-hosting",
        "title": "Self-hosting DevForge",
        "type": "PROCEDURE",
        "content": """\
# Self-hosting DevForge

DevForge is open source under the MIT licence and built to be run by whoever uses
it. Nothing that distinguishes one deployment from another is baked into the
build — the same image serves a public instance, a company's private one, and a
single-person notebook. The difference is a row in the database.

## Bring it up

```bash
git clone https://github.com/ensui-dev/devforge.git
cd devforge
cp .env.example .env
openssl rand -base64 48        # paste as DEVFORGE_JWT_SECRET in .env
docker compose --profile full up --build -d
```

Then open `http://localhost:3000`. Type the `http://` — nothing here serves
HTTPS, and browsers upgrade a bare `localhost:3000` to `https://`.

Once it is behind a domain and a reverse proxy, that address is whatever you put
in front of it. Pages in this handbook that need to name this instance write
`{{instance.url}}`, which resolves to **{{instance.url}}** as you are reading
it — so the same handbook is correct on every deployment.

## What must be in the environment

Only two things, and both are infrastructure rather than product settings:

| Variable | Why it has to be here |
|---|---|
| `DEVFORGE_DB_URL` and credentials | The application needs a database before it can read anything, including its own settings |
| `DEVFORGE_JWT_SECRET` | Signs access tokens. Minimum 32 characters; the application refuses to start below that |

The committed default secret is a public string in the repository. Anyone who has
read the source could mint valid tokens for a deployment still using it. Generate
your own before the instance is reachable by anyone else.

Everything a person would recognise as a setting — the name, the mark, who may
sign up, whether documentation can be published — is chosen in setup and stored in
the database. It survives a redeploy, and changing it needs no shell access.

## First run

An instance nobody has claimed redirects every route to `/setup` and shows
nothing else. See **First-run setup** for what the four steps decide.

## Upgrading

Migrations run at startup, so an upgrade is a redeploy. The instance settings row
survives it. Back up with `pg_dump` — a logo image is stored in that row rather
than in object storage, so a database dump is a complete backup.
""",
    },
    {
        "slug": "first-run-setup",
        "title": "First-run setup",
        "type": "PROCEDURE",
        "content": """\
# First-run setup

The screen a fresh instance shows before it will show anything else.

## It runs once

Setup refuses forever once it completes. That is a security property, not a
convenience: a deployment briefly reachable before its operator finishes must not
be claimable by whoever gets there second, and the endpoint must never be usable
to mint an administrator on a running instance.

There is no recovery path in the product if someone else completes it first.
Bring the instance up on a closed port, or finish setup immediately.

## The four steps

| Step | What it decides |
|---|---|
| **Identity** | Name, tagline, and public address |
| **Appearance** | A mark — a character or an uploaded image — and an accent colour |
| **Access** | Whether people may sign themselves up, and from which email domains |
| **Operator** | The first administrator |

Everything except the operator account can be changed afterwards, from
**Instance settings**.

## Identity

The name appears in the header, on the sign-in screen, and in the browser tab.
The public address is where the instance is reachable; it is used to build
absolute links and is never shown to visitors.

## Appearance

The accent replaces exactly one design token. Links, primary actions, and active
state follow it; spacing, contrast, and the signal colours used for priority and
warnings do not move. A rebrand is a colour, not a redesign.

A logo image is stored as a data URI in the settings row, so self-hosting needs no
file server. Keep it under 64KB.

## Access

Choose the registration mode. `RESTRICTED` needs at least one email domain — an
instance that restricts registration to nothing accepts nobody, and the server
refuses those settings outright.

## Operator

The account created here administers the instance. It owns the settings, and on a
closed instance it is the only way to add anyone else. The password is confirmed
before it is used, because a typo here would lock you out of your own deployment
with no way back in.

Once setup finishes you are signed in as that account, and `/setup` is gone.
""",
    },
    {
        "slug": "instance-settings",
        "title": "Instance settings",
        "type": "PROCEDURE",
        "content": """\
# Instance settings

Everything the operator of a self-hosted instance controls, at
**Instance settings** in the workspace header. Only an instance administrator sees
the link, and the server refuses everyone else.

## Registration modes

| Mode | Who can create an account | Suits |
|---|---|---|
| `OPEN` | Anyone who can reach the deployment | A public instance |
| `RESTRICTED` | Anyone with an email at a listed domain | A company instance on a public address |
| `CLOSED` | Nobody — the operator creates every account | A private or single-team instance |

Domains match exactly; subdomains are not included. `acme.com` does not admit
`mail.acme.com`.

## Adding people to a closed instance

**Operators → Add operator** does two things: it appoints someone already on the
instance, and it creates an account outright. Creation bypasses the registration
mode entirely — that is what makes `CLOSED` a usable setting rather than a locked
door. Clear the administrator box to create an ordinary account.

## Keep a second operator

An instance whose only administrator loses their password cannot be reconfigured
by anything inside the product. The settings screen refuses to remove the last
administrator and says why. Appoint a second one early.

An operator can step down once a second one exists.

## Public documentation

The master switch. Switching it off refuses new publications **and** takes every
already-published site offline at once. Nothing is deleted: the workspaces stay
published in the database, and they reappear the moment it is switched back on.

The handbook path names which published workspace `/docs` opens by default, as
`handle/slug`. Leave it blank and `/docs` lists every published workspace on the
instance instead.

## What visitors can see

Branding is served from `GET /api/public/instance`, which needs no session — the
sign-in screen has to know the instance's name before anyone has signed in. That
response deliberately omits operational settings such as the public address, so
adding a setting later cannot accidentally publish it.
""",
    },
]

# Typed edges, as (source slug, relationship, target slug). Read forwards:
# "source RELATIONSHIP target".
REFERENCES: list[tuple[str, str, str]] = [
    ("quickstart", "DEPENDS_ON", "welcome"),
    ("quickstart", "RELATED", "roles-and-permissions"),
    ("tutorial-writing-documents", "DEPENDS_ON", "document-types"),
    ("tutorial-writing-documents", "IMPLEMENTS", "quickstart"),
    ("tutorial-linking", "DEPENDS_ON", "reference-graph"),
    ("tutorial-linking", "IMPLEMENTS", "quickstart"),
    ("tutorial-boards", "IMPLEMENTS", "quickstart"),
    ("tutorial-linking-tasks", "DEPENDS_ON", "tutorial-boards"),
    ("tutorial-linking-tasks", "DEPENDS_ON", "reference-graph"),
    ("tutorial-search", "DEPENDS_ON", "document-types"),
    ("reference-graph", "DOCUMENTS", "welcome"),
    ("api-authentication", "DOCUMENTS", "roles-and-permissions"),
    ("api-workspaces", "DEPENDS_ON", "api-authentication"),
    ("api-workspaces", "DOCUMENTS", "roles-and-permissions"),
    ("api-documents", "DEPENDS_ON", "api-authentication"),
    ("api-documents", "DOCUMENTS", "reference-graph"),
    ("api-boards", "DEPENDS_ON", "api-authentication"),
    ("api-errors", "RELATED", "api-authentication"),
    ("api-documents", "RELATED", "api-errors"),
    ("publishing", "DEPENDS_ON", "roles-and-permissions"),
    ("publishing", "RELATED", "tutorial-writing-documents"),
    ("publishing", "IMPLEMENTS", "quickstart"),
    ("api-public-docs", "DOCUMENTS", "publishing"),
    ("api-public-docs", "RELATED", "api-documents"),
    ("api-workspaces", "RELATED", "publishing"),
    ("running-locally", "DEPENDS_ON", "tech-stack"),
    ("troubleshooting", "DEPENDS_ON", "running-locally"),
    ("troubleshooting", "RELATED", "api-errors"),
    ("use-case-onboarding", "DEPENDS_ON", "tutorial-boards"),
    ("use-case-onboarding", "DEPENDS_ON", "tutorial-linking-tasks"),
    ("use-case-decisions", "DEPENDS_ON", "reference-graph"),
    ("use-case-decisions", "DEPENDS_ON", "document-types"),
    ("use-case-runbooks", "DEPENDS_ON", "reference-graph"),
    ("use-case-runbooks", "RELATED", "troubleshooting"),
    ("open-source", "DOCUMENTS", "welcome"),
    ("sync-from-git", "DEPENDS_ON", "tutorial-writing-documents"),
    ("sync-from-git", "RELATED", "history-and-attribution"),
    ("sync-from-git", "DOCUMENTS", "document-types"),
    ("history-and-attribution", "DEPENDS_ON", "tutorial-writing-documents"),
    ("history-and-attribution", "DOCUMENTS", "roles-and-permissions"),
    ("history-and-attribution", "RELATED", "api-documents"),
    ("self-hosting", "DEPENDS_ON", "open-source"),
    ("self-hosting", "DEPENDS_ON", "tech-stack"),
    ("self-hosting", "RELATED", "running-locally"),
    ("first-run-setup", "DEPENDS_ON", "self-hosting"),
    ("instance-settings", "DEPENDS_ON", "first-run-setup"),
    ("instance-settings", "DOCUMENTS", "roles-and-permissions"),
    ("instance-settings", "RELATED", "publishing"),
    ("publishing", "DEPENDS_ON", "instance-settings"),
]

# Board columns, then tasks as (column, title, priority, description, [doc slugs]).
BOARD_NAME = "Learn DevForge"
COLUMNS = ["Read first", "Try it", "Reference", "Done"]

TASKS: list[tuple[str, str, str, str, list[str]]] = [
    ("Read first", "Understand what DevForge is for", "HIGH",
     "Ten minutes. Read the welcome page, then the reference graph page — that is the idea "
     "everything else is built on.",
     ["welcome", "reference-graph"]),
    ("Read first", "Get the stack running", "CRITICAL",
     "Bring up the containers and sign in. If anything fights you, the troubleshooting page "
     "covers the usual causes.",
     ["running-locally", "troubleshooting"]),
    ("Read first", "Know what you are running", "MEDIUM",
     "DevForge is MIT-licensed open source with no telemetry and no paid tier. Worth two "
     "minutes before you decide to depend on it.",
     ["open-source"]),
    ("Read first", "Claim the instance you are running", "CRITICAL",
     "Setup runs once and refuses forever afterwards. Finish it before the deployment is "
     "reachable by anyone else, and appoint a second operator so one lost password cannot "
     "leave the instance unconfigurable.",
     ["self-hosting", "first-run-setup", "instance-settings"]),
    ("Try it", "Point a workspace at a git repository", "MEDIUM",
     "Put a couple of markdown files in a repo, configure the sync, and press Sync now. Then "
     "delete one upstream and watch it be withdrawn rather than destroyed.",
     ["sync-from-git"]),
    ("Try it", "Edit a page, then read its history", "MEDIUM",
     "Make an edit, open History, and read the diff. Then restore the earlier revision and "
     "notice that history grew rather than rewound.",
     ["history-and-attribution"]),
    ("Read first", "Learn the roles before inviting anyone", "MEDIUM",
     "Roles are ranked and cumulative. Know what you are granting.",
     ["roles-and-permissions"]),
    ("Try it", "Write your first document", "HIGH",
     "Create an architecture overview for something you are actually working on. Real content "
     "beats a placeholder.",
     ["quickstart", "tutorial-writing-documents", "document-types"]),
    ("Try it", "Link two documents and find the backlink", "HIGH",
     "Create the edge from the page that has the dependency, then open the other page and see "
     "it from the far side. This is the moment the tool clicks.",
     ["tutorial-linking", "reference-graph"]),
    ("Try it", "Run a board for one week of real work", "MEDIUM",
     "Set a WIP limit on your in-progress column and see whether you respect it.",
     ["tutorial-boards"]),
    ("Try it", "Attach documents to a task", "MEDIUM",
     "Take a task whose description repeats a spec, and replace the repetition with a link.",
     ["tutorial-linking-tasks"]),
    ("Try it", "Search for something buried in a body", "LOW",
     "Search reads full text, not just titles. Try a phrase in quotes.",
     ["tutorial-search"]),
    ("Try it", "Publish your team's documentation", "MEDIUM",
     "Publishing exposes every page that is not marked internal, so read what the badges and "
     "banners are telling you before you flip it on.",
     ["publishing", "roles-and-permissions"]),
    ("Reference", "Wire up an API client", "MEDIUM",
     "Start with authentication, then the resource you need. Interactive docs are at "
     "/swagger-ui.html.",
     ["api-authentication", "api-workspaces", "api-documents", "api-boards"]),
    ("Reference", "Handle errors properly in your client", "LOW",
     "One error shape everywhere, with per-field messages on validation failures.",
     ["api-errors"]),
    ("Reference", "Set up onboarding for your team", "LOW",
     "A worked example of using a board as a reading list.",
     ["use-case-onboarding"]),
    ("Reference", "Start a decision record trail", "LOW",
     "Supersede decisions rather than deleting them; the trail is the valuable part.",
     ["use-case-decisions", "use-case-runbooks"]),
]


# --------------------------------------------------------------------------
# Seeding
# --------------------------------------------------------------------------


def resolve_workspace(client: Client, slug: str, name: str) -> tuple[str, bool]:
    """
    Finds the handbook workspace, creating it if this instance has none.

    @return the workspace id, and whether it already existed
    """
    for workspace in client.call("GET", "/api/workspaces"):
        if workspace["slug"] == slug:
            return workspace["id"], True

    created = client.call("POST", "/api/workspaces", {
        "name": name,
        "description": "How DevForge works, written in DevForge.",
        "slug": slug,
    })
    return created["id"], False


def sync_documents(client: Client, workspace_id: str) -> dict[str, str]:
    """
    Brings the workspace's documents in line with DOCUMENTS.

    Existing pages are updated in place rather than replaced, so their ids survive
    and every reference and task citation pointing at them stays intact.

    @return document ids by slug
    """
    existing = {
        entry["slug"]: entry["id"]
        for entry in client.call(
            "GET", f"/api/workspaces/{workspace_id}/documents?size=100")["content"]
    }

    ids: dict[str, str] = {}
    created = updated = 0
    for document in DOCUMENTS:
        payload = {
            "title": document["title"],
            "slug": document["slug"],
            "content": document["content"],
            "documentType": document["type"],
            "internal": False,
        }
        if document["slug"] in existing:
            document_id = existing[document["slug"]]
            client.call(
                "PUT", f"/api/workspaces/{workspace_id}/documents/{document_id}", payload)
            updated += 1
        else:
            document_id = client.call(
                "POST", f"/api/workspaces/{workspace_id}/documents", payload)["id"]
            created += 1
        ids[document["slug"]] = document_id

    print(f"  {created} created, {updated} updated")
    return ids


def sync_references(client: Client, workspace_id: str, ids: dict[str, str]) -> None:
    """Adds any missing edges. Existing ones are left alone."""
    added = 0
    for source, relationship, target in REFERENCES:
        if source not in ids or target not in ids:
            continue
        try:
            client.call(
                "POST",
                f"/api/workspaces/{workspace_id}/documents/{ids[source]}/references",
                {"targetDocumentId": ids[target], "referenceType": relationship},
            )
            added += 1
        except ApiError as error:
            # 409 means this edge already exists, which is the desired end state.
            if error.status != 409:
                raise
    print(f"  {added} added, {len(REFERENCES) - added} already present")


def sync_board(client: Client, workspace_id: str, ids: dict[str, str]) -> None:
    """Creates the learning board if absent, then adds any missing tasks."""
    boards = client.call("GET", f"/api/workspaces/{workspace_id}/boards")
    match = next((b for b in boards if b["name"] == BOARD_NAME), None)

    if match is None:
        board = client.call(
            "POST", f"/api/workspaces/{workspace_id}/boards", {"name": BOARD_NAME})
        board_id = board["id"]
        for index, name in enumerate(COLUMNS):
            existing = board["columns"]
            if index < len(existing):
                board = client.call(
                    "PUT",
                    f"/api/workspaces/{workspace_id}/boards/{board_id}/columns/"
                    f"{existing[index]['id']}",
                    {"name": name, "wipLimit": None})
            else:
                board = client.call(
                    "POST", f"/api/workspaces/{workspace_id}/boards/{board_id}/columns",
                    {"name": name})
    else:
        board = client.call("GET", f"/api/workspaces/{workspace_id}/boards/{match['id']}")
        board_id = board["id"]

    columns = {column["name"]: column["id"] for column in board["columns"]}
    present = {task["title"] for column in board["columns"] for task in column["tasks"]}

    added = 0
    for column, title, priority, description, slugs in TASKS:
        if title in present or column not in columns:
            continue
        client.call("POST", f"/api/workspaces/{workspace_id}/boards/{board_id}/tasks", {
            "title": title,
            "description": description,
            "columnId": columns[column],
            "priority": priority,
            "linkedDocumentIds": [ids[s] for s in slugs if s in ids],
        })
        added += 1
    print(f"  {added} tasks added, {len(TASKS) - added} already present")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Create or update the DevForge Handbook workspace.")
    parser.add_argument("--base", default="http://localhost:8080",
                        help="API base URL (default: %(default)s)")
    parser.add_argument("--email", default="handbook@devforge.local")
    parser.add_argument("--password", default="handbook123")
    parser.add_argument("--name", default="Handbook Author")
    parser.add_argument("--workspace", default="DevForge Handbook")
    parser.add_argument("--slug", default="devforge-handbook")
    parser.add_argument("--publish", action="store_true",
                        help="publish the handbook once it is in place")
    args = parser.parse_args()

    client = Client(args.base)

    print(f"Syncing the DevForge Handbook into {client.base}")
    print("\n1. Account")
    client.sign_in(args.email, args.name, args.password)

    print("\n2. Workspace")
    workspace_id, existed = resolve_workspace(client, args.slug, args.workspace)
    print(f"  {'found' if existed else 'created'} /{args.slug}")

    print(f"\n3. Documents ({len(DOCUMENTS)} defined)")
    ids = sync_documents(client, workspace_id)

    print(f"\n4. Typed references ({len(REFERENCES)} defined)")
    sync_references(client, workspace_id, ids)

    print(f"\n5. Board ({len(TASKS)} tasks defined)")
    sync_board(client, workspace_id, ids)

    public_path = f"/docs/{args.slug}"
    if args.publish:
        print("\n6. Publication")
        state = client.call("PUT", f"/api/workspaces/{workspace_id}/publication",
                            {"published": True})
        # Namespaced by the owner's handle, so read it back rather than guessing.
        public_path = state["publicPath"]
        print(f"  public at {public_path} "
              f"({state['publicPages']} pages, {state['internalPages']} held back)")

    app = client.base.replace(":8080", ":3000")
    print(f"""
Done.

  In the app    {app}/workspaces/{workspace_id}
  Public docs   {app}{public_path}
  Sign in as    {args.email} / {args.password}

Re-run this any time: existing pages are updated in place, so their ids survive
and every reference and task citation pointing at them stays intact.
""")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except ApiError as error:
        print(f"\nFailed: {error}", file=sys.stderr)
        sys.exit(1)
