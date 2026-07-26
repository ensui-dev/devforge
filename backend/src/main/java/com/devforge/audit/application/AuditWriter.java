package com.devforge.audit.application;

import com.devforge.audit.domain.AuditEvent;
import com.devforge.audit.domain.AuditEventRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of recording, kept in its own bean deliberately.
 *
 * <p>A {@code REQUIRES_NEW} transaction commits when the proxied method
 * <em>returns</em>, not at the last statement of its body. So a try/catch written
 * inside such a method cannot see a failure raised by its own commit — the
 * exception surfaces in the caller. Putting the boundary here means
 * {@link AuditRecorder} catches around the whole transaction, commit included,
 * which is the only way to honour the promise that recording never breaks the
 * change being recorded.
 *
 * <p>Package-private: nothing outside this module should hold a reference to it.
 */
@Component
class AuditWriter {

    private final AuditEventRepository repository;

    AuditWriter(AuditEventRepository repository) {
        this.repository = repository;
    }

    /**
     * Writes one event in a transaction of its own.
     *
     * <p>Independent of the caller's transaction on purpose: an attempt that was
     * later rolled back for an unrelated reason still happened, and the log should
     * say so.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void write(AuditEvent event) {
        repository.save(event);
    }
}
