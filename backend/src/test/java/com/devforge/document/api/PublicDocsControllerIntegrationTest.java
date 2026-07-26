package com.devforge.document.api;

import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The public documentation is the only unauthenticated view of workspace content,
 * so these tests pin exactly how far it reaches.
 */
class PublicDocsControllerIntegrationTest extends AbstractIntegrationTest {

    /** Publishes a workspace, which requires at least one non-internal page. */
    private void publish(TestUser admin, UUID workspaceId) throws Exception {
        mockMvc.perform(authed(put("/api/workspaces/{id}/publication", workspaceId), admin)
                        .content("""
                                {"published":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true));
    }

    @Test
    void servesAPublishedWorkspaceWithoutASession() throws Exception {
        TestUser author = registerUser();
        UUID workspaceId = createWorkspace(author, "Platform", "platform");
        createDocument(author, workspaceId, "Welcome", "welcome", "# Welcome", "GENERAL");
        publish(author, workspaceId);

        // No Authorization header anywhere in these requests.
        mockMvc.perform(get("/api/public/docs/{h}/{s}", author.handle(), "platform"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Platform"))
                .andExpect(jsonPath("$.slug").value("platform"))
                .andExpect(jsonPath("$.entries[0].slug").value("welcome"));

        mockMvc.perform(get("/api/public/docs/{h}/{ws}/{doc}", author.handle(), "platform", "welcome"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("# Welcome"));
    }

    /** The containment guarantee: private stays private, with no session involved. */
    @Test
    void refusesAnUnpublishedWorkspace() throws Exception {
        TestUser author = registerUser();
        UUID workspaceId = createWorkspace(author, "Private", "private-team");
        createDocument(author, workspaceId, "Secrets", "secrets", "confidential", "GENERAL");

        mockMvc.perform(get("/api/public/docs/{h}/{s}", author.handle(), "private-team"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/docs/{h}/{ws}/{doc}", author.handle(), "private-team", "secrets"))
                .andExpect(status().isNotFound());
    }

    @Test
    void stopsServingOnceUnpublished() throws Exception {
        TestUser author = registerUser();
        UUID workspaceId = createWorkspace(author, "Platform", "platform");
        createDocument(author, workspaceId, "Welcome", "welcome", "body", "GENERAL");
        publish(author, workspaceId);

        mockMvc.perform(get("/api/public/docs/{h}/{s}", author.handle(), "platform")).andExpect(status().isOk());

        mockMvc.perform(authed(put("/api/workspaces/{id}/publication", workspaceId), author)
                        .content("""
                                {"published":false}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false));

        mockMvc.perform(get("/api/public/docs/{h}/{s}", author.handle(), "platform"))
                .andExpect(status().isNotFound());
    }

    /** A page marked internal must stay out of a published workspace's site. */
    @Test
    void holdsBackInternalPages() throws Exception {
        TestUser author = registerUser();
        UUID workspaceId = createWorkspace(author, "Platform", "platform");
        createDocument(author, workspaceId, "Public page", "public-page", "visible", "GENERAL");

        mockMvc.perform(authed(post("/api/workspaces/{id}/documents", workspaceId), author)
                        .content("""
                                {"title":"Internal notes","slug":"internal-notes","content":"secret",
                                 "documentType":"GENERAL","internal":true}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.internal").value(true));

        publish(author, workspaceId);

        mockMvc.perform(get("/api/public/docs/{h}/{s}", author.handle(), "platform"))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].slug").value("public-page"));

        mockMvc.perform(get("/api/public/docs/{h}/{ws}/{doc}", author.handle(), "platform", "internal-notes"))
                .andExpect(status().isNotFound());
    }

    /**
     * An internal page must not leak through the graph either — a public page that
     * links to it would otherwise expose its title.
     */
    @Test
    void hidesReferencesPointingAtInternalPages() throws Exception {
        TestUser author = registerUser();
        UUID workspaceId = createWorkspace(author, "Platform", "platform");
        UUID visible = createDocument(author, workspaceId, "Design", "design", "", "ARCHITECTURE");

        String internalBody = mockMvc.perform(authed(
                        post("/api/workspaces/{id}/documents", workspaceId), author)
                        .content("""
                                {"title":"Internal notes","slug":"internal-notes","content":"",
                                 "documentType":"GENERAL","internal":true}"""))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID internal = UUID.fromString(objectMapper.readTree(internalBody).get("id").asText());

        mockMvc.perform(authed(post(
                        "/api/workspaces/{id}/documents/{docId}/references", workspaceId, visible), author)
                        .content("""
                                {"targetDocumentId":"%s","referenceType":"DEPENDS_ON"}"""
                                .formatted(internal)))
                .andExpect(status().isCreated());

        publish(author, workspaceId);

        mockMvc.perform(get("/api/public/docs/{h}/{ws}/{doc}", author.handle(), "platform", "design"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.references.length()").value(0));
    }

    @Test
    void exposesTheGraphBetweenPublicPages() throws Exception {
        TestUser author = registerUser();
        UUID workspaceId = createWorkspace(author, "Platform", "platform");
        UUID runbook = createDocument(author, workspaceId, "Runbook", "runbook", "", "RUNBOOK");
        UUID design = createDocument(author, workspaceId, "Design", "design", "", "ARCHITECTURE");

        mockMvc.perform(authed(post(
                        "/api/workspaces/{id}/documents/{docId}/references", workspaceId, runbook), author)
                        .content("""
                                {"targetDocumentId":"%s","referenceType":"DEPENDS_ON"}""".formatted(design)))
                .andExpect(status().isCreated());

        publish(author, workspaceId);

        mockMvc.perform(get("/api/public/docs/{h}/{ws}/{doc}", author.handle(), "platform", "runbook"))
                .andExpect(jsonPath("$.references[0].outgoing").value(true))
                .andExpect(jsonPath("$.references[0].relatedDocumentSlug").value("design"));

        // The same edge, seen as a backlink from the far page.
        mockMvc.perform(get("/api/public/docs/{h}/{ws}/{doc}", author.handle(), "platform", "design"))
                .andExpect(jsonPath("$.references[0].outgoing").value(false))
                .andExpect(jsonPath("$.references[0].relatedDocumentTitle").value("Runbook"));
    }

    @Test
    void listsOnlyPublishedWorkspacesInTheDirectory() throws Exception {
        TestUser author = registerUser();
        UUID published = createWorkspace(author, "Published", "published-team");
        createDocument(author, published, "Welcome", "welcome", "body", "GENERAL");
        publish(author, published);

        UUID hidden = createWorkspace(author, "Hidden", "hidden-team");
        createDocument(author, hidden, "Notes", "notes", "body", "GENERAL");

        mockMvc.perform(get("/api/public/docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("published-team"))
                .andExpect(jsonPath("$[0].pageCount").value(1));
    }

    @Test
    void reportsNotFoundForAnUnknownPage() throws Exception {
        TestUser author = registerUser();
        UUID workspaceId = createWorkspace(author, "Platform", "platform");
        createDocument(author, workspaceId, "Welcome", "welcome", "body", "GENERAL");
        publish(author, workspaceId);

        mockMvc.perform(get("/api/public/docs/{h}/{ws}/{doc}", author.handle(), "platform", "no-such-page"))
                .andExpect(status().isNotFound());
    }

    @Test
    void refusesWritesToThePublicPath() throws Exception {
        mockMvc.perform(post("/api/public/docs").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void describesThisInstancesDefaultHandbook() throws Exception {
        mockMvc.perform(get("/api/public/instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handbookPath").exists())
                .andExpect(jsonPath("$.name").exists());
    }
}
