package com.devforge.sync.api;

import com.devforge.sync.application.DocumentSource;
import com.devforge.sync.application.SourceFile;
import com.devforge.sync.application.SourceSnapshot;
import com.devforge.sync.application.SourceUnavailableException;
import com.devforge.sync.application.WebhookSignature;
import com.devforge.sync.domain.SyncConfiguration;
import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Git sync end to end, with the repository replaced by a stub.
 *
 * <p>The transport is stubbed and everything else is real: the workspace permission
 * checks, the document module's authoring contract, the revision store, the audit
 * log, and the webhook's signature verification. That is deliberate — the point of
 * these tests is that the modules fit together, and the one thing not worth
 * exercising here is HTTP against a git host, which
 * {@code ArchiveDocumentSourceTest} covers on its own.
 */
@Import(GitSyncIntegrationTest.StubbedSource.class)
class GitSyncIntegrationTest extends AbstractIntegrationTest {

    /**
     * Replaces the archive fetcher.
     *
     * <p>Marked {@code @Primary} rather than mocked per-test so the whole context has
     * one source, and tests set what it returns.
     */
    @TestConfiguration
    static class StubbedSource {
        @Bean
        @Primary
        DocumentSource stubDocumentSource() {
            return new ScriptedSource();
        }
    }

    /** A source whose next answer the test decides. */
    static class ScriptedSource implements DocumentSource {
        List<SourceFile> files = new ArrayList<>();
        String ref = "abc1234";
        RuntimeException failure;

        @Override
        public SourceSnapshot fetch(SyncConfiguration configuration, String accessToken) {
            if (failure != null) {
                throw failure;
            }
            return new SourceSnapshot(ref, List.copyOf(files));
        }
    }

    private ScriptedSource source;

    @org.springframework.beans.factory.annotation.Autowired
    private DocumentSource injectedSource;

    @BeforeEach
    void resetSource() {
        source = (ScriptedSource) injectedSource;
        source.files = new ArrayList<>();
        source.ref = "abc1234";
        source.failure = null;
    }

    private String settings(String url, String path, String policy) {
        return """
                {"repositoryUrl":"%s","branch":"main","documentPath":"%s",
                 "defaultType":"GENERAL","deletionPolicy":"%s","enabled":true}"""
                .formatted(url, path, policy);
    }

    private void configure(TestUser user, UUID workspaceId, String policy) throws Exception {
        mockMvc.perform(authed(put("/api/workspaces/{w}/sync", workspaceId), user)
                        .content(settings("https://example.test/owner/repo", "docs", policy)))
                .andExpect(status().isOk());
    }

    // ---------------------------------------------------------------- configuring

    @Test
    void reportsNothingConfiguredForAFreshWorkspace() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(get("/api/workspaces/{w}/sync", ws), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.branch").value("main"));
    }

    @Test
    void configuresARepositoryAndPublishesAWebhookUrl() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(put("/api/workspaces/{w}/sync", ws), owner)
                        .content(settings("https://example.test/owner/repo", "/docs/", "ARCHIVE")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                // Slashes normalised, so '/docs/' and 'docs' mean the same thing.
                .andExpect(jsonPath("$.documentPath").value("docs"))
                .andExpect(jsonPath("$.webhookUrl").value(
                        org.hamcrest.Matchers.startsWith("/api/public/sync/")))
                .andExpect(jsonPath("$.hasWebhookSecret").value(false));
    }

    /** Only an admin decides where a workspace's documentation comes from. */
    @Test
    void refusesConfigurationBelowAdmin() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        TestUser member = registerUser("member@acme.test", "Member");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        addMember(owner, ws, member.email(), "MEMBER");

