package com.devforge.document.api;

import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The interconnection feature: typed links and the backlinks they imply. */
class DocumentReferenceControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void linksTwoDocumentsAndExposesTheBacklink() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID architecture = createDocument(user, workspaceId, "Architecture", "architecture", "", "ARCHITECTURE");
        UUID runbook = createDocument(user, workspaceId, "Runbook", "runbook", "", "RUNBOOK");

        mockMvc.perform(authed(references(workspaceId, runbook), user)
                        .content(dependsOn(architecture)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.referenceType").value("DEPENDS_ON"))
                .andExpect(jsonPath("$.outgoing").value(true))
                .andExpect(jsonPath("$.relatedDocumentTitle").value("Architecture"));

        // Seen from the runbook: one outgoing link.
        mockMvc.perform(authed(get(referencesPath(), workspaceId, runbook), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].outgoing").value(true));

        // Seen from the architecture doc: the same edge, as a backlink.
        mockMvc.perform(authed(get(referencesPath(), workspaceId, architecture), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].outgoing").value(false))
                .andExpect(jsonPath("$[0].relatedDocumentTitle").value("Runbook"));
    }

    @Test
    void rejectsASelfReference() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID document = createDocument(user, workspaceId, "Solo", "solo", "", "GENERAL");

        mockMvc.perform(authed(references(workspaceId, document), user)
                        .content(dependsOn(document)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("cannot reference itself")));
    }

    @Test
    void rejectsADuplicateTypedLink() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID source = createDocument(user, workspaceId, "Source", "source", "", "GENERAL");
        UUID target = createDocument(user, workspaceId, "Target", "target", "", "GENERAL");

        mockMvc.perform(authed(references(workspaceId, source), user).content(dependsOn(target)))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(references(workspaceId, source), user).content(dependsOn(target)))
                .andExpect(status().isConflict());
    }

    /** Different edge types between the same pair are legitimately distinct. */
    @Test
    void allowsDifferentTypesBetweenTheSamePair() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID source = createDocument(user, workspaceId, "Source", "source", "", "GENERAL");
        UUID target = createDocument(user, workspaceId, "Target", "target", "", "GENERAL");

        mockMvc.perform(authed(references(workspaceId, source), user).content(dependsOn(target)))
                .andExpect(status().isCreated());
        mockMvc.perform(authed(references(workspaceId, source), user)
                        .content("""
                                {"targetDocumentId":"%s","referenceType":"RELATED"}""".formatted(target)))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(get(referencesPath(), workspaceId, source), user))
                .andExpect(jsonPath("$.length()").value(2));
    }

    /** Cross-workspace linking must fail even with a real document id. */
    @Test
    void refusesToLinkAcrossWorkspaces() throws Exception {
        TestUser user = registerUser();
        UUID first = createWorkspace(user, "First", "first");
        UUID second = createWorkspace(user, "Second", "second");
        UUID here = createDocument(user, first, "Here", "here", "", "GENERAL");
        UUID elsewhere = createDocument(user, second, "Elsewhere", "elsewhere", "", "GENERAL");

        mockMvc.perform(authed(references(first, here), user).content(dependsOn(elsewhere)))
                .andExpect(status().isNotFound());
    }

    @Test
    void removesALinkFromTheDocumentThatDeclaredIt() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID source = createDocument(user, workspaceId, "Source", "source", "", "GENERAL");
        UUID target = createDocument(user, workspaceId, "Target", "target", "", "GENERAL");

        JsonNode created = objectMapper.readTree(
                mockMvc.perform(authed(references(workspaceId, source), user).content(dependsOn(target)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString());
        UUID referenceId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(authed(
                        delete(referencesPath() + "/{referenceId}", workspaceId, source, referenceId), user))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get(referencesPath(), workspaceId, source), user))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** A backlink belongs to its declaring document and cannot be deleted remotely. */
    @Test
    void refusesToDeleteALinkFromTheTargetSide() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID source = createDocument(user, workspaceId, "Source", "source", "", "GENERAL");
        UUID target = createDocument(user, workspaceId, "Target", "target", "", "GENERAL");

        JsonNode created = objectMapper.readTree(
                mockMvc.perform(authed(references(workspaceId, source), user).content(dependsOn(target)))
                        .andReturn().getResponse().getContentAsString());
        UUID referenceId = UUID.fromString(created.get("id").asText());

        mockMvc.perform(authed(
                        delete(referencesPath() + "/{referenceId}", workspaceId, target, referenceId), user))
                .andExpect(status().isNotFound());
    }

    /** Deleting a document must take its edges with it. */
    @Test
    void deletingADocumentRemovesItsReferences() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID source = createDocument(user, workspaceId, "Source", "source", "", "GENERAL");
        UUID target = createDocument(user, workspaceId, "Target", "target", "", "GENERAL");

        mockMvc.perform(authed(references(workspaceId, source), user).content(dependsOn(target)))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(delete("/api/workspaces/{id}/documents/{docId}", workspaceId, target), user))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get(referencesPath(), workspaceId, source), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    private static String referencesPath() {
        return "/api/workspaces/{workspaceId}/documents/{documentId}/references";
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder references(
            UUID workspaceId, UUID documentId) {
        return post(referencesPath(), workspaceId, documentId);
    }

    private static String dependsOn(UUID targetId) {
        return """
                {"targetDocumentId":"%s","referenceType":"DEPENDS_ON"}""".formatted(targetId);
    }
}
