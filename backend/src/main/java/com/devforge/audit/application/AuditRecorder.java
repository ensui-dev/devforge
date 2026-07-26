package com.devforge.audit.application;

import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.audit.domain.AuditEvent;
import com.devforge.identity.contract.UserDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

/**
 * Writes to the audit log.
 *
 * <p>Every other module calls this, so it deliberately depends on as little as
 * possible: its own repository, the identity directory to name the actor, and a
 * JSON mapper. Anything it called back into would risk a bean cycle — the same
 * reason {@code InstancePolicyService} is kept separate from {@code InstanceService}.
 *
 * <p>Reading the log is {@link AuditQueryService}'s job. Splitting them keeps this
 * class free of {@code WorkspaceAccess}, which workspace's own services would then
 * be depending on transitively.
 */
@Service
public class AuditRecorder implements AuditTrail {

    private static final Logger log = LoggerFactory.getLogger(AuditRecorder.class);

    private final AuditWriter writer;
    private final UserDirectory userDirectory;
    private final JsonMapper jsonMapper;

    AuditRecorder(
            AuditWriter writer,
            UserDirectory userDirectory,
            JsonMapper jsonMapper
    ) {
        this.writer = writer;
        this.userDirectory = userDirectory;
        this.jsonMapper = jsonMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Deliberately <em>not</em> transactional itself. {@link AuditWriter} owns
     * the transaction, so the catch below wraps the commit as well as the insert —
     * a {@code @Transactional} method cannot catch a failure thrown by its own
     * commit, because that happens after its body returns.
     *
     * <p>Failures are swallowed and logged. Losing an audit row is bad; failing
     * the user's actual request because the log was unavailable is worse.
     */
    @Override
    public void record(UUID actorId, AuditEntry entry) {
        try {
            writer.write(new AuditEvent(
                    actorId,
                    labelFor(actorId),
                    entry.action(),
                    entry.targetType(),
                    entry.targetId(),
                    truncate(entry.targetLabel(), 255),
                    entry.workspaceId(),
                    serialise(entry.detail())
            ));
        } catch (RuntimeException e) {
            log.error("Could not record audit event {} on {} by {}",
                    entry.action(), entry.targetType(), actorId, e);
        }
    }

    /**
     * The actor's name and address as they are now, copied into the row.
     *
     * <p>Resolved at write time on purpose: joining to {@code users} at read time
     * would make old entries change when someone is renamed, and vanish when an
     * account is deleted.
     */
    private String labelFor(UUID actorId) {
        if (actorId == null) {
            return null;
        }
        return userDirectory.findById(actorId)
                .map(user -> truncate("%s <%s>".formatted(user.displayName(), user.email()), 320))
                .orElse(null);
    }

    private String serialise(Map<String, Object> detail) {
        if (detail.isEmpty()) {
            return null;
        }
        return jsonMapper.writeValueAsString(detail);
    }

    /**
     * Keeps a label inside its column.
     *
     * <p>Both are reachable in practice: a display name of 120 plus an address of
     * 320 exceeds the 320-character actor column, and a document title can fill
     * the 255-character target column. Either would fail the insert and — because
     * failures here are swallowed — silently lose the entry.
     */
    private static String truncate(String label, int max) {
        if (label == null || label.length() <= max) {
            return label;
        }
        return label.substring(0, max - 3) + "...";
    }
}
