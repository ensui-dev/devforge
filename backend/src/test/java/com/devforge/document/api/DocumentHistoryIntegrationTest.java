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
 * Document history.
 *
 * <p>The property everything else rests on: history is append-only. Restoring an
 * old revision writes a new one rather than rewinding, so you can always see that
 * a restore happened and undo it the same way.
 */
class DocumentHistoryIntegrationTest extends AbstractIntegrationTest {

    private String body(String title, String content) {
        return """
                {"title":"%s","slug":"page","content":"%s",
                 "documentType":"GENERAL","internal":false}"""
                .formatted(title, content);
    }

    @Test
    void createsRevisionOneFromTheDocumentAsCreated() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "First", "page", "original body", "GENERAL");

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/revisions", ws, doc), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].revision").value(1))
                .andExpect(jsonPath("$.content[0].reason").value("CREATED"))
                .andExpect(jsonPath("$.content[0].title").value("First"))
                .andExpect(jsonPath("$.content[0].authorLabel").value(
                        org.hamcrest.Matchers.containsString("owner@acme.test")));
    }

    /** A list of fifty revisions must not ship the document fifty times. */
    @Test
    void omitsBodiesFromTheHistoryList() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "First", "page", "original body", "GENERAL");

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/revisions", ws, doc), owner))
                .andExpect(jsonPath("$.content[0].content").doesNotExist());

        // ...but reading one revision gives you the whole thing.
        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/revisions/1", ws, doc), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("original body"));
    }

    @Test
    void appendsARevisionOnEveryEdit() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "First", "page", "v1", "GENERAL");

        mockMvc.perform(authed(put("/api/workspaces/{w}/documents/{d}", ws, doc), owner)
                        .content(body("Second", "v2")))
                .andExpect(status().isOk());
        mockMvc.perform(authed(put("/api/workspaces/{w}/documents/{d}", ws, doc), owner)
                        .content(body("Third", "v3")))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/revisions", ws, doc), owner))
                .andExpect(jsonPath("$.totalElements").value(3))
                // Newest first.
                .andExpect(jsonPath("$.content[0].revision").value(3))
                .andExpect(jsonPath("$.content[0].title").value("Third"))
                .andExpect(jsonPath("$.content[2].revision").value(1));
    }

    /** The property that defines the feature. */
    @Test
    void restoringAppendsARevisionRatherThanRewinding() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "First", "page", "v1", "GENERAL");

        mockMvc.perform(authed(put("/api/workspaces/{w}/documents/{d}", ws, doc), owner)
                        .content(body("Second", "v2")))
                .andExpect(status().isOk());

        mockMvc.perform(authed(
                        post("/api/workspaces/{w}/documents/{d}/revisions/1/restore", ws, doc), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("First"))
                .andExpect(jsonPath("$.content").value("v1"));

        // Three revisions, not one: the edit that was undone is still there.
        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/revisions", ws, doc), owner))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[0].revision").value(3))
                .andExpect(jsonPath("$.content[0].reason").value("RESTORED"))
                .andExpect(jsonPath("$.content[0].restoredFrom").value(1));

        // And revision 2 still says what it said.
        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/revisions/2", ws, doc), owner))
                .andExpect(jsonPath("$.content").value("v2"));
    }

    /** So a restore can itself be undone. */
    @Test
    void aRestoreCanBeRestoredAwayFrom() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "First", "page", "v1", "GENERAL");

        mockMvc.perform(authed(put("/api/workspaces/{w}/documents/{d}", ws, doc), owner)
                        .content(body("Second", "v2")))
                .andExpect(status().isOk());
        mockMvc.perform(authed(
                        post("/api/workspaces/{w}/documents/{d}/revisions/1/restore", ws, doc), owner))
                .andExpect(status().isOk());
        mockMvc.perform(authed(
                        post("/api/workspaces/{w}/documents/{d}/revisions/2/restore", ws, doc), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("v2"));

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/revisions", ws, doc), owner))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void reportsAnUnknownRevisionRatherThanTheLatest() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "First", "page", "v1", "GENERAL");

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/revisions/99", ws, doc), owner))
                .andExpect(status().isNotFound());
        mockMvc.perform(authed(
                        post("/api/workspaces/{w}/documents/{d}/revisions/99/restore", ws, doc), owner))
                .andExpect(status().isNotFound());
    }

    /** History reveals nothing a reader cannot already see, so VIEWER suffices. */
    @Test
    void aViewerCanReadHistoryButNotRestore() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        TestUser viewer = registerUser("viewer@acme.test", "Viewer");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        addMember(owner, ws, viewer.email(), "VIEWER");
        UUID doc = createDocument(owner, ws, "First", "page", "v1", "GENERAL");

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/revisions", ws, doc), viewer))
                .andExpect(status().isOk());
        mockMvc.perform(authed(
                        post("/api/workspaces/{w}/documents/{d}/revisions/1/restore", ws, doc), viewer))
                .andExpect(status().isForbidden());
    }

    @Test
    void hidesHistoryFromNonMembers() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        TestUser outsider = registerUser("outsider@acme.test", "Outsider");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "First", "page", "v1", "GENERAL");

        // 404 rather than 403: a non-member must not learn the workspace exists.
        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}/revisions", ws, doc), outsider))
                .andExpect(status().isNotFound());
    }
}
