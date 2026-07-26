package com.devforge.instance.api;

import com.devforge.support.AbstractIntegrationTest;
import com.devforge.support.DatabaseCleaner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * First-run setup, which is how someone self-hosting DevForge claims their
 * instance.
 *
 * <p>The property that matters most: the endpoint closes permanently once setup
 * completes. A deployment briefly reachable before its operator finishes must not
 * be claimable by whoever gets there second, and the endpoint must never be usable
 * to mint an administrator on a running instance.
 */
class SetupControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private DatabaseCleaner databaseCleaner;

    private static final String SETUP_BODY = """
            {
              "instance": {
                "name": "Acme Docs",
                "tagline": "How Acme builds things",
                "logoMark": "◆",
                "accentColor": "#7a3ea1",
                "registrationMode": "OPEN",
                "publicDocsEnabled": true,
                "handbookPath": "",
                "publicBaseUrl": "https://docs.acme.test"
              },
              "admin": {
                "email": "ops@acme.test",
                "displayName": "Acme Ops",
                "password": "password123"
              }
            }""";

    @BeforeEach
    void unconfigureInstance() {
        // The shared cleaner leaves a configured instance behind so other tests can
        // register users; these tests need one that has never been set up.
        databaseCleaner.resetSetup();
    }

    @Test
    void reportsThatANewInstanceNeedsSetup() throws Exception {
        mockMvc.perform(get("/api/public/instance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false));
    }

    /** Nobody can register their way into an instance before its operator claims it. */
    @Test
    void refusesRegistrationBeforeSetup() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"squatter@example.com","displayName":"Squatter",
                                 "password":"password123"}"""))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("has not been set up")));
    }

    @Test
    void configuresTheInstanceAndCreatesItsAdministrator() throws Exception {
        mockMvc.perform(post("/api/setup").contentType("application/json").content(SETUP_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance.configured").value(true))
                .andExpect(jsonPath("$.instance.name").value("Acme Docs"))
                .andExpect(jsonPath("$.instance.accentColor").value("#7a3ea1"))
                .andExpect(jsonPath("$.adminEmail").value("ops@acme.test"));

        // The branding is what an unauthenticated visitor now sees.
        mockMvc.perform(get("/api/public/instance"))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.name").value("Acme Docs"))
                .andExpect(jsonPath("$.logoMark").value("◆"));
    }

    @Test
    void theAdministratorCanSignInAndReadTheSettings() throws Exception {
        mockMvc.perform(post("/api/setup").contentType("application/json").content(SETUP_BODY))
                .andExpect(status().isOk());

        String login = mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"ops@acme.test","password":"password123"}"""))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(login).get("accessToken").asText();

        mockMvc.perform(get("/api/instance").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instance.name").value("Acme Docs"))
                .andExpect(jsonPath("$.publicBaseUrl").value("https://docs.acme.test"))
                .andExpect(jsonPath("$.setupCompletedAt").exists());
    }

    /** The security property: setup is one-shot. */
    @Test
    void refusesToRunTwice() throws Exception {
        mockMvc.perform(post("/api/setup").contentType("application/json").content(SETUP_BODY))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/setup")
                        .contentType("application/json")
                        .content(SETUP_BODY.replace("ops@acme.test", "attacker@evil.test")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("already been set up")));

        // And the second account was not created.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"attacker@evil.test","password":"password123"}"""))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAWeakAdminPasswordWithFieldDetail() throws Exception {
        mockMvc.perform(post("/api/setup")
                        .contentType("application/json")
                        .content(SETUP_BODY.replace("\"password123\"", "\"short\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['admin.password']").exists());
    }

    @Test
    void rejectsAnAccentThatIsNotAHexColour() throws Exception {
        mockMvc.perform(post("/api/setup")
                        .contentType("application/json")
                        .content(SETUP_BODY.replace("#7a3ea1", "purple")))
                .andExpect(status().isBadRequest())
                // Nested payloads produce a dotted key, which is what a client needs
                // to put the message beside the right input.
                .andExpect(jsonPath("$.fieldErrors['instance.accentColor']").exists());
    }

    @Test
    void refusesRestrictedRegistrationWithNoDomainsListed() throws Exception {
        mockMvc.perform(post("/api/setup")
                        .contentType("application/json")
                        .content(SETUP_BODY.replace("\"registrationMode\": \"OPEN\"",
                                "\"registrationMode\": \"RESTRICTED\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("at least one email domain")));
    }
}
