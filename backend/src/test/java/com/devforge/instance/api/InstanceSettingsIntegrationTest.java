package com.devforge.instance.api;

import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Instance settings after setup: who may change them, and what they control.
 *
 * <p>The shared test fixture leaves an instance that is configured, open, and has
 * no administrator, so each test promotes the account it needs.
 */
class InstanceSettingsIntegrationTest extends AbstractIntegrationTest {

    private static final String SETTINGS = """
            {
              "name": "Acme Docs",
              "tagline": "How Acme builds things",
              "logoMark": "◆",
              "registrationMode": "%s",
              "allowedEmailDomains": "%s",
              "publicDocsEnabled": %s,
              "handbookPath": "",
              "publicBaseUrl": ""
            }""";

    private static String settings(String mode, String domains, boolean publicDocs) {
        return SETTINGS.formatted(mode, domains, publicDocs);
    }

    /** Promotes an account, since the fixture's instance has no admin of its own. */
    private TestUser instanceAdmin() throws Exception {
        TestUser admin = registerUser("ops@acme.test", "Acme Ops");
        makeInstanceAdmin(admin);
        return admin;
    }

    @Test
    void onlyAnInstanceAdminCanReadOrChangeTheSettings() throws Exception {
        TestUser ordinary = registerUser("dev@acme.test", "Dev");

        mockMvc.perform(authed(get("/api/instance"), ordinary))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(put("/api/instance"), ordinary)
                        .content(settings("OPEN", "", true)))
                .andExpect(status().isForbidden());
    }

    @Test
    void anInstanceAdminCanRebrandTheDeployment() throws Exception {
        TestUser admin = instanceAdmin();

        mockMvc.perform(authed(put("/api/instance"), admin)
                        .content(settings("OPEN", "", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance.name").value("Acme Docs"));

        // Branding is what an unauthenticated visitor sees, so it must change there.
        mockMvc.perform(get("/api/public/instance"))
                .andExpect(jsonPath("$.name").value("Acme Docs"))
                .andExpect(jsonPath("$.logoMark").value("◆"));
    }

    @Test
    void restrictedRegistrationAcceptsOnlyListedDomains() throws Exception {
        TestUser admin = instanceAdmin();
        mockMvc.perform(authed(put("/api/instance"), admin)
                        .content(settings("RESTRICTED", "acme.test", true)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"newcomer@acme.test","displayName":"Newcomer",
                                 "password":"password123"}"""))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"outsider@other.test","displayName":"Outsider",
                                 "password":"password123"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("acme.test")));
    }

    @Test
    void closedRegistrationRefusesEveryone() throws Exception {
        TestUser admin = instanceAdmin();
        mockMvc.perform(authed(put("/api/instance"), admin)
                        .content(settings("CLOSED", "", true)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"anyone@acme.test","displayName":"Anyone",
                                 "password":"password123"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("not accepting new accounts")));
    }

    /** A company deployment may want no public documentation at all. */
    @Test
    void switchingPublicDocumentationOffBlocksPublishing() throws Exception {
        TestUser admin = instanceAdmin();
        UUID workspaceId = createWorkspace(admin, "Platform", "platform");
        createDocument(admin, workspaceId, "Welcome", "welcome", "body", "GENERAL");

        mockMvc.perform(authed(put("/api/instance"), admin)
                        .content(settings("OPEN", "", false)))
                .andExpect(status().isOk());

        mockMvc.perform(authed(put("/api/workspaces/{id}/publication", workspaceId), admin)
                        .content("""
                                {"published":true}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("switched off for this instance")));
    }

    /** Switching it off must also take already-published sites offline. */
    @Test
    void switchingItOffHidesDocumentationThatWasAlreadyPublished() throws Exception {
        TestUser admin = instanceAdmin();
        UUID workspaceId = createWorkspace(admin, "Platform", "platform");
        createDocument(admin, workspaceId, "Welcome", "welcome", "body", "GENERAL");
        mockMvc.perform(authed(put("/api/workspaces/{id}/publication", workspaceId), admin)
                        .content("""
                                {"published":true}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/docs/{h}/{s}", admin.handle(), "platform"))
                .andExpect(status().isOk());

        mockMvc.perform(authed(put("/api/instance"), admin)
                        .content(settings("OPEN", "", false)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/docs/{h}/{s}", admin.handle(), "platform"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/public/docs"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void doesNotExposeOperationalSettingsToVisitors() throws Exception {
        TestUser admin = instanceAdmin();
        mockMvc.perform(authed(put("/api/instance"), admin)
                        .content(settings("OPEN", "", true).replace(
                                "\"publicBaseUrl\": \"\"", "\"publicBaseUrl\": \"https://internal.acme.test\"")))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/public/instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicBaseUrl").doesNotExist());
    }
}
