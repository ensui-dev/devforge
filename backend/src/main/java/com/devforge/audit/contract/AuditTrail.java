package com.devforge.audit.contract;

import java.util.UUID;

/**
 * Records what an account did, for every module that changes durable state.
 *
 * <p>Published so workspace, document, task, and instance can write to the log
 * without seeing how it is stored. The implementation deliberately depends on
 * nothing but its own repository — every other module calls into it, so anything
 * it called back into would risk a bean cycle. Reading the log is a separate
 * concern with its own service.
 *
 * <p>Recording must never break the thing being recorded. A failure to write an
 * audit row is logged and swallowed rather than rolling back the change the user
 * actually asked for.
 */
public interface AuditTrail {

    /**
     * @param actorId the signed-in account responsible, or {@code null} for an
     *                event with no actor — first-run setup happens before any
     *                account exists
     */
    void record(UUID actorId, AuditEntry entry);
}
