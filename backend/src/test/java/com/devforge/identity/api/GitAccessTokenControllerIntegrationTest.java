package com.devforge.identity.api;

import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Managing the credentials git uses.
 *
 * <p>The property worth testing is negative: the secret exists in exactly one
 * response and can never be read again, and one account's tokens are invisible to
 * every other.
 */
class GitAccessTokenControllerIntegrationTest extends AbstractIntegrationTest {

    private record NewToken(String name, Integer expiresInDays) {
    }

    private String issue(TestUser user, String name) throws Exception {
        return objectMapper.readTree(mockMvc.perform(authed(
                        post("/api/me/git-tokens")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(asJson(new NewToken(name, null))), user))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("secret").asText();
    }

    @Test
    void issuesATokenAndShowsTheSecretOnce() throws Exception {
        TestUser user = registerUser("ada@acme.test", "Ada");

        String secret = issue(user, "laptop");

        assertThat(secret).startsWith("dfg_");

        // The listing knows the token, and does not know its secret.
        String listing = mockMvc.perform(authed(get("/api/me/git-tokens"), user))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("laptop"))
                .andExpect(jsonPath("$[0].expired").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(listing).doesNotContain(secret);
    }

    /** Enough to recognise which token a machine is using, not enough to use it. */
    @Test
    void showsAHintRatherThanTheToken() throws Exception {
        TestUser user = registerUser("ada@acme.test", "Ada");
        String secret = issue(user, "laptop");

        mockMvc.perform(authed(get("/api/me/git-tokens"), user))
                .andExpect(jsonPath("$[0].hint").value(secret.substring(0, 12)));

        assertThat(secret.length()).isGreaterThan(30);
    }

    @Test
    void revokesAToken() throws Exception {
        TestUser user = registerUser("ada@acme.test", "Ada");
        issue(user, "laptop");
        String id = objectMapper.readTree(mockMvc.perform(authed(get("/api/me/git-tokens"), user))
                        .andReturn().getResponse().getContentAsString())
                .get(0).get("id").asText();

        mockMvc.perform(authed(delete("/api/me/git-tokens/{id}", id), user))
                .andExpect(status().isNoContent());

        mockMvc.perform(authed(get("/api/me/git-tokens"), user))
                .andExpect(jsonPath("$.length()").value(0));
    }

    /** Someone else's token is reported absent; saying "forbidden" would confirm it. */
    @Test
    void cannotSeeOrRevokeAnotherAccountsTokens() throws Exception {
        TestUser ada = registerUser("ada@acme.test", "Ada");
        TestUser grace = registerUser("grace@acme.test", "Grace");
        issue(ada, "ada's laptop");
        String id = objectMapper.readTree(mockMvc.perform(authed(get("/api/me/git-tokens"), ada))
                        .andReturn().getResponse().getContentAsString())
                .get(0).get("id").asText();

        mockMvc.perform(authed(get("/api/me/git-tokens"), grace))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(authed(delete("/api/me/git-tokens/{id}", id), grace))
                .andExpect(status().isNotFound());

        // And Ada's token is still there.
        mockMvc.perform(authed(get("/api/me/git-tokens"), ada))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void refusesATokenWithNoName() throws Exception {
        TestUser user = registerUser("ada@acme.test", "Ada");

        mockMvc.perform(authed(post("/api/me/git-tokens")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(asJson(new NewToken("  ", null))), user))
                .andExpect(status().isBadRequest());
    }

    @Test
    void setsAnExpiryWhenAskedFor() throws Exception {
        TestUser user = registerUser("ada@acme.test", "Ada");

        mockMvc.perform(authed(post("/api/me/git-tokens")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content(asJson(new NewToken("ci", 30))), user))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token.expiresAt").isNotEmpty());
    }

    @Test
    void requiresAnAccount() throws Exception {
        mockMvc.perform(get("/api/me/git-tokens")).andExpect(status().isUnauthorized());
    }
}
