package com.devforge.sync.api;

import com.devforge.identity.application.GitCredentialService;
import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Git hosting, exercised with the real git client over real HTTP.
 *
 * <p>MockMvc cannot test this. The smart-HTTP protocol streams packfiles, negotiates
 * over chunked encoding, and turns on status codes MockMvc never really produces, so
 * the application runs on a port and {@code git} is invoked against it.
 *
 * <p>Deliberately the real client rather than JGit's. JGit's client will not
 * volunteer Basic credentials over plain HTTP, so it fails here for a reason that has
 * nothing to do with the server — and {@code git} is the client people will actually
 * use. It also caught a defect JGit's client could not have:
 * {@code DefaultReceivePackFactory} refuses a push when {@code getRemoteUser()} is
 * null, which no amount of request attributes fixes.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GitHostingIntegrationTest extends AbstractIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private GitCredentialService gitCredentials;

    @TempDir
    Path scratch;

    private String tokenFor(TestUser user) {
        return gitCredentials.issue(user.id(), "test", null).secret();
    }

    /**
     * A remote URL carrying the token.
     *
     * <p>The username is ignored — the token identifies its owner — so anything may
     * appear before the colon, which is how every git host behaves.
     */
    private String remote(TestUser owner, String workspaceSlug, String token) {
        return "http://devforge:" + token + "@127.0.0.1:" + port
                + "/git/" + owner.handle() + "/" + workspaceSlug + ".git";
    }

    private String anonymousRemote(TestUser owner, String workspaceSlug) {
        return "http://127.0.0.1:" + port + "/git/" + owner.handle() + "/" + workspaceSlug + ".git";
    }

    private record GitResult(int exitCode, String output) {
        boolean succeeded() {
            return exitCode == 0;
        }
    }

    /**
     * Runs git with its credential helper disabled.
     *
     * <p>Without that, a failing test would either hang on a keyring prompt or
     * quietly borrow the developer's own credentials and pass for the wrong reason.
     */
    private static GitResult git(Path directory, String... arguments) throws Exception {
        String[] command = new String[arguments.length + 3];
        command[0] = "git";
        command[1] = "-c";
        command[2] = "credential.helper=";
        System.arraycopy(arguments, 0, command, 3, arguments.length);

        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true);
        builder.environment().put("GIT_TERMINAL_PROMPT", "0");
        builder.environment().put("GIT_ASKPASS", "true");

        Process process = builder.start();
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes());
        return new GitResult(process.waitFor(), output);
    }

    private static void gitOk(Path directory, String... arguments) throws Exception {
        GitResult result = git(directory, arguments);
        assertThat(result.succeeded())
                .as("git %s failed: %s", String.join(" ", arguments), result.output())
                .isTrue();
    }

    /** A working copy with the given files, committed on `main`. */
    private Path workingCopy(String name, String... pathsAndContents) throws Exception {
        Path directory = Files.createDirectories(scratch.resolve(name));
        gitOk(directory, "init", "-q", "-b", "main", ".");
        write(directory, pathsAndContents);
        gitOk(directory, "add", ".");
        commit(directory, "documentation");
        return directory;
    }

    private static void write(Path directory, String... pathsAndContents) throws Exception {
        for (int i = 0; i < pathsAndContents.length; i += 2) {
            Path file = directory.resolve(pathsAndContents[i]);
            Files.createDirectories(file.getParent());
            Files.writeString(file, pathsAndContents[i + 1]);
        }
    }

    private static void commit(Path directory, String message) throws Exception {
        gitOk(directory, "-c", "user.email=ada@example.test", "-c", "user.name=Ada",
                "commit", "-q", "-m", message);
    }

    // ------------------------------------------------------------------- pushing

    @Test
    void pushingCreatesTheDocuments() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        Path work = workingCopy("push",
                "docs/design.md", "---\ntitle: Event ingestion\ntype: ARCHITECTURE\n---\nthe body",
                "docs/runbooks/consumer-lag.md", "# Consumer lag\n\nsteps");

        gitOk(work, "push", "-q", remote(owner, "platform", tokenFor(owner)), "main");

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/docs/design", ws), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Event ingestion"))
                .andExpect(jsonPath("$.documentType").value("ARCHITECTURE"))
                .andExpect(jsonPath("$.content").value("the body"));

        // Folders are mirrored, from the repository root when none is configured.
        mockMvc.perform(authed(
                        get("/api/workspaces/{w}/documents/by-slug/docs/runbooks/consumer-lag", ws), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Consumer lag"));
    }

    /** The outcome comes back on the push, which is the one moment anyone is looking. */
    @Test
    void reportsWhatItAppliedOnThePushItself() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        createWorkspace(owner, "Platform", "platform");
        Path work = workingCopy("report", "docs/a.md", "# A", "docs/b.md", "# B");

        GitResult result = git(work, "push", remote(owner, "platform", tokenFor(owner)), "main");

        assertThat(result.succeeded()).isTrue();
        assertThat(result.output()).contains("DevForge: 2 created");
    }

    @Test
    void aSecondPushUpdatesRatherThanDuplicating() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        String url = remote(owner, "platform", tokenFor(owner));
        Path work = workingCopy("second", "docs/a.md", "# A\n\nfirst");

        gitOk(work, "push", "-q", url, "main");

        write(work, "docs/a.md", "# A\n\nsecond");
        gitOk(work, "add", ".");
        commit(work, "edit");
        gitOk(work, "push", "-q", url, "main");

        String document = mockMvc.perform(
                        authed(get("/api/workspaces/{w}/documents/by-slug/docs/a", ws), owner))
                .andExpect(jsonPath("$.content").value("# A\n\nsecond"))
                .andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(document).get("id").asText());

        // History says the change arrived from git, not from the editor.
        mockMvc.perform(authed(
                        get("/api/workspaces/{w}/documents/{d}/revisions", ws, documentId), owner))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].reason").value("SYNCED"));
    }

    /** Typed links survive the trip, resolved after every document exists. */
    @Test
    void pushingAppliesTheReferenceGraph() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        Path work = workingCopy("graph",
                "docs/design.md", "---\ndepends_on: docs/runbooks/consumer-lag\n---\nbody",
                "docs/runbooks/consumer-lag.md", "# Consumer lag");

        gitOk(work, "push", "-q", remote(owner, "platform", tokenFor(owner)), "main");

        String document = mockMvc.perform(
                        authed(get("/api/workspaces/{w}/documents/by-slug/docs/design", ws), owner))
                .andReturn().getResponse().getContentAsString();
        UUID design = UUID.fromString(objectMapper.readTree(document).get("id").asText());

        mockMvc.perform(authed(
                        get("/api/workspaces/{w}/documents/{d}/references", ws, design), owner))
                .andExpect(jsonPath("$[0].referenceType").value("DEPENDS_ON"))
                .andExpect(jsonPath("$[0].relatedDocumentSlug").value("docs/runbooks/consumer-lag"));
    }

    @Test
    void recordsThePushInTheActivityLog() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        Path work = workingCopy("activity", "docs/a.md", "# A");

        gitOk(work, "push", "-q", remote(owner, "platform", tokenFor(owner)), "main");

        mockMvc.perform(authed(get("/api/workspaces/{w}/activity?action=WORKSPACE_SYNCED", ws), owner))
                .andExpect(jsonPath("$.content[0].detail.trigger").value("push"))
                .andExpect(jsonPath("$.content[0].actorLabel").value(
                        org.hamcrest.Matchers.containsString("owner@acme.test")));
    }

    // ------------------------------------------------------------------- cloning

    @Test
    void clonesWhatWasPushed() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        createWorkspace(owner, "Platform", "platform");
        String token = tokenFor(owner);
        Path work = workingCopy("origin", "docs/a.md", "# A");
        gitOk(work, "push", "-q", remote(owner, "platform", token), "main");

        Path clone = Files.createDirectories(scratch.resolve("clone"));
        gitOk(clone, "clone", "-q", remote(owner, "platform", token), ".");

        assertThat(clone.resolve("docs/a.md")).exists();
        assertThat(Files.readString(clone.resolve("docs/a.md"))).isEqualTo("# A");
    }

    // -------------------------------------------------------------- permissions

    /** The repository is not a second public surface; documentation is published at /docs. */
    @Test
    void refusesAnAnonymousClone() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        createWorkspace(owner, "Platform", "platform");

        Path clone = Files.createDirectories(scratch.resolve("anon"));
        assertThat(git(clone, "clone", "-q", anonymousRemote(owner, "platform"), ".").succeeded())
                .isFalse();
    }

    @Test
    void refusesAnInvalidToken() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        createWorkspace(owner, "Platform", "platform");

        Path clone = Files.createDirectories(scratch.resolve("bad-token"));
        assertThat(git(clone, "clone", "-q",
                remote(owner, "platform", "dfg_not-a-real-token"), ".").succeeded())
                .isFalse();
    }

    @Test
    void refusesAnExpiredToken() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        createWorkspace(owner, "Platform", "platform");
        String expired = gitCredentials
                .issue(owner.id(), "old", java.time.Instant.now().minusSeconds(60)).secret();

        Path clone = Files.createDirectories(scratch.resolve("expired"));
        assertThat(git(clone, "clone", "-q", remote(owner, "platform", expired), ".").succeeded())
                .isFalse();
    }

    /**
     * A non-member cannot tell a workspace apart from one that does not exist, which
     * is the same rule the API follows.
     */
    @Test
    void hidesAWorkspaceFromSomeoneWhoIsNotAMember() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        TestUser outsider = registerUser("outsider@acme.test", "Outsider");
        createWorkspace(owner, "Platform", "platform");
        String token = tokenFor(outsider);

        Path present = Files.createDirectories(scratch.resolve("outsider-present"));
        Path absent = Files.createDirectories(scratch.resolve("outsider-absent"));

        assertThat(git(present, "clone", "-q", remote(owner, "platform", token), ".").succeeded())
                .isFalse();
        assertThat(git(absent, "clone", "-q", remote(owner, "nope", token), ".").succeeded())
                .isFalse();
    }

    /** A viewer may clone; changing documentation needs MEMBER. */
    @Test
    void letsAViewerCloneButNotPush() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        TestUser viewer = registerUser("viewer@acme.test", "Viewer");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        addMember(owner, ws, viewer.email(), "VIEWER");

        Path work = workingCopy("owner-copy", "docs/a.md", "# A");
        gitOk(work, "push", "-q", remote(owner, "platform", tokenFor(owner)), "main");

        String viewerUrl = remote(owner, "platform", tokenFor(viewer));
        Path clone = Files.createDirectories(scratch.resolve("viewer"));
        gitOk(clone, "clone", "-q", viewerUrl, ".");

        write(clone, "docs/a.md", "# Changed by a viewer");
        gitOk(clone, "add", ".");
        commit(clone, "viewer edit");

        assertThat(git(clone, "push", "-q", viewerUrl, "main").succeeded()).isFalse();

        // And nothing was applied.
        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/docs/a", ws), owner))
                .andExpect(jsonPath("$.content").value("# A"));
    }

    // ----------------------------------------------------------------- refusals

    /**
     * The same refusal the fetching path makes: a push that empties the documentation
     * folder looks exactly like one that deleted everything, and withdrawing a
     * workspace's every page is not a guess worth making.
     */
    @Test
    void refusesAPushThatWouldWithdrawEveryPage() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        String url = remote(owner, "platform", tokenFor(owner));
        Path work = workingCopy("emptying", "docs/a.md", "# A", "docs/b.md", "# B");
        gitOk(work, "push", "-q", url, "main");

        Files.delete(work.resolve("docs/a.md"));
        Files.delete(work.resolve("docs/b.md"));
        write(work, "src/code.txt", "not documentation");
        gitOk(work, "add", "-A");
        commit(work, "move the documentation away");

        GitResult result = git(work, "push", url, "main");

        // The push is accepted — the objects are valid git — but the import refuses,
        // and says so where the person pushing will see it.
        assertThat(result.output()).contains("refusing to withdraw");

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/docs/a", ws), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internal").value(false));
    }

    /** Only the default branch is documentation; a feature branch is work in progress. */
    @Test
    void ignoresAPushToAnotherBranch() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        String url = remote(owner, "platform", tokenFor(owner));
        Path work = workingCopy("branches", "docs/a.md", "# A");
        gitOk(work, "push", "-q", url, "main");

        gitOk(work, "checkout", "-q", "-b", "draft");
        write(work, "docs/a.md", "# A rewritten on a branch");
        gitOk(work, "add", ".");
        commit(work, "draft");
        gitOk(work, "push", "-q", url, "draft");

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/docs/a", ws), owner))
                .andExpect(jsonPath("$.content").value("# A"));
    }

    // -------------------------------------------------------------- the clone URL

    /**
     * The path half only. The browser supplies the origin, which is the only way to
     * be right about it behind a reverse proxy or a tunnel.
     */
    @Test
    void tellsAMemberWhereToCloneFrom() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(get("/api/workspaces/{w}/git", ws), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.clonePath").value("/git/" + owner.handle() + "/platform.git"))
                // Nobody has cloned or pushed yet, so there is nothing on disk.
                .andExpect(jsonPath("$.exists").value(false));
    }

    @Test
    void reportsTheRepositoryOnceSomethingHasBeenPushed() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        Path work = workingCopy("size", "docs/a.md", "# A");
        gitOk(work, "push", "-q", remote(owner, "platform", tokenFor(owner)), "main");

        mockMvc.perform(authed(get("/api/workspaces/{w}/git", ws), owner))
                .andExpect(jsonPath("$.exists").value(true))
                .andExpect(jsonPath("$.sizeBytes").isNumber());
    }

    /** Reading the address needs only membership; the sync settings need ADMIN. */
    @Test
    void hidesTheAddressFromSomeoneWhoIsNotAMember() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        TestUser outsider = registerUser("outsider@acme.test", "Outsider");
        UUID ws = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(get("/api/workspaces/{w}/git", ws), outsider))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------- committing back out

    /** A fresh clone of the workspace's repository, as a directory of real files. */
    private Path cloneOf(TestUser owner, String workspaceSlug, String name) throws Exception {
        Path clone = Files.createDirectories(scratch.resolve(name));
        gitOk(clone, "clone", "-q", remote(owner, workspaceSlug, tokenFor(owner)), ".");
        return clone;
    }

    private UUID documentId(TestUser owner, UUID workspaceId, String slug) throws Exception {
        String json = mockMvc.perform(authed(
                        get("/api/workspaces/{w}/documents/by-slug/" + slug, workspaceId), owner))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(json).get("id").asText());
    }

    private record EditPayload(
            String title, String slug, String content, String documentType, Boolean internal) {
    }

    private void edit(TestUser user, UUID workspaceId, UUID documentId, EditPayload payload)
            throws Exception {
        mockMvc.perform(authed(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/workspaces/{w}/documents/{d}", workspaceId, documentId)
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(asJson(payload)), user))
                .andExpect(status().isOk());
    }

    /**
     * The other direction, and the point of the whole feature: an edit made in the
     * interface is a commit, by the person who made it, visible to a clone.
     */
    @Test
    void anEditInTheInterfaceBecomesACommit() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        Path work = workingCopy("mirror-edit", "docs/design.md", "# Design\n\nthe original");
        gitOk(work, "push", "-q", remote(owner, "platform", tokenFor(owner)), "main");

        edit(owner, ws, documentId(owner, ws, "docs/design"), new EditPayload(
                "Design", "docs/design", "# Design\n\nedited in DevForge", "ARCHITECTURE", false));

        Path clone = cloneOf(owner, "platform", "mirror-edit-clone");
        assertThat(Files.readString(clone.resolve("docs/design.md")))
                .contains("edited in DevForge")
                .contains("type: ARCHITECTURE");

        GitResult log = git(clone, "log", "-1", "--format=%an <%ae>%n%s");
        assertThat(log.output())
                .contains("Owner <owner@acme.test>")
                .contains("Update docs/design");
    }

    @Test
    void aPageCreatedInTheInterfaceAppearsInTheRepository() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        Path work = workingCopy("mirror-create", "docs/design.md", "# Design");
        gitOk(work, "push", "-q", remote(owner, "platform", tokenFor(owner)), "main");

        createDocument(owner, ws, "Consumer lag", "docs/runbooks/consumer-lag", "steps", "RUNBOOK");

        Path clone = cloneOf(owner, "platform", "mirror-create-clone");
        assertThat(clone.resolve("docs/runbooks/consumer-lag.md")).exists();
        assertThat(Files.readString(clone.resolve("docs/runbooks/consumer-lag.md")))
                .contains("title: Consumer lag")
                .contains("type: RUNBOOK")
                .endsWith("steps\n");
        // The file it never mentioned is still there.
        assertThat(clone.resolve("docs/design.md")).exists();
    }

    @Test
    void deletingAPageDeletesItsFile() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        Path work = workingCopy("mirror-delete", "docs/a.md", "# A", "docs/b.md", "# B");
        gitOk(work, "push", "-q", remote(owner, "platform", tokenFor(owner)), "main");

        mockMvc.perform(authed(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete("/api/workspaces/{w}/documents/{d}",
                                        ws, documentId(owner, ws, "docs/a")), owner))
                .andExpect(status().isNoContent());

        Path clone = cloneOf(owner, "platform", "mirror-delete-clone");
        assertThat(clone.resolve("docs/a.md")).doesNotExist();
        assertThat(clone.resolve("docs/b.md")).exists();
    }

    /** A renamed page moves rather than appearing twice. */
    @Test
    void renamingAPageMovesItsFile() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        Path work = workingCopy("mirror-rename", "docs/old-name.md", "# Old");
        gitOk(work, "push", "-q", remote(owner, "platform", tokenFor(owner)), "main");

        edit(owner, ws, documentId(owner, ws, "docs/old-name"), new EditPayload(
                "Renamed", "docs/new-name", "# Renamed", "GENERAL", false));

        Path clone = cloneOf(owner, "platform", "mirror-rename-clone");
        assertThat(clone.resolve("docs/old-name.md")).doesNotExist();
        assertThat(clone.resolve("docs/new-name.md")).exists();
        assertThat(git(clone, "log", "-1", "--format=%s").output()).contains("Rename");
    }

    /**
     * The loop breaker, proved rather than reasoned about: importing a push must
     * not produce a commit of its own, or every push would grow a twin.
     */
    @Test
    void aPushIsNotCommittedBackOut() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        createWorkspace(owner, "Platform", "platform");
        Path work = workingCopy("mirror-echo", "docs/a.md", "# A", "docs/b.md", "# B");
        gitOk(work, "push", "-q", remote(owner, "platform", tokenFor(owner)), "main");

        Path clone = cloneOf(owner, "platform", "mirror-echo-clone");

        assertThat(git(clone, "rev-list", "--count", "HEAD").output().strip()).isEqualTo("1");
        // And the files are still the ones that were pushed, not rewritten copies.
        assertThat(Files.readString(clone.resolve("docs/a.md"))).isEqualTo("# A");
    }

    /**
     * A file named something other than its slug keeps its name. Rewriting it as
     * {@code getting-started.md} would leave the repository holding the same page
     * twice, and the next push would import both.
     */
    @Test
    void rewritesTheFileWhereItAlreadyLives() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        Path work = workingCopy("mirror-name", "docs/Getting Started.md", "# Getting started");
        gitOk(work, "push", "-q", remote(owner, "platform", tokenFor(owner)), "main");

        edit(owner, ws, documentId(owner, ws, "docs/getting-started"), new EditPayload(
                "Getting started", "docs/getting-started", "# Getting started\n\nmore", "GENERAL", false));

        Path clone = cloneOf(owner, "platform", "mirror-name-clone");
        assertThat(Files.readString(clone.resolve("docs/Getting Started.md"))).contains("more");
        assertThat(clone.resolve("docs/getting-started.md")).doesNotExist();
    }

    /**
     * The round trip: what DevForge writes, DevForge reads back unchanged. A push of
     * its own commit must find nothing to do, or the two halves disagree.
     */
    @Test
    void whatItWritesItReadsBackWithoutChangingAnything() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        Path work = workingCopy("mirror-round-trip", "docs/design.md", "# Design");
        String url = remote(owner, "platform", tokenFor(owner));
        gitOk(work, "push", "-q", url, "main");

        edit(owner, ws, documentId(owner, ws, "docs/design"), new EditPayload(
                "Design", "docs/design", "# Design\n\nedited", "DECISION", false));

        // Pull DevForge's own commit back and push it again untouched.
        Path clone = cloneOf(owner, "platform", "mirror-round-trip-clone");
        write(clone, "docs/other.md", "# Other");
        gitOk(clone, "add", ".");
        commit(clone, "add another page");
        GitResult result = git(clone, "push", url, "main");

        assertThat(result.succeeded()).isTrue();
        // One page created; the one DevForge wrote is recognised as unchanged.
        assertThat(result.output()).contains("1 created, 0 updated, 0 withdrawn, 1 unchanged");
    }
}
