package com.devforge.document.api;

import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DocumentControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createsAndListsDocuments() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");

        mockMvc.perform(authed(post("/api/workspaces/{id}/documents", workspaceId), user)
                        .content("""
                                {"title":"Service Architecture","slug":"service-architecture",
                                 "content":"# Overview","documentType":"ARCHITECTURE"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Service Architecture"))
                .andExpect(jsonPath("$.documentType").value("ARCHITECTURE"));

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents", workspaceId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].slug").value("service-architecture"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.last").value(true));
    }

    /** Listings return an excerpt, not the whole body. */
    @Test
    void listingsOmitFullContent() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        createDocument(user, workspaceId, "Long", "long", "body ".repeat(200), "GENERAL");

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents", workspaceId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].excerpt").exists())
                .andExpect(jsonPath("$.content[0].content").doesNotExist());
    }

    @Test
    void paginatesDocuments() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        for (int index = 0; index < 5; index++) {
            createDocument(user, workspaceId, "Doc " + index, "doc-" + index, "body", "GENERAL");
        }

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents", workspaceId), user)
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void filtersByDocumentType() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        createDocument(user, workspaceId, "Arch", "arch", "body", "ARCHITECTURE");
        createDocument(user, workspaceId, "Runbook", "runbook", "body", "RUNBOOK");

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents", workspaceId), user)
                        .param("documentType", "RUNBOOK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].slug").value("runbook"));
    }

    /** Exercises the generated tsvector column and its GIN index. */
    @Test
    void searchesDocumentBodies() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        createDocument(user, workspaceId, "Auth Flow", "auth-flow",
                "We authenticate using rotating refresh tokens.", "ARCHITECTURE");
        createDocument(user, workspaceId, "Deployment", "deployment",
                "Blue-green rollout via the pipeline.", "PROCEDURE");

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents/search", workspaceId), user)
                        .param("q", "refresh tokens"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].slug").value("auth-flow"));
    }

    @Test
    void searchMatchesTitlesAsWellAsBodies() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        createDocument(user, workspaceId, "Kubernetes Runbook", "kubernetes-runbook", "body", "RUNBOOK");

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents/search", workspaceId), user)
                        .param("q", "kubernetes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    /** Search reflects edits because PostgreSQL maintains the vector. */
    @Test
    void searchIndexFollowsEdits() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID documentId = createDocument(
                user, workspaceId, "Notes", "notes", "original wording", "GENERAL");

        mockMvc.perform(authed(put("/api/workspaces/{id}/documents/{docId}", workspaceId, documentId), user)
                        .content("""
                                {"title":"Notes","slug":"notes",
                                 "content":"completely different terminology","documentType":"GENERAL"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents/search", workspaceId), user)
                        .param("q", "terminology"))
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents/search", workspaceId), user)
                        .param("q", "original"))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    /** Malformed search input must not reach the database as a syntax error. */
    @Test
    void toleratesAwkwardSearchSyntax() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        createDocument(user, workspaceId, "Notes", "notes", "body", "GENERAL");

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents/search", workspaceId), user)
                        .param("q", "\"unbalanced quote & | !"))
                .andExpect(status().isOk());
    }

    @Test
    void fetchesBySlugForDeepLinks() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        createDocument(user, workspaceId, "Auth Flow", "auth-flow", "body", "ARCHITECTURE");

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents/by-slug/{slug}", workspaceId, "auth-flow"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("body"));
    }

    @Test
    void rejectsADuplicateSlugWithinAWorkspace() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        createDocument(user, workspaceId, "First", "shared-slug", "body", "GENERAL");

        mockMvc.perform(authed(post("/api/workspaces/{id}/documents", workspaceId), user)
                        .content("""
                                {"title":"Second","slug":"shared-slug","content":"","documentType":"GENERAL"}"""))
                .andExpect(status().isConflict());
    }

    /** Slugs are scoped per workspace, so two teams may use the same one. */
    @Test
    void allowsTheSameSlugInDifferentWorkspaces() throws Exception {
        TestUser user = registerUser();
        UUID first = createWorkspace(user, "First", "first");
        UUID second = createWorkspace(user, "Second", "second");

        createDocument(user, first, "Overview", "overview", "body", "GENERAL");
        createDocument(user, second, "Overview", "overview", "body", "GENERAL");
    }

    @Test
    void rejectsAnUnknownDocumentType() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");

        mockMvc.perform(authed(post("/api/workspaces/{id}/documents", workspaceId), user)
                        .content("""
                                {"title":"Bad","slug":"bad","content":"","documentType":"NOT_A_TYPE"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesADocument() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID documentId = createDocument(user, workspaceId, "Temp", "temp", "body", "GENERAL");

        mockMvc.perform(authed(delete("/api/workspaces/{id}/documents/{docId}", workspaceId, documentId), user))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents/{docId}", workspaceId, documentId), user))
                .andExpect(status().isNotFound());
    }

    /** Tenancy isolation at the document level. */
    @Test
    void hidesDocumentsFromNonMembers() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        TestUser stranger = registerUser("stranger@example.com", "Stranger");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");
        UUID documentId = createDocument(owner, workspaceId, "Secret", "secret", "body", "GENERAL");

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents/{docId}", workspaceId, documentId), stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void cannotReachADocumentThroughAnotherWorkspaceId() throws Exception {
        TestUser user = registerUser();
        UUID first = createWorkspace(user, "First", "first");
        UUID second = createWorkspace(user, "Second", "second");
        UUID documentId = createDocument(user, first, "Doc", "doc", "body", "GENERAL");

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents/{docId}", second, documentId), user))
                .andExpect(status().isNotFound());
    }
}
