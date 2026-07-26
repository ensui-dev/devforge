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
 * The accounts an operator manages directly.
 *
 * <p>These exist because two settings would otherwise be traps. {@code CLOSED}
 * registration would leave an operator with no way to add anyone, and a single
 * administrator would be a deployment one lost password away from being
 * unconfigurable forever.
 */
class InstanceAccountsIntegrationTest extends AbstractIntegrationTest {

    private static final String CLOSED_SETTINGS = """
            {
              "name": "Acme Docs",
              "registrationMode": "CLOSED",
              "publicDocsEnabled": true,
              "handbookPath": "",
              "publicBaseUrl": ""
            }""";

    private TestUser instanceAdmin() throws Exception {
        TestUser admin = registerUser("ops@acme.test", "Acme Ops");
        makeInstanceAdmin(admin);
        return admin;
    }

    /** The point of the endpoint: a closed instance is still populatable. */
    @Test
    void anOperatorCanCreateAccountsOnAClosedInstance() throws Exception {
        TestUser admin = instanceAdmin();
        mockMvc.perform(authed(put("/api/instance"), admin).content(CLOSED_SETTINGS))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"dev@acme.test","displayName":"Dev","password":"password123"}"""))
                .andExpect(status().isForbidden());

        mockMvc.perform(authed(post("/api/instance/users"), admin)
                        .content("""
                                {"email":"dev@acme.test","displayName":"Dev",
                                 "password":"password123","instanceAdmin":false}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("dev@acme.test"))
                .andExpect(jsonPath("$.handle").value("dev"))
                .andExpect(jsonPath("$.instanceAdmin").value(false));

        // And the account works, even though registration is shut.
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"dev@acme.test","password":"password123"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void ordinaryUsersCannotCreateAccountsOrListOperators() throws Exception {
        TestUser ordinary = registerUser("dev@acme.test", "Dev");

        mockMvc.perform(authed(post("/api/instance/users"), ordinary)
                        .content("""
                                {"email":"mine@acme.test","displayName":"Mine",
                                 "password":"password123","instanceAdmin":true}"""))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/instance/admins"), ordinary))
                .andExpect(status().isForbidden());
    }

    @Test
    void appointingASecondOperatorGivesThemTheSettings() throws Exception {
        TestUser admin = instanceAdmin();
        TestUser colleague = registerUser("second@acme.test", "Second");

        mockMvc.perform(authed(get("/api/instance"), colleague))
                .andExpect(status().isForbidden());

        mockMvc.perform(authed(put("/api/instance/users/{id}/admin", colleague.id()), admin)
                        .content("""
                                {"instanceAdmin":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instanceAdmin").value(true));

        mockMvc.perform(authed(get("/api/instance"), colleague))
                .andExpect(status().isOk());
        mockMvc.perform(authed(get("/api/instance/admins"), admin))
                .andExpect(jsonPath("$.length()").value(2));
    }

    /** The lockout this guard exists to prevent. */
    @Test
    void refusesToRemoveTheLastOperator() throws Exception {
        TestUser admin = instanceAdmin();

        mockMvc.perform(authed(put("/api/instance/users/{id}/admin", admin.id()), admin)
                        .content("""
                                {"instanceAdmin":false}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("only instance administrator")));

        // Still an administrator, so the instance is still configurable.
        mockMvc.perform(authed(get("/api/instance"), admin))
                .andExpect(status().isOk());
    }

    @Test
    void stepsDownOnceSomeoneElseCanTakeOver() throws Exception {
        TestUser admin = instanceAdmin();
        TestUser successor = registerUser("successor@acme.test", "Successor");

        mockMvc.perform(authed(put("/api/instance/users/{id}/admin", successor.id()), admin)
                        .content("""
                                {"instanceAdmin":true}"""))
                .andExpect(status().isOk());
        mockMvc.perform(authed(put("/api/instance/users/{id}/admin", admin.id()), admin)
                        .content("""
                                {"instanceAdmin":false}"""))
                .andExpect(status().isOk());

        mockMvc.perform(authed(get("/api/instance"), admin))
                .andExpect(status().isForbidden());
        mockMvc.perform(authed(get("/api/instance"), successor))
                .andExpect(status().isOk());
    }

    @Test
    void reportsAnUnknownAccountRatherThanFailingQuietly() throws Exception {
        TestUser admin = instanceAdmin();

        mockMvc.perform(authed(put("/api/instance/users/{id}/admin", UUID.randomUUID()), admin)
                        .content("""
                                {"instanceAdmin":true}"""))
                .andExpect(status().isNotFound());
    }

    /** The client shows an instance-settings link only when this says so. */
    @Test
    void tellsTheSignedInUserWhetherTheyAdministerTheInstance() throws Exception {
        TestUser admin = instanceAdmin();
        TestUser ordinary = registerUser("dev@acme.test", "Dev");

        mockMvc.perform(authed(get("/api/auth/me"), admin))
                .andExpect(jsonPath("$.instanceAdmin").value(true));
        mockMvc.perform(authed(get("/api/auth/me"), ordinary))
                .andExpect(jsonPath("$.instanceAdmin").value(false));
    }

    /** Other people's admin status is not the member picker's business. */
    @Test
    void doesNotRevealOperatorsThroughTheUserDirectory() throws Exception {
        TestUser admin = instanceAdmin();

        mockMvc.perform(authed(get("/api/users?q=ops"), admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("ops@acme.test"))
                .andExpect(jsonPath("$[0].instanceAdmin").doesNotExist());
    }
}
