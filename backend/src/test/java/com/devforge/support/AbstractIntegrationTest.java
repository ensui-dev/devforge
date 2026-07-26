package com.devforge.support;

import com.devforge.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base class for REST integration tests.
 *
 * <p>Note the plain {@code @SpringBootTest} with no {@code classes} attribute.
 * The original tests declared {@code classes = TestDevforgeBackendApplication.class}
 * — a class carrying only a {@code main} method and no
 * {@code @SpringBootApplication} — so component scanning never ran, no controller
 * was registered, and every request returned 404. Letting Spring locate the real
 * {@code @SpringBootConfiguration} is what makes these tests exercise the
 * application.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    /** Spring Boot 4 ships Jackson 3, so the configured mapper is a {@link JsonMapper}. */
    @Autowired
    protected JsonMapper objectMapper;

    @Autowired
    private DatabaseCleaner databaseCleaner;

    @Autowired
    private InstanceAdminSupport instanceAdminSupport;

    @BeforeEach
    void resetDatabase() {
        databaseCleaner.clean();
    }

    /**
     * Registers a user and returns their identity plus a usable bearer token.
     *
     * <p>Tests go through the real registration endpoint rather than inserting
     * rows, so password encoding and token issuance are covered incidentally by
     * every test that needs a caller.
     */
    protected TestUser registerUser(String email, String displayName) throws Exception {
        String body = objectMapper.writeValueAsString(new RegistrationPayload(email, displayName, "password123"));

        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return new TestUser(
                UUID.fromString(json.get("user").get("id").asText()),
                email,
                displayName,
                // The handle namespaces this user's workspaces, so public paths in
                // tests are built from it rather than hardcoded.
                json.get("user").get("handle").asText(),
                json.get("accessToken").asText()
        );
    }

    /** Convenience for the common case of one arbitrary authenticated user. */
    protected TestUser registerUser() throws Exception {
        String unique = UUID.randomUUID().toString().substring(0, 8);
        return registerUser("user-%s@example.com".formatted(unique), "User " + unique);
    }

    protected String asJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** Attaches the bearer token and a JSON content type in one call. */
    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder, TestUser user) {
        return builder
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON);
    }

    /** Posts a JSON body, asserts 201, and returns the parsed response. */
    protected JsonNode postForCreated(TestUser user, String path, Object body, Object... uriVars)
            throws Exception {
        String response = mockMvc.perform(authed(post(path, uriVars), user).content(asJson(body)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response);
    }

    /** Creates a workspace owned by {@code user} and returns its id. */
    protected UUID createWorkspace(TestUser user, String name, String slug) throws Exception {
        return UUID.fromString(
                postForCreated(user, "/api/workspaces", new WorkspacePayload(name, null, slug))
                        .get("id")
                        .asText());
    }

    /** Creates a document and returns its id. */
    protected UUID createDocument(
            TestUser user,
            UUID workspaceId,
            String title,
            String slug,
            String content,
            String documentType
    ) throws Exception {
        return UUID.fromString(postForCreated(
                user,
                "/api/workspaces/{workspaceId}/documents",
                new DocumentPayload(title, slug, content, documentType),
                workspaceId)
                .get("id")
                .asText());
    }

    /**
     * Promotes an account to instance administrator.
     *
     * <p>Done directly rather than through an endpoint: there is no API for granting
     * instance administration, because the only ways to become one are running setup
     * or being promoted by an operator with database access.
     */
    protected void makeInstanceAdmin(TestUser user) {
        instanceAdminSupport.promote(user.id());
    }

    /** Creates a board seeded with default columns and returns the full response. */
    protected JsonNode createBoard(TestUser user, UUID workspaceId, String name) throws Exception {
        return postForCreated(
                user, "/api/workspaces/{workspaceId}/boards", new BoardPayload(name), workspaceId);
    }

    protected record TestUser(
            UUID id, String email, String displayName, String handle, String token) {
    }

    private record WorkspacePayload(String name, String description, String slug) {
    }

    private record DocumentPayload(String title, String slug, String content, String documentType) {
    }

    private record BoardPayload(String name) {
    }

    private record RegistrationPayload(String email, String displayName, String password) {
    }
}
