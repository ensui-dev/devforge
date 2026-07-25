package com.devforge.identity.api;

import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registersAndReturnsUsableToken() throws Exception {
        TestUser user = registerUser("dev@example.com", "Dev Example");

        mockMvc.perform(authed(get("/api/auth/me"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("dev@example.com"))
                .andExpect(jsonPath("$.displayName").value("Dev Example"));
    }

    @Test
    void normalisesEmailCaseOnRegistration() throws Exception {
        registerUser("Mixed.Case@Example.COM", "Mixed Case");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"mixed.case@example.com","password":"password123"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("mixed.case@example.com"));
    }

    @Test
    void rejectsDuplicateRegistration() throws Exception {
        registerUser("taken@example.com", "First");

        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"taken@example.com","displayName":"Second","password":"password123"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsWrongPasswordWithoutRevealingWhy() throws Exception {
        registerUser("real@example.com", "Real User");

        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"real@example.com","password":"wrong-password"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void givesSameErrorForUnknownAccount() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"nobody@example.com","password":"password123"}"""))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    void rejectsShortPasswordWithFieldDetail() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"short@example.com","displayName":"Short","password":"abc"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void requiresAuthenticationForProtectedEndpoints() throws Exception {
        mockMvc.perform(get("/api/workspaces"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsGarbageToken() throws Exception {
        mockMvc.perform(get("/api/workspaces").header("Authorization", "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }
}
