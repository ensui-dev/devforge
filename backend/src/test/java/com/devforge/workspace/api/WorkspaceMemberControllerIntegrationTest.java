package com.devforge.workspace.api;

import com.devforge.support.AbstractIntegrationTest;
import com.devforge.workspace.application.AddMemberRequest;
import com.devforge.workspace.application.UpdateMemberRoleRequest;
import com.devforge.workspace.application.UpdateWorkspaceRequest;
import com.devforge.workspace.contract.WorkspaceRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end coverage of the team model and the role rules that protect it. */
class WorkspaceMemberControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void listsTheOwnerAsTheOnlyInitialMember() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(get("/api/workspaces/{id}/members", workspaceId), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value("owner@example.com"))
                .andExpect(jsonPath("$[0].role").value("OWNER"));
    }

    @Test
    void addsAMemberByEmailAndGrantsThemAccess() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        TestUser teammate = registerUser("dev@example.com", "Dev");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(post("/api/workspaces/{id}/members", workspaceId), owner)
                        .content(asJson(new AddMemberRequest("dev@example.com", WorkspaceRole.MEMBER))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.displayName").value("Dev"));

        // The workspace is now visible to the new member.
        mockMvc.perform(authed(get("/api/workspaces/{id}", workspaceId), teammate))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callerRole").value("MEMBER"));
    }

    @Test
    void rejectsAddingAnUnregisteredEmail() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(post("/api/workspaces/{id}/members", workspaceId), owner)
                        .content(asJson(new AddMemberRequest("ghost@example.com", WorkspaceRole.MEMBER))))
                .andExpect(status().isNotFound());
    }

    @Test
    void aViewerCannotWriteContentButCanRead() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        TestUser viewer = registerUser("viewer@example.com", "Viewer");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");
        addMember(owner, workspaceId, "viewer@example.com", WorkspaceRole.VIEWER);

        mockMvc.perform(authed(get("/api/workspaces/{id}/documents", workspaceId), viewer))
                .andExpect(status().isOk());

        mockMvc.perform(authed(post("/api/workspaces/{id}/documents", workspaceId), viewer)
                        .content("""
                                {"title":"Notes","slug":"notes","content":"","documentType":"GENERAL"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMemberCannotAdministerTheTeam() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        TestUser member = registerUser("dev@example.com", "Dev");
        registerUser("third@example.com", "Third");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");
        addMember(owner, workspaceId, "dev@example.com", WorkspaceRole.MEMBER);

        mockMvc.perform(authed(post("/api/workspaces/{id}/members", workspaceId), member)
                        .content(asJson(new AddMemberRequest("third@example.com", WorkspaceRole.MEMBER))))
                .andExpect(status().isForbidden());
    }

    /** Privilege escalation guard: an admin must not be able to create an owner. */
    @Test
    void anAdminCannotGrantOwnership() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        TestUser admin = registerUser("admin@example.com", "Admin");
        registerUser("third@example.com", "Third");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");
        addMember(owner, workspaceId, "admin@example.com", WorkspaceRole.ADMIN);

        mockMvc.perform(authed(post("/api/workspaces/{id}/members", workspaceId), admin)
                        .content(asJson(new AddMemberRequest("third@example.com", WorkspaceRole.OWNER))))
                .andExpect(status().isForbidden());
    }

    @Test
    void changesAMembersRole() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        TestUser member = registerUser("dev@example.com", "Dev");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");
        addMember(owner, workspaceId, "dev@example.com", WorkspaceRole.VIEWER);

        mockMvc.perform(authed(put("/api/workspaces/{id}/members/{userId}", workspaceId, member.id()), owner)
                        .content(asJson(new UpdateMemberRoleRequest(WorkspaceRole.ADMIN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ADMIN"));

        // The promotion takes effect immediately.
        mockMvc.perform(authed(put("/api/workspaces/{id}", workspaceId), member)
                        .content(asJson(new UpdateWorkspaceRequest("Renamed", null, "renamed"))))
                .andExpect(status().isOk());
    }

    /** Lock-out guard, end to end. */
    @Test
    void theLastOwnerCannotLeave() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(delete("/api/workspaces/{id}/members/{userId}", workspaceId, owner.id()), owner))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("at least one owner")));
    }

    @Test
    void anOwnerMayLeaveOnceAnotherOwnerExists() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        registerUser("second@example.com", "Second Owner");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");
        addMember(owner, workspaceId, "second@example.com", WorkspaceRole.OWNER);

        mockMvc.perform(authed(delete("/api/workspaces/{id}/members/{userId}", workspaceId, owner.id()), owner))
                .andExpect(status().isNoContent());

        // Having left, the workspace is no longer visible.
        mockMvc.perform(authed(get("/api/workspaces/{id}", workspaceId), owner))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMemberMayLeaveWithoutAdminRights() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        TestUser member = registerUser("dev@example.com", "Dev");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");
        addMember(owner, workspaceId, "dev@example.com", WorkspaceRole.MEMBER);

        mockMvc.perform(authed(delete("/api/workspaces/{id}/members/{userId}", workspaceId, member.id()), member))
                .andExpect(status().isNoContent());
    }

    @Test
    void findsUsersToInvite() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        registerUser("searchable@example.com", "Searchable Person");

        mockMvc.perform(authed(get("/api/users").param("q", "Searchable"), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("searchable@example.com"));
    }

    @Test
    void rejectsATooShortUserSearch() throws Exception {
        TestUser owner = registerUser();

        mockMvc.perform(authed(get("/api/users").param("q", "a"), owner))
                .andExpect(status().isBadRequest());
    }

    private void addMember(TestUser actor, UUID workspaceId, String email, WorkspaceRole role)
            throws Exception {
        mockMvc.perform(authed(post("/api/workspaces/{id}/members", workspaceId), actor)
                        .content(asJson(new AddMemberRequest(email, role))))
                .andExpect(status().isCreated());
    }
}
