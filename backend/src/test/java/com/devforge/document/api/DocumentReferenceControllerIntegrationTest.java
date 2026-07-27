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

    // ------------------------------------------------------- falling out of step

    /** Edits a document, which is what moves its "last changed" forward. */
    private void edit(TestUser user, UUID workspaceId, UUID documentId, String content)
            throws Exception {
        mockMvc.perform(authed(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .put("/api/workspaces/{w}/documents/{d}", workspaceId, documentId), user)
                        .content("""
                                {"title":"Edited","slug":"%s","content":"%s","documentType":"GENERAL"}"""
                                .formatted("s" + documentId.toString().substring(0, 8), content)))
                .andExpect(status().isOk());
    }

    /**
     * The question the graph exists to answer, asked from the page that has to act
     * on it: something this page depends on has moved since anyone looked at this
     * one.
     */
    @Test
    void marksAnOutgoingLinkWhoseTargetHasChangedSince() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID design = createDocument(user, workspaceId, "Design", "design", "original", "ARCHITECTURE");
        UUID runbook = createDocument(user, workspaceId, "Runbook", "runbook", "steps", "RUNBOOK");
        mockMvc.perform(authed(references(workspaceId, runbook), user).content(dependsOn(design)))
                .andExpect(status().isCreated());

        // Nothing has moved yet.
        mockMvc.perform(authed(get(referencesPath(), workspaceId, runbook), user))
                .andExpect(jsonPath("$[0].behind").value(false));

        edit(user, workspaceId, design, "the design changed");

        mockMvc.perform(authed(get(referencesPath(), workspaceId, runbook), user))
                .andExpect(jsonPath("$[0].outgoing").value(true))
                .andExpect(jsonPath("$[0].behind").value(true))
                .andExpect(jsonPath("$[0].relatedChangedAt").isNotEmpty());
    }

    /** The same fact from the other end: what depends on this has not caught up. */
    @Test
    void marksABacklinkThatHasNotCaughtUp() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID design = createDocument(user, workspaceId, "Design", "design", "original", "ARCHITECTURE");
        UUID runbook = createDocument(user, workspaceId, "Runbook", "runbook", "steps", "RUNBOOK");
        mockMvc.perform(authed(references(workspaceId, runbook), user).content(dependsOn(design)))
                .andExpect(status().isCreated());

        edit(user, workspaceId, design, "the design changed");

        mockMvc.perform(authed(get(referencesPath(), workspaceId, design), user))
                .andExpect(jsonPath("$[0].outgoing").value(false))
                .andExpect(jsonPath("$[0].behind").value(true));
    }

    /** Editing the page is what says "I have taken that into account". */
    @Test
    void editingThePageClearsTheMark() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID design = createDocument(user, workspaceId, "Design", "design", "original", "ARCHITECTURE");
        UUID runbook = createDocument(user, workspaceId, "Runbook", "runbook", "steps", "RUNBOOK");
        mockMvc.perform(authed(references(workspaceId, runbook), user).content(dependsOn(design)))
                .andExpect(status().isCreated());

        edit(user, workspaceId, design, "the design changed");
        edit(user, workspaceId, runbook, "steps, updated for the new design");

        mockMvc.perform(authed(get(referencesPath(), workspaceId, runbook), user))
                .andExpect(jsonPath("$[0].behind").value(false));
    }

    /**
     * A fresh workspace must not light up. Two pages written together are in step
     * with each other, and marking everything on the first day teaches people to
     * ignore the marker.
     */
    @Test
    void doesNotMarkPagesThatHaveNeverDiverged() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID design = createDocument(user, workspaceId, "Design", "design", "a", "ARCHITECTURE");
        UUID runbook = createDocument(user, workspaceId, "Runbook", "runbook", "b", "RUNBOOK");
        mockMvc.perform(authed(references(workspaceId, runbook), user).content(dependsOn(design)))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(get(referencesPath(), workspaceId, runbook), user))
                .andExpect(jsonPath("$[0].behind").value(false));
        mockMvc.perform(authed(get(referencesPath(), workspaceId, design), user))
                .andExpect(jsonPath("$[0].behind").value(false));
    }

    // --------------------------------------------------------- what changed

    @Test
    void showsWhatTheLinkedPageChanged() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID design = createDocument(user, workspaceId, "Design", "design", "original", "ARCHITECTURE");
        UUID runbook = createDocument(user, workspaceId, "Runbook", "runbook", "steps", "RUNBOOK");
        String created = mockMvc.perform(
                        authed(references(workspaceId, runbook), user).content(dependsOn(design)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID referenceId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        edit(user, workspaceId, design, "the design changed");

        mockMvc.perform(authed(get(referencesPath() + "/{referenceId}/changes",
                        workspaceId, runbook, referenceId), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relatedDocumentTitle").value("Edited"))
                // What it said when the runbook was last written, and what it says now.
                .andExpect(jsonPath("$.before").value("original"))
                .andExpect(jsonPath("$.after").value("the design changed"))
                .andExpect(jsonPath("$.beforeRevision").value(1))
                .andExpect(jsonPath("$.afterRevision").value(2))
                .andExpect(jsonPath("$.since").isNotEmpty());
    }

    /**
     * An edge id is not a capability. Without the check, any edge could be used to
     * read the pair of documents it joins from any document the caller can open.
     */
    @Test
    void refusesAnEdgeThatDoesNotTouchThisDocument() throws Exception {
        TestUser user = registerUser();
        UUID workspaceId = createWorkspace(user, "Platform", "platform");
        UUID a = createDocument(user, workspaceId, "A", "a", "", "GENERAL");
        UUID b = createDocument(user, workspaceId, "B", "b", "", "GENERAL");
        UUID bystander = createDocument(user, workspaceId, "Bystander", "bystander", "", "GENERAL");
        String created = mockMvc.perform(authed(references(workspaceId, a), user).content(dependsOn(b)))
                .andReturn().getResponse().getContentAsString();
        UUID referenceId = UUID.fromString(objectMapper.readTree(created).get("id").asText());

        mockMvc.perform(authed(get(referencesPath() + "/{referenceId}/changes",
                        workspaceId, bystander, referenceId), user))
                .andExpect(status().isNotFound());
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