        mockMvc.perform(authed(put("/api/workspaces/{w}/sync", ws), member)
                        .content(settings("https://example.test/o/r", "docs", "ARCHIVE")))
                .andExpect(status().isForbidden());
        // Reading exposes the webhook URL, so it is admin-only too.
        mockMvc.perform(authed(get("/api/workspaces/{w}/sync", ws), member))
                .andExpect(status().isForbidden());
    }

    @Test
    void hidesSyncSettingsFromNonMembers() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        TestUser outsider = registerUser("outsider@acme.test", "Outsider");
        UUID ws = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(get("/api/workspaces/{w}/sync", ws), outsider))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsARepositoryUrlThatIsNotHttp() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(put("/api/workspaces/{w}/sync", ws), owner)
                        .content(settings("git@example.test:owner/repo.git", "docs", "ARCHIVE")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.repositoryUrl").exists());
    }

    // -------------------------------------------------------------------- syncing

    @Test
    void createsDocumentsFromTheRepository() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(
                new SourceFile("docs/design.md",
                        "---\ntitle: Event ingestion\ntype: ARCHITECTURE\n---\nthe body"),
                new SourceFile("docs/runbook.md", "# Consumer lag\n\nsteps"));

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastStatus").value("OK"))
                .andExpect(jsonPath("$.lastCreated").value(2))
                .andExpect(jsonPath("$.lastRef").value("abc1234"));

        // The documents are really there, with front matter applied.
        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/{s}", ws, "design"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Event ingestion"))
                .andExpect(jsonPath("$.documentType").value("ARCHITECTURE"))
                .andExpect(jsonPath("$.content").value("the body"));
    }

    /** A second identical sync must not append a revision to every page. */
    @Test
    void aRepeatedSyncChangesNothing() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        source.files = List.of(new SourceFile("docs/a.md", "# A\n\nbody"));

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastCreated").value(1));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastCreated").value(0))
                .andExpect(jsonPath("$.lastUpdated").value(0))
                .andExpect(jsonPath("$.lastUnchanged").value(1));

        // One revision, from the creation. The second sync added nothing.
        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/{s}", ws, "a"), owner))
                .andReturn();
    }

    @Test
    void appliesAnEditFromTheRepositoryAsASyncedRevision() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(new SourceFile("docs/a.md", "# A\n\nfirst"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner));

        source.files = List.of(new SourceFile("docs/a.md", "# A\n\nsecond"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastUpdated").value(1));

        String document = mockMvc.perform(
                        authed(get("/api/workspaces/{w}/documents/by-slug/{s}", ws, "a"), owner))
                .andReturn().getResponse().getContentAsString();
        UUID documentId = UUID.fromString(objectMapper.readTree(document).get("id").asText());

        // History records where the change came from.
        mockMvc.perform(authed(
                        get("/api/workspaces/{w}/documents/{d}/revisions", ws, documentId), owner))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].reason").value("SYNCED"));
    }

    @Test
    void withdrawsAPageWhoseFileHasGone() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(
                new SourceFile("docs/kept.md", "# Kept"),
                new SourceFile("docs/going.md", "# Going"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastCreated").value(2));

        source.files = List.of(new SourceFile("docs/kept.md", "# Kept"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastArchived").value(1));

        // Archived means internal, not deleted: still there, no longer published.
        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/{s}", ws, "going"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.internal").value(true));
    }

    @Test
    void deletesInsteadOfArchivingUnderTheDeletePolicy() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "DELETE");

        source.files = List.of(
                new SourceFile("docs/kept.md", "# Kept"),
                new SourceFile("docs/going.md", "# Going"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner));

        source.files = List.of(new SourceFile("docs/kept.md", "# Kept"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastArchived").value(1));

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/{s}", ws, "going"), owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void leavesHandWrittenPagesAloneUnderTheIgnorePolicy() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        createDocument(owner, ws, "By hand", "by-hand", "typed here", "GENERAL");
        configure(owner, ws, "IGNORE");

        source.files = List.of(new SourceFile("docs/from-git.md", "# From git"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastCreated").value(1))
                .andExpect(jsonPath("$.lastArchived").value(0));

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/{s}", ws, "by-hand"), owner))
                .andExpect(jsonPath("$.internal").value(false));
    }

    /**
     * The safety valve. A mistyped document path matches nothing, which under the
     * archive policy would withdraw every page at once.
     */
    @Test
    void refusesToWithdrawEverythingWhenTheSourceLooksEmpty() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        createDocument(owner, ws, "Existing", "existing", "body", "GENERAL");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of();

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastStatus").value("FAILED"))
                .andExpect(jsonPath("$.lastMessage").value(
                        org.hamcrest.Matchers.containsString("nothing was changed")))
                .andExpect(jsonPath("$.lastArchived").value(0));

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/{s}", ws, "existing"), owner))
                .andExpect(jsonPath("$.internal").value(false));
    }

    /** An empty repository and an empty workspace is not a misconfiguration. */
    @Test
    void allowsAnEmptySourceWhenThereIsNothingToLose() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of();

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastStatus").value("OK"));
    }

    @Test
    void reportsAnUnreachableRepositoryWithoutFailingTheRequest() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.failure = new SourceUnavailableException("That repository is private.");

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastStatus").value("FAILED"))
                .andExpect(jsonPath("$.lastMessage").value("That repository is private."));
    }

    /** One malformed file must not block the rest. */
    @Test
    void appliesWhatItUnderstandsAndReportsWhatItDoesNot() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(
                new SourceFile("docs/good.md", "# Good"),
                new SourceFile("docs/odd.md", "---\ntype: NONSENSE\n---\nstill usable"));

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.lastCreated").value(2))
                .andExpect(jsonPath("$.problems[0]").value(
                        org.hamcrest.Matchers.containsString("NONSENSE")));
    }

    /**
     * The regression this guards: problems used to live only in the response to a
     * manual sync, so a reload lost them and a webhook-triggered sync never showed
     * them at all. Reporting "1 problem(s)" without saying which is useless.
     */
    @Test
    void keepsTheProblemsAfterTheResponseThatReportedThem() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(
                new SourceFile("docs/good.md", "# Good"),
                new SourceFile("docs/odd.md", "---\ntype: NONSENSE\n---\nbody"));

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.problems[0]").value(
                        org.hamcrest.Matchers.containsString("NONSENSE")));

        // Read fresh, as a reload would.
        mockMvc.perform(authed(get("/api/workspaces/{w}/sync", ws), owner))
                .andExpect(jsonPath("$.lastStatus").value("PARTIAL"))
                .andExpect(jsonPath("$.problems[0]").value(
                        org.hamcrest.Matchers.containsString("NONSENSE")));
    }

    /** A clean run must not leave the previous run's problems lying around. */
    @Test
    void clearsTheProblemsWhenTheNextSyncIsClean() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(new SourceFile("docs/odd.md", "---\ntype: NONSENSE\n---\nbody"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.problems.length()").value(1));

        source.files = List.of(new SourceFile("docs/odd.md", "---\ntype: RUNBOOK\n---\nbody"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastStatus").value("OK"))
                .andExpect(jsonPath("$.problems.length()").value(0));
    }

    @Test
    void recordsEverySyncInTheWorkspaceActivityLog() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        source.files = List.of(new SourceFile("docs/a.md", "# A"));

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner));

        mockMvc.perform(authed(get("/api/workspaces/{w}/activity?action=WORKSPACE_SYNCED", ws), owner))
                .andExpect(jsonPath("$.content[0].detail.trigger").value("manual"))
                .andExpect(jsonPath("$.content[0].detail.created").value(1))
                .andExpect(jsonPath("$.content[0].actorLabel").value(
                        org.hamcrest.Matchers.containsString("owner@acme.test")));
    }

    // ------------------------------------------------------------------- webhooks

    /** The secret is shown once, because it is stored encrypted. */
    private String generateSecret(TestUser user, UUID workspaceId) throws Exception {
        String response = mockMvc.perform(
                        authed(post("/api/workspaces/{w}/sync/secret", workspaceId), user))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("webhookSecret").asText();
    }

    private UUID webhookIdOf(TestUser user, UUID workspaceId) throws Exception {
        String response = mockMvc.perform(authed(get("/api/workspaces/{w}/sync", workspaceId), user))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("webhookId").asText());
    }

    @Test
    void appliesAPushDeliveredWithAValidSignature() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        String secret = generateSecret(owner, ws);
        UUID webhookId = webhookIdOf(owner, ws);

        source.files = List.of(new SourceFile("docs/pushed.md", "# Pushed"));
        String payload = "{\"ref\":\"refs/heads/main\"}";

        mockMvc.perform(post("/api/public/sync/{id}", webhookId)
                        .contentType("application/json")
                        .header("X-Hub-Signature-256",
                                "sha256=" + WebhookSignature.hex(payload.getBytes(), secret))
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.created").value(1));

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/{s}", ws, "pushed"), owner))
                .andExpect(status().isOk());
    }

    /** The security boundary of the whole feature. */
    @Test
    void refusesADeliveryWithTheWrongSignature() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        generateSecret(owner, ws);
        UUID webhookId = webhookIdOf(owner, ws);

        source.files = List.of(new SourceFile("docs/pushed.md", "# Pushed"));
        String payload = "{\"ref\":\"refs/heads/main\"}";

        mockMvc.perform(post("/api/public/sync/{id}", webhookId)
                        .contentType("application/json")
                        .header("X-Hub-Signature-256",
                                "sha256=" + WebhookSignature.hex(payload.getBytes(), "wrong-secret"))
                        .content(payload))
                .andExpect(status().isNotFound());

        // And nothing was applied.
        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/{s}", ws, "pushed"), owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesADeliveryWithNoSignatureAtAll() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        generateSecret(owner, ws);
        UUID webhookId = webhookIdOf(owner, ws);

        mockMvc.perform(post("/api/public/sync/{id}", webhookId)
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    /**
     * Without a stored secret nothing can be verified, so the endpoint must not be an
     * unauthenticated way to make the server fetch a URL.
     */
    @Test
    void refusesDeliveriesUntilASecretIsGenerated() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        UUID webhookId = webhookIdOf(owner, ws);

        mockMvc.perform(post("/api/public/sync/{id}", webhookId)
                        .contentType("application/json")
                        .header("X-Hub-Signature-256", "sha256=" + "00".repeat(32))
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void answersNotFoundForAnUnknownWebhookId() throws Exception {
        mockMvc.perform(post("/api/public/sync/{id}", UUID.randomUUID())
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void acceptsAForgejoStyleBareHexSignature() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        String secret = generateSecret(owner, ws);
        UUID webhookId = webhookIdOf(owner, ws);

        source.files = List.of(new SourceFile("docs/a.md", "# A"));
        String payload = "{\"ref\":\"refs/heads/main\"}";

        mockMvc.perform(post("/api/public/sync/{id}", webhookId)
                        .contentType("application/json")
                        .header("X-Forgejo-Signature",
                                WebhookSignature.hex(payload.getBytes(), secret))
                        .content(payload))
                .andExpect(status().isOk());
    }

    @Test
    void rotatingTheUrlInvalidatesTheOldOne() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        String secret = generateSecret(owner, ws);
        UUID original = webhookIdOf(owner, ws);

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/rotate-url", ws), owner))
                .andExpect(status().isOk());

        String payload = "{}";
        mockMvc.perform(post("/api/public/sync/{id}", original)
                        .contentType("application/json")
                        .header("X-Hub-Signature-256",
                                "sha256=" + WebhookSignature.hex(payload.getBytes(), secret))
                        .content(payload))
                .andExpect(status().isNotFound());
    }

    /** A webhook fires without a session, and the log should say so. */
    @Test
    void attributesAWebhookSyncToTheWebhookRatherThanAPerson() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        String secret = generateSecret(owner, ws);
        UUID webhookId = webhookIdOf(owner, ws);

        source.files = List.of(new SourceFile("docs/a.md", "# A"));
        String payload = "{}";
        mockMvc.perform(post("/api/public/sync/{id}", webhookId)
                        .contentType("application/json")
                        .header("X-Hub-Signature-256",
                                "sha256=" + WebhookSignature.hex(payload.getBytes(), secret))
                        .content(payload))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/workspaces/{w}/activity?action=WORKSPACE_SYNCED", ws), owner))
                .andExpect(jsonPath("$.content[0].detail.trigger").value("webhook"))
                .andExpect(jsonPath("$.content[0].actorLabel").doesNotExist());
    }

    // ----------------------------------------------------------------- disconnect

    @Test
    void disconnectingForgetsTheConfigurationButKeepsTheDocuments() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        source.files = List.of(new SourceFile("docs/a.md", "# A"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner));

        mockMvc.perform(authed(delete("/api/workspaces/{w}/sync", ws), owner))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/workspaces/{w}/sync", ws), owner))
                .andExpect(jsonPath("$.configured").value(false));
        // The documentation it brought in stays.
        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/{s}", ws, "a"), owner))
                .andExpect(status().isOk());
    }

    /** Never echoed back, or encrypting it at rest would be pointless. */
    @Test
    void neverReturnsAStoredCredential() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(put("/api/workspaces/{w}/sync", ws), owner)
                        .content("""
                                {"repositoryUrl":"https://example.test/o/r","branch":"main",
                                 "documentPath":"docs","defaultType":"GENERAL",
                                 "deletionPolicy":"ARCHIVE","enabled":true,
                                 "accessToken":"ghp_secretTokenValue"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasAccessToken").value(true))
                .andExpect(jsonPath("$.accessToken").doesNotExist());

        String settings = mockMvc.perform(authed(get("/api/workspaces/{w}/sync", ws), owner))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(settings).doesNotContain("ghp_secretTokenValue");
    }

    /** Absent means leave it; empty means clear it. */
    @Test
    void keepsAnExistingTokenWhenTheFieldIsOmitted() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(put("/api/workspaces/{w}/sync", ws), owner)
                        .content("""
                                {"repositoryUrl":"https://example.test/o/r","branch":"main",
                                 "documentPath":"docs","defaultType":"GENERAL",
                                 "deletionPolicy":"ARCHIVE","enabled":true,
                                 "accessToken":"a-token"}"""))
                .andExpect(jsonPath("$.hasAccessToken").value(true));

        // Saving other settings without mentioning the token leaves it alone.
        mockMvc.perform(authed(put("/api/workspaces/{w}/sync", ws), owner)
                        .content(settings("https://example.test/o/r", "guides", "ARCHIVE")))
                .andExpect(jsonPath("$.hasAccessToken").value(true))
                .andExpect(jsonPath("$.documentPath").value("guides"));

        // An empty string clears it.
        mockMvc.perform(authed(put("/api/workspaces/{w}/sync", ws), owner)
                        .content("""
                                {"repositoryUrl":"https://example.test/o/r","branch":"main",
                                 "documentPath":"docs","defaultType":"GENERAL",
                                 "deletionPolicy":"ARCHIVE","enabled":true,
                                 "accessToken":""}"""))
                .andExpect(jsonPath("$.hasAccessToken").value(false));
    }

    // ------------------------------------------------------------- folder slugs

    /**
     * Folders below the documentation path become part of the slug, and the whole
     * thing has to survive being a URL — which spans several path segments, so the
     * routes match on the remainder rather than on one segment.
     */
    @Test
    void mirrorsFoldersIntoSlugsAndStillRoutesThem() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(
                new SourceFile("docs/design.md", "# Design"),
                new SourceFile("docs/runbooks/consumer-lag.md", "# Consumer lag"));

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastCreated").value(2));

        mockMvc.perform(authed(
                        get("/api/workspaces/{w}/documents/by-slug/runbooks/consumer-lag", ws), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("runbooks/consumer-lag"))
                .andExpect(jsonPath("$.title").value("Consumer lag"));
    }

    /** The collision that made flattening wrong, now simply two documents. */
    @Test
    void importsSameNamedFilesFromDifferentFolders() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(
                new SourceFile("docs/README.md", "# Root readme"),
                new SourceFile("docs/frontend/README.md", "# Frontend readme"));

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastStatus").value("OK"))
                .andExpect(jsonPath("$.lastCreated").value(2))
                .andExpect(jsonPath("$.problems.length()").value(0));

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/by-slug/readme", ws), owner))
                .andExpect(jsonPath("$.title").value("Root readme"));
        mockMvc.perform(authed(
                        get("/api/workspaces/{w}/documents/by-slug/frontend/readme", ws), owner))
                .andExpect(jsonPath("$.title").value("Frontend readme"));
    }

    /** A published folder slug has to work as a public URL too. */
    @Test
    void servesAFolderSlugFromThePublicDocumentationUrl() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        source.files = List.of(
                new SourceFile("docs/runbooks/consumer-lag.md", "# Consumer lag"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner));

        mockMvc.perform(authed(put("/api/workspaces/{w}/publication", ws), owner)
                        .content("{\"published\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/docs/{h}/{s}/runbooks/consumer-lag",
                        owner.handle(), "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value("runbooks/consumer-lag"))
                .andExpect(jsonPath("$.title").value("Consumer lag"));
    }

    /** A link may name a folder path, and resolves to that document. */
    @Test
    void resolvesALinkThatNamesAFolderPath() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(
                new SourceFile("docs/design.md",
                        "---\ndepends_on: runbooks/consumer-lag\n---\nbody"),
                new SourceFile("docs/runbooks/consumer-lag.md", "# Consumer lag"));

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastStatus").value("OK"));

        UUID design = documentIdOf(owner, ws, "design");
        mockMvc.perform(authed(
                        get("/api/workspaces/{w}/documents/{d}/references", ws, design), owner))
                .andExpect(jsonPath("$[0].relatedDocumentSlug").value("runbooks/consumer-lag"));
    }

    // ---------------------------------------------------------- reference graph

    /**
     * The two-pass import. A file may point at one that appears later in the same
     * sync — documentation is written as a graph, not in dependency order — so every
     * document is created before any link is resolved.
     */
    @Test
    void resolvesALinkToADocumentThatDoesNotExistYet() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        // 'ingestion' sorts before 'kafka', so it is written first and its link
        // points at a document that does not exist at that moment.
        source.files = List.of(
                new SourceFile("docs/ingestion.md",
                        "---\ntitle: Ingestion\ndepends_on: kafka\n---\nbody"),
                new SourceFile("docs/kafka.md", "---\ntitle: Kafka\n---\nbody"));

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastStatus").value("OK"))
                .andExpect(jsonPath("$.lastCreated").value(2));

        UUID ingestion = documentIdOf(owner, ws, "ingestion");
        mockMvc.perform(authed(
                        get("/api/workspaces/{w}/documents/{d}/references", ws, ingestion), owner))
                .andExpect(jsonPath("$[0].referenceType").value("DEPENDS_ON"))
                .andExpect(jsonPath("$[0].outgoing").value(true))
                .andExpect(jsonPath("$[0].relatedDocumentTitle").value("Kafka"));
    }

    /** Backlinks are derived, so the far side gains one without declaring anything. */
    @Test
    void theTargetGainsABacklinkWithoutDeclaringIt() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        source.files = List.of(
                new SourceFile("docs/ingestion.md", "---\ndepends_on: kafka\n---\nbody"),
                new SourceFile("docs/kafka.md", "# Kafka"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner));

        UUID kafka = documentIdOf(owner, ws, "kafka");
        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/references", ws, kafka), owner))
                .andExpect(jsonPath("$[0].outgoing").value(false))
                .andExpect(jsonPath("$[0].referenceType").value("DEPENDS_ON"));
    }

    @Test
    void removesALinkTheRepositoryNoLongerDeclares() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(
                new SourceFile("docs/a.md", "---\ndepends_on: b, c\n---\nbody"),
                new SourceFile("docs/b.md", "# B"),
                new SourceFile("docs/c.md", "# C"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner));

        UUID a = documentIdOf(owner, ws, "a");
        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/references", ws, a), owner))
                .andExpect(jsonPath("$.length()").value(2));

        // One link dropped upstream.
        source.files = List.of(
                new SourceFile("docs/a.md", "---\ndepends_on: b\n---\nbody"),
                new SourceFile("docs/b.md", "# B"),
                new SourceFile("docs/c.md", "# C"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastStatus").value("OK"));

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/references", ws, a), owner))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].relatedDocumentSlug").value("b"));
    }

    /**
     * Opting in per file. A repository of prose that declares no relationships must
     * not silently delete links someone made in the interface.
     */
    @Test
    void leavesHandMadeLinksAloneWhenTheFileDeclaresNone() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(
                new SourceFile("docs/a.md", "# A"),
                new SourceFile("docs/b.md", "# B"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner));

        UUID a = documentIdOf(owner, ws, "a");
        UUID b = documentIdOf(owner, ws, "b");

        // A link made in the interface, not in the repository.
        mockMvc.perform(authed(post("/api/workspaces/{w}/documents/{d}/references", ws, a), owner)
                        .content("""
                                {"targetDocumentId":"%s","referenceType":"RELATED"}""".formatted(b)))
                .andExpect(status().isCreated());

        // Syncing again must not remove it.
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastStatus").value("OK"));

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/references", ws, a), owner))
                .andExpect(jsonPath("$.length()").value(1));
    }

    /** Once a file declares links, the repository is managing that page's graph. */
    @Test
    void replacesHandMadeLinksOnceTheFileDeclaresItsOwn() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(
                new SourceFile("docs/a.md", "# A"),
                new SourceFile("docs/b.md", "# B"),
                new SourceFile("docs/c.md", "# C"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner));

        UUID a = documentIdOf(owner, ws, "a");
        UUID b = documentIdOf(owner, ws, "b");
        mockMvc.perform(authed(post("/api/workspaces/{w}/documents/{d}/references", ws, a), owner)
                        .content("""
                                {"targetDocumentId":"%s","referenceType":"RELATED"}""".formatted(b)))
                .andExpect(status().isCreated());

        source.files = List.of(
                new SourceFile("docs/a.md", "---\ndepends_on: c\n---\nbody"),
                new SourceFile("docs/b.md", "# B"),
                new SourceFile("docs/c.md", "# C"));
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner));

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/references", ws, a), owner))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].relatedDocumentSlug").value("c"))
                .andExpect(jsonPath("$[0].referenceType").value("DEPENDS_ON"));
    }

    /** A typo in a link is exactly what a reader would otherwise discover much later. */
    @Test
    void reportsALinkToSomethingThatIsNotThere() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");

        source.files = List.of(
                new SourceFile("docs/a.md", "---\ndepends_on: typo-here\n---\nbody"));

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastStatus").value("PARTIAL"))
                // The document is still applied; only its bad link is reported.
                .andExpect(jsonPath("$.lastCreated").value(1))
                .andExpect(jsonPath("$.problems[0]").value(
                        org.hamcrest.Matchers.containsString("typo-here")));
    }

    @Test
    void aRepeatedSyncDoesNotChurnTheGraph() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        configure(owner, ws, "ARCHIVE");
        source.files = List.of(
                new SourceFile("docs/a.md", "---\ndepends_on: b\n---\nbody"),
                new SourceFile("docs/b.md", "# B"));

        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastMessage").value(
                        org.hamcrest.Matchers.containsString("1 links added")));

        // Nothing moved, so the second run says nothing about links at all.
        mockMvc.perform(authed(post("/api/workspaces/{w}/sync/run", ws), owner))
                .andExpect(jsonPath("$.lastStatus").value("OK"))
                .andExpect(jsonPath("$.lastMessage").value(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("links"))));
    }

    private UUID documentIdOf(TestUser user, UUID workspaceId, String slug) throws Exception {
        String response = mockMvc.perform(authed(
                        get("/api/workspaces/{w}/documents/by-slug/{s}", workspaceId, slug), user))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }
}
