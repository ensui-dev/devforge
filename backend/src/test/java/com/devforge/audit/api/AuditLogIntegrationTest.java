package com.devforge.audit.api;

import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The audit log: who changed what, and when.
 *
 * <p>Before this existed a change left only {@code updated_at} and a version
 * number, so you could tell that something had moved but never who moved it.
 */
class AuditLogIntegrationTest extends AbstractIntegrationTest {

    @Test
    void recordsWhoCreatedAWorkspaceAndWhatItWasCalled() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(get("/api/workspaces/{w}/activity", ws), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].action").value("WORKSPACE_CREATED"))
                .andExpect(jsonPath("$.content[0].targetLabel").value("Platform"))
                .andExpect(jsonPath("$.content[0].actorLabel").value(
                        org.hamcrest.Matchers.containsString("owner@acme.test")))
                .andExpect(jsonPath("$.content[0].detail.slug").value("platform"));
    }

    /** Newest first, and every kind of change lands in the same place. */
    @Test
    void collectsEveryKindOfChangeInOneTimeline() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        TestUser member = registerUser("member@acme.test", "Member");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "Design", "design", "body", "ARCHITECTURE");
        addMember(owner, ws, member.email(), "MEMBER");

        mockMvc.perform(authed(delete("/api/workspaces/{w}/documents/{d}", ws, doc), owner))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/workspaces/{w}/activity", ws), owner))
                .andExpect(jsonPath("$.content[0].action").value("DOCUMENT_DELETED"))
                .andExpect(jsonPath("$.content[1].action").value("MEMBER_ADDED"))
                .andExpect(jsonPath("$.content[2].action").value("DOCUMENT_CREATED"))
                .andExpect(jsonPath("$.content[3].action").value("WORKSPACE_CREATED"));
    }

    /** An "updated" entry that listed every field would bury the one that moved. */
    @Test
    void recordsOnlyTheFieldsThatActuallyChanged() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        UUID doc = createDocument(owner, ws, "Design", "design", "body", "ARCHITECTURE");

        mockMvc.perform(authed(put("/api/workspaces/{w}/documents/{d}", ws, doc), owner)
                        .content("""
                                {"title":"Renamed","slug":"design","content":"body",
                                 "documentType":"ARCHITECTURE","internal":false}"""))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/workspaces/{w}/activity?action=DOCUMENT_UPDATED", ws), owner))
                .andExpect(jsonPath("$.content[0].detail.title.from").value("Design"))
                .andExpect(jsonPath("$.content[0].detail.title.to").value("Renamed"))
                // The slug and type did not move, so they are not in the entry.
                .andExpect(jsonPath("$.content[0].detail.slug").doesNotExist())
                .andExpect(jsonPath("$.content[0].detail.documentType").doesNotExist());
    }

    @Test
    void recordsRoleChangesWithBothRoles() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        TestUser member = registerUser("member@acme.test", "Member");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        addMember(owner, ws, member.email(), "VIEWER");

        mockMvc.perform(authed(put("/api/workspaces/{w}/members/{u}", ws, member.id()), owner)
                        .content("""
                                {"role":"ADMIN"}"""))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/workspaces/{w}/activity?action=MEMBER_ROLE_CHANGED", ws), owner))
                .andExpect(jsonPath("$.content[0].detail.role.from").value("VIEWER"))
                .andExpect(jsonPath("$.content[0].detail.role.to").value("ADMIN"))
                .andExpect(jsonPath("$.content[0].targetLabel").value("member@acme.test"));
    }

    @Test
    void hidesAWorkspacesActivityFromNonMembers() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        TestUser outsider = registerUser("outsider@acme.test", "Outsider");
        UUID ws = createWorkspace(owner, "Platform", "platform");

        // 404, not 403: the same answer a non-member gets for the workspace itself.
        mockMvc.perform(authed(get("/api/workspaces/{w}/activity", ws), outsider))
                .andExpect(status().isNotFound());
    }

    @Test
    void showsTheInstanceWideLogOnlyToOperators() throws Exception {
        TestUser ordinary = registerUser("dev@acme.test", "Dev");
        TestUser admin = registerUser("ops@acme.test", "Ops");
        makeInstanceAdmin(admin);

        mockMvc.perform(authed(get("/api/instance/activity"), ordinary))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/instance/activity"), admin))
                .andExpect(status().isOk());
    }

    /**
     * Audit rows carry no foreign key to workspaces, so deleting a workspace
     * cannot delete the evidence that it happened. The entries are simply no
     * longer reachable through the workspace's own endpoint, which now 404s.
     */
    @Test
    void keepsTheRecordOfADeletedWorkspace() throws Exception {
        TestUser admin = registerUser("ops@acme.test", "Ops");
        makeInstanceAdmin(admin);
        UUID ws = createWorkspace(admin, "Doomed", "doomed");

        mockMvc.perform(authed(delete("/api/workspaces/{w}", ws), admin))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/instance/activity?action=WORKSPACE_DELETED"), admin))
                .andExpect(jsonPath("$.content[0].targetLabel").value("Doomed"))
                .andExpect(jsonPath("$.content[0].workspaceId").value(ws.toString()));

        // ...and the workspace itself is genuinely gone.
        mockMvc.perform(authed(get("/api/workspaces/{w}/activity", ws), admin))
                .andExpect(status().isNotFound());
    }

    /** Publishing changes who can read a workspace, so it is worth recording. */
    @Test
    void recordsPublishingAndUnpublishing() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID ws = createWorkspace(owner, "Platform", "platform");
        createDocument(owner, ws, "Welcome", "welcome", "body", "GENERAL");

        mockMvc.perform(authed(put("/api/workspaces/{w}/publication", ws), owner)
                        .content("""
                                {"published":true}"""))
                .andExpect(status().isOk());
        mockMvc.perform(authed(put("/api/workspaces/{w}/publication", ws), owner)
                        .content("""
                                {"published":false}"""))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/workspaces/{w}/activity", ws), owner))
                .andExpect(jsonPath("$.content[0].action").value("WORKSPACE_UNPUBLISHED"))
                .andExpect(jsonPath("$.content[1].action").value("WORKSPACE_PUBLISHED"));
    }

    /** Who holds the keys to the instance is the log's most consequential entry. */
    @Test
    void recordsInstanceAdministrationChanges() throws Exception {
        TestUser admin = registerUser("ops@acme.test", "Ops");
        TestUser successor = registerUser("second@acme.test", "Second");
        makeInstanceAdmin(admin);

        mockMvc.perform(authed(put("/api/instance/users/{id}/admin", successor.id()), admin)
                        .content("""
                                {"instanceAdmin":true}"""))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/instance/activity?action=INSTANCE_ADMIN_GRANTED"), admin))
                .andExpect(jsonPath("$.content[0].targetLabel").value("second@acme.test"))
                .andExpect(jsonPath("$.content[0].actorLabel").value(
                        org.hamcrest.Matchers.containsString("ops@acme.test")))
                .andExpect(jsonPath("$.content[0].detail.self").value(false));
    }

    @Test
    void doesNotLeakOneWorkspacesActivityIntoAnothers() throws Exception {
        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID first = createWorkspace(owner, "First", "first");
        UUID second = createWorkspace(owner, "Second", "second");
        createDocument(owner, first, "Only here", "only-here", "body", "GENERAL");

        mockMvc.perform(authed(get("/api/workspaces/{w}/activity", second), owner))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].action").value("WORKSPACE_CREATED"))
                .andExpect(jsonPath("$.content[0].targetLabel").value("Second"));
    }
}
