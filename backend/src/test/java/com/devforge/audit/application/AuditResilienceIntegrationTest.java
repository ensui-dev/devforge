package com.devforge.audit.application;

import com.devforge.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Recording must never break the thing being recorded.
 *
 * <p>This is a regression test for a real defect. {@link AuditRecorder} originally
 * carried {@code @Transactional(REQUIRES_NEW)} itself, with a try/catch in its
 * body. That cannot work: a transactional proxy commits after the method
 * <em>returns</em>, so a failure raised by the commit surfaces in the caller,
 * past the catch. The first audit row that violated a constraint turned every
 * {@code 201 Created} into a {@code 500}.
 *
 * <p>The fix moved the transaction into {@link AuditWriter}, so the catch wraps
 * the whole transaction. Stubbing the writer to throw exercises that boundary:
 * the exception arrives exactly where a commit failure would.
 */
class AuditResilienceIntegrationTest extends AbstractIntegrationTest {

    @MockitoBean
    private AuditWriter auditWriter;

    @Test
    void aFailingAuditWriteDoesNotFailTheRequest() throws Exception {
        Mockito.doThrow(new RuntimeException("audit storage is unavailable"))
                .when(auditWriter).write(Mockito.any());

        TestUser owner = registerUser("owner@acme.test", "Owner");

        // The whole point: every one of these still works.
        UUID workspace = createWorkspace(owner, "Platform", "platform");
        UUID document = createDocument(owner, workspace, "Design", "design", "body", "GENERAL");

        mockMvc.perform(authed(get("/api/workspaces/{w}/documents/{d}", workspace, document), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Design"));

        Mockito.verify(auditWriter, Mockito.atLeastOnce()).write(Mockito.any());
    }

    /**
     * And the change is genuinely committed, not merely reported as committed —
     * a swallowed audit failure must not leave the caller's transaction marked
     * rollback-only.
     */
    @Test
    void theChangeSurvivesAFailedAuditWrite() throws Exception {
        Mockito.doThrow(new RuntimeException("audit storage is unavailable"))
                .when(auditWriter).write(Mockito.any());

        TestUser owner = registerUser("owner@acme.test", "Owner");
        UUID workspace = createWorkspace(owner, "Platform", "platform");

        // Read it back in a fresh request; if the write had rolled back this 404s.
        mockMvc.perform(authed(get("/api/workspaces/{w}", workspace), owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Platform"));
    }
}
