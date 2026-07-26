package com.devforge.workspace.api;

import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Workspace slugs are namespaced by their owner's handle.
 *
 * <p>They were unique across the whole instance, so the first team to take a common
 * name blocked every other team from ever using it. These tests pin the behaviour
 * that replaced it.
 */
class WorkspaceNamespaceIntegrationTest extends AbstractIntegrationTest {

    private void publish(TestUser admin, UUID workspaceId) throws Exception {
        mockMvc.perform(authed(put("/api/workspaces/{id}/publication", workspaceId), admin)
                        .content("""
                                {"published":true}"""))
                .andExpect(status().isOk());
    }

    /** The point of the whole change. */
    @Test
    void twoOwnersCanEachHaveAWorkspaceWithTheSameSlug() throws Exception {
        TestUser first = registerUser("first@example.com", "First");
        TestUser second = registerUser("second@example.com", "Second");

        createWorkspace(first, "Nokia", "nokia");
        createWorkspace(second, "Nokia", "nokia");

        // And each is reachable at its own address.
        mockMvc.perform(authed(get("/api/workspaces"), first))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("nokia"));
        mockMvc.perform(authed(get("/api/workspaces"), second))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].slug").value("nokia"));
    }

    @Test
    void oneOwnerStillCannotReuseASlugTwice() throws Exception {
        TestUser owner = registerUser();
        createWorkspace(owner, "Nokia", "nokia");

        mockMvc.perform(authed(post("/api/workspaces"), owner)
                        .content("""
                                {"name":"Nokia Again","slug":"nokia"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already have a workspace")));
    }

    @Test
    void sameSlugFromTwoOwnersPublishesToDistinctAddresses() throws Exception {
        TestUser first = registerUser("first@example.com", "First");
        TestUser second = registerUser("second@example.com", "Second");

        UUID a = createWorkspace(first, "Nokia", "nokia");
        UUID b = createWorkspace(second, "Nokia", "nokia");
        createDocument(first, a, "Alpha", "overview", "first team", "GENERAL");
        createDocument(second, b, "Beta", "overview", "second team", "GENERAL");
        publish(first, a);
        publish(second, b);

        assertThat(first.handle()).isNotEqualTo(second.handle());

        // Same slug, same document slug, different content — kept apart by namespace.
        mockMvc.perform(get("/api/public/docs/{h}/{ws}/{doc}", first.handle(), "nokia", "overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("first team"));
        mockMvc.perform(get("/api/public/docs/{h}/{ws}/{doc}", second.handle(), "nokia", "overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("second team"));
    }

    @Test
    void reportsTheNamespacedPublicPath() throws Exception {
        TestUser owner = registerUser("dana@example.com", "Dana");
        UUID workspaceId = createWorkspace(owner, "Nokia", "nokia");
        createDocument(owner, workspaceId, "Overview", "overview", "body", "GENERAL");

        mockMvc.perform(authed(put("/api/workspaces/{id}/publication", workspaceId), owner)
                        .content("""
                                {"published":true}"""))
                .andExpect(jsonPath("$.publicPath").value("/docs/dana/nokia"));
    }

    @Test
    void listsEverythingOneOwnerHasPublished() throws Exception {
        TestUser owner = registerUser("dana@example.com", "Dana");
        UUID published = createWorkspace(owner, "Public", "public-one");
        createDocument(owner, published, "Overview", "overview", "body", "GENERAL");
        publish(owner, published);

        UUID hidden = createWorkspace(owner, "Hidden", "hidden-one");
        createDocument(owner, hidden, "Notes", "notes", "body", "GENERAL");

        mockMvc.perform(get("/api/public/docs/{handle}", "dana"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.handle").value("dana"))
                .andExpect(jsonPath("$.workspaces.length()").value(1))
                .andExpect(jsonPath("$.workspaces[0].slug").value("public-one"))
                .andExpect(jsonPath("$.workspaces[0].publicPath").value("/docs/dana/public-one"));
    }

    @Test
    void anUnknownHandleListsNothing() throws Exception {
        mockMvc.perform(get("/api/public/docs/{handle}", "nobody-here"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaces.length()").value(0))
                .andExpect(jsonPath("$.movedTo").doesNotExist());
    }

    /**
     * Addresses used to be /docs/{slug}. Those links should keep working, so an
     * unambiguous one resolves to its new home rather than simply breaking.
     */
    @Test
    void resolvesALegacySlugToItsNamespacedPath() throws Exception {
        TestUser owner = registerUser("dana@example.com", "Dana");
        UUID workspaceId = createWorkspace(owner, "Nokia", "nokia");
        createDocument(owner, workspaceId, "Overview", "overview", "body", "GENERAL");
        publish(owner, workspaceId);

        mockMvc.perform(get("/api/public/docs/{segment}", "nokia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movedTo").value("/docs/dana/nokia"));
    }

    /** With two owners publishing the same slug there is no single right answer. */
    @Test
    void doesNotGuessWhenALegacySlugIsAmbiguous() throws Exception {
        TestUser first = registerUser("first@example.com", "First");
        TestUser second = registerUser("second@example.com", "Second");
        UUID a = createWorkspace(first, "Nokia", "nokia");
        UUID b = createWorkspace(second, "Nokia", "nokia");
        createDocument(first, a, "Overview", "overview", "body", "GENERAL");
        createDocument(second, b, "Overview", "overview", "body", "GENERAL");
        publish(first, a);
        publish(second, b);

        mockMvc.perform(get("/api/public/docs/{segment}", "nokia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movedTo").doesNotExist());
    }

    @Test
    void doesNotResolveALegacySlugForAPrivateWorkspace() throws Exception {
        TestUser owner = registerUser("dana@example.com", "Dana");
        UUID workspaceId = createWorkspace(owner, "Nokia", "nokia");
        createDocument(owner, workspaceId, "Overview", "overview", "body", "GENERAL");

        mockMvc.perform(get("/api/public/docs/{segment}", "nokia"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.movedTo").doesNotExist());
    }

    @Test
    void derivesAHandleFromTheEmailAddress() throws Exception {
        TestUser owner = registerUser("Ada.Lovelace@Example.com", "Ada");

        assertThat(owner.handle()).isEqualTo("ada-lovelace");

        mockMvc.perform(authed(get("/api/auth/me"), owner))
                .andExpect(jsonPath("$.handle").value("ada-lovelace"));
    }

    @Test
    void suffixesAHandleThatIsAlreadyTaken() throws Exception {
        TestUser first = registerUser("dana@one.example", "Dana One");
        TestUser second = registerUser("dana@two.example", "Dana Two");

        assertThat(first.handle()).isEqualTo("dana");
        assertThat(second.handle()).isEqualTo("dana-2");
    }
}
