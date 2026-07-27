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
import pathlib
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

# The pages live in the devforge-docs submodule, not in this file. They used to
# be string literals here, which meant the only way to read the handbook was to
# run this script against a live instance — and once the same pages existed in a
# repository, a second copy here would have quietly overwritten edits made there.
#
# So this reads them, in the same front-matter grammar the sync module parses.
# One source of truth, and it is the one a pull request can review.

HANDBOOK = pathlib.Path(__file__).resolve().parent.parent / "docs" / "handbook"

RELATIONSHIPS = ("related", "depends_on", "implements", "documents", "supersedes")


def read_front_matter(text: str) -> tuple[dict[str, str], str]:
    """
    Splits a page into its front matter and its body.

    A deliberately small grammar — a `---` fence, then `key: value` lines — the
    same subset the server accepts, so a file that works here works there.
    """
    if not text.startswith("---\n"):
        return {}, text

    close = text.find("\n---", 3)
    if close < 0:
        raise SystemExit("front matter is not closed by a --- line")

    front = {}
    for line in text[4:close].split("\n"):
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        key, _, value = line.partition(":")
        front[key.strip().lower()] = value.strip()

    body = text[close + 4:]
    return front, body.lstrip("\n")


def load_handbook() -> tuple[list[dict[str, str]], list[tuple[str, str, str]]]:
    """@return the pages, and the typed edges they declare"""
    if not HANDBOOK.is_dir():
        raise SystemExit(
            f"No handbook at {HANDBOOK}.\n"
            "The pages are a submodule:  git submodule update --init"
        )

    documents: list[dict[str, str]] = []
    references: list[tuple[str, str, str]] = []

    for path in sorted(HANDBOOK.rglob("*.md")):
        front, body = read_front_matter(path.read_text())
        # The path below the folder is the slug, folders included — the same rule
        # the server applies, so a page keeps its address wherever it is read.
        slug = path.relative_to(HANDBOOK).with_suffix("").as_posix()

        documents.append({
            "slug": slug,
            "title": front.get("title", slug),
            "type": front.get("type", "GENERAL"),
            "content": body.strip(),
        })

        for relationship in RELATIONSHIPS:
            for target in front.get(relationship, "").split(","):
                if target.strip():
                    references.append((slug, relationship.upper(), target.strip()))

    return documents, references


DOCUMENTS, REFERENCES = load_handbook()

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
    ("Try it", "Clone the workspace and push a change", "MEDIUM",
     "Issue a git access token, clone the workspace, edit a page in your own editor and push it. "
     "Then edit a page here and pull — the edit comes back as a commit with your name on it.",
     ["push-and-clone"]),
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
