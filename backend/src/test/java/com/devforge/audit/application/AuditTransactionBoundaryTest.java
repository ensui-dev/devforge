package com.devforge.audit.application;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Where the audit transaction boundary sits, asserted directly.
 *
 * <p>{@link AuditResilienceIntegrationTest} proves that a throwing writer does not
 * break the caller — but it proves it with a stub, and a stub throws on the call
 * whichever class owns the transaction. It therefore cannot detect the specific
 * mistake this feature was born with: putting {@code @Transactional(REQUIRES_NEW)}
 * on {@code AuditRecorder.record} and catching inside it.
 *
 * <p>That arrangement looks correct and is not. A transactional proxy commits
 * after the method returns, so a constraint violation surfacing at commit passes
 * straight through the catch and into the caller — which is how the first version
 * turned every {@code 201 Created} into a {@code 500}.
 *
 * <p>The invariant is structural, so it is asserted structurally.
 */
class AuditTransactionBoundaryTest {

    @Test
    void theRecorderIsNotTransactional() throws Exception {
        Method record = AuditRecorder.class.getDeclaredMethod(
                "record", java.util.UUID.class, com.devforge.audit.contract.AuditEntry.class);

        assertThat(record.isAnnotationPresent(Transactional.class))
                .as("AuditRecorder.record must not be transactional: it has to catch "
                        + "failures raised by the commit, which happens after a "
                        + "transactional method returns")
                .isFalse();
        assertThat(AuditRecorder.class.isAnnotationPresent(Transactional.class))
                .as("nor may the class carry it, which would annotate every method")
                .isFalse();
    }

    @Test
    void theWriterOwnsTheTransactionAndKeepsItSeparate() throws Exception {
        Method write = AuditWriter.class.getDeclaredMethod("write", com.devforge.audit.domain.AuditEvent.class);
        Transactional annotation = write.getAnnotation(Transactional.class);

        assertThat(annotation)
                .as("AuditWriter.write owns the audit transaction")
                .isNotNull();
        assertThat(annotation.propagation())
                .as("an attempt that the caller later rolls back still happened, "
                        + "and the log should say so")
                .isEqualTo(Propagation.REQUIRES_NEW);
    }
}
