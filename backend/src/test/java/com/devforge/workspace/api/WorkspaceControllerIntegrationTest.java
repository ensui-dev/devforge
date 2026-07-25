package com.devforge.workspace.api;

import com.devforge.support.AbstractIntegrationTest;
import com.devforge.workspace.application.CreateWorkspaceRequest;
import com.devforge.workspace.application.UpdateWorkspaceRequest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class WorkspaceControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createsAWorkspaceAndEnrolsTheCreatorAsOwner() throws Exception {
        TestUser owner = registerUser();

        mockMvc.perform(authed(post("/api/workspaces"), owner)
                        .content(asJson(new CreateWorkspaceRequest("Platform", "Core services", "platform"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Platform"))
                .andExpect(jsonPath("$.slug").value("platform"))
                .andExpect(jsonPath("$.callerRole").value("OWNER"));

        mockMvc.perform(authed(get("/api/workspaces"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("platform"));
    }

    /**
     * The isolation guarantee: workspaces are invisible to non-members. The
     * original suite could not express this — there were no users.
     */
    @Test
    void doesNotListAnotherUsersWorkspaces() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        TestUser stranger = registerUser("stranger@example.com", "Stranger");
        createWorkspace(owner, "platform");

        mockMvc.perform(authed(get("/api/workspaces"), stranger))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** A non-member gets 404, not 403, so workspaces cannot be probed. */
    @Test
    void hidesAWorkspaceFromNonMembers() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        TestUser stranger = registerUser("stranger@example.com", "Stranger");
        UUID workspaceId = createWorkspace(owner, "platform");

        mockMvc.perform(authed(get("/api/workspaces/{id}", workspaceId), stranger))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsADuplicateSlug() throws Exception {
        TestUser owner = registerUser();
        createWorkspace(owner, "platform");

        mockMvc.perform(authed(post("/api/workspaces"), owner)
                        .content(asJson(new CreateWorkspaceRequest("Another", null, "platform"))))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsAnInvalidSlugWithFieldDetail() throws Exception {
        TestUser owner = registerUser();

        mockMvc.perform(authed(post("/api/workspaces"), owner)
                        .content(asJson(new CreateWorkspaceRequest("Bad", null, "Not A Slug"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.slug").exists());
    }

    @Test
    void updatesAWorkspace() throws Exception {
        TestUser owner = registerUser();
        UUID workspaceId = createWorkspace(owner, "platform");

        mockMvc.perform(authed(put("/api/workspaces/{id}", workspaceId), owner)
                        .content(asJson(new UpdateWorkspaceRequest("Renamed", "New description", "renamed"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed"))
                .andExpect(jsonPath("$.slug").value("renamed"));
    }

    @Test
    void deletesAWorkspaceAsOwner() throws Exception {
        TestUser owner = registerUser();
        UUID workspaceId = createWorkspace(owner, "platform");

        mockMvc.perform(authed(delete("/api/workspaces/{id}", workspaceId), owner))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/workspaces/{id}", workspaceId), owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAMalformedWorkspaceId() throws Exception {
        TestUser owner = registerUser();

        mockMvc.perform(authed(get("/api/workspaces/{id}", "not-a-uuid"), owner))
                .andExpect(status().isBadRequest());
    }

    private UUID createWorkspace(TestUser owner, String slug) throws Exception {
        return createWorkspace(owner, "Platform", slug);
    }
}
