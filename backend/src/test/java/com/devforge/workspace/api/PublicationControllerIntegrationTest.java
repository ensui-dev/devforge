package com.devforge.workspace.api;

import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Who may publish a workspace's documentation, and what publishing reports. */
class PublicationControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void reportsPrivateByDefault() throws Exception {
        TestUser owner = registerUser();
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(get("/api/workspaces/{id}/publication", workspaceId), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(false))
                .andExpect(jsonPath("$.publicPath").doesNotExist());
    }

    @Test
    void reportsThePublicPathOncePublished() throws Exception {
        TestUser owner = registerUser();
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");
        createDocument(owner, workspaceId, "Welcome", "welcome", "body", "GENERAL");

        mockMvc.perform(authed(put("/api/workspaces/{id}/publication", workspaceId), owner)
                        .content("""
                                {"published":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.published").value(true))
                .andExpect(jsonPath("$.publishedAt").exists())
                .andExpect(jsonPath("$.publicPath").value("/docs/" + owner.handle() + "/platform"))
                .andExpect(jsonPath("$.publicPages").value(1))
                .andExpect(jsonPath("$.internalPages").value(0));
    }

    @Test
    void countsWhatWouldBeExposed() throws Exception {
        TestUser owner = registerUser();
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");
        createDocument(owner, workspaceId, "Public", "public-page", "body", "GENERAL");
        mockMvc.perform(authed(post("/api/workspaces/{id}/documents", workspaceId), owner)
                        .content("""
                                {"title":"Internal","slug":"internal-page","content":"",
                                 "documentType":"GENERAL","internal":true}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(authed(get("/api/workspaces/{id}/publication", workspaceId), owner))
                .andExpect(jsonPath("$.publicPages").value(1))
                .andExpect(jsonPath("$.internalPages").value(1));
    }

    /** Publishing nothing would serve an empty site, which reads as broken. */
    @Test
    void refusesToPublishWithNoPublicPages() throws Exception {
        TestUser owner = registerUser();
        UUID workspaceId = createWorkspace(owner, "Empty", "empty-team");

        mockMvc.perform(authed(put("/api/workspaces/{id}/publication", workspaceId), owner)
                        .content("""
                                {"published":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("at least one document")));
    }

    @Test
    void publishingIsIdempotentAndKeepsTheOriginalDate() throws Exception {
        TestUser owner = registerUser();
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");
        createDocument(owner, workspaceId, "Welcome", "welcome", "body", "GENERAL");

        String first = mockMvc.perform(authed(
                        put("/api/workspaces/{id}/publication", workspaceId), owner)
                        .content("""
                                {"published":true}"""))
                .andReturn().getResponse().getContentAsString();
        String again = mockMvc.perform(authed(
                        put("/api/workspaces/{id}/publication", workspaceId), owner)
                        .content("""
                                {"published":true}"""))
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions
                .assertThat(objectMapper.readTree(again).get("publishedAt").asText())
                .isEqualTo(objectMapper.readTree(first).get("publishedAt").asText());
    }

    @Test
    void aMemberCanSeeThatTheWorkspaceIsPublishedButCannotChangeIt() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        TestUser member = registerUser("dev@example.com", "Dev");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");
        createDocument(owner, workspaceId, "Welcome", "welcome", "body", "GENERAL");
        mockMvc.perform(authed(post("/api/workspaces/{id}/members", workspaceId), owner)
                        .content("""
                                {"email":"dev@example.com","role":"MEMBER"}"""))
                .andExpect(status().isCreated());

        // Anyone writing in a published workspace needs to know that it is published.
        mockMvc.perform(authed(get("/api/workspaces/{id}/publication", workspaceId), member))
                .andExpect(status().isOk());

        mockMvc.perform(authed(put("/api/workspaces/{id}/publication", workspaceId), member)
                        .content("""
                                {"published":true}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void hidesPublicationFromNonMembers() throws Exception {
        TestUser owner = registerUser("owner@example.com", "Owner");
        TestUser stranger = registerUser("stranger@example.com", "Stranger");
        UUID workspaceId = createWorkspace(owner, "Platform", "platform");

        mockMvc.perform(authed(get("/api/workspaces/{id}/publication", workspaceId), stranger))
                .andExpect(status().isNotFound());
    }
}
