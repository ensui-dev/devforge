package com.devforge.audit.contract;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * One thing to record.
 *
 * <p>Built through {@link #of} and the {@code with…} methods so a caller states
 * only what applies. The label fields matter more than they look: they are
 * denormalised copies of what the actor and target were called at the time, so
 * the log still reads correctly after a rename or a deletion. A log that rewrites
 * itself when the present changes is not a log.
 */
public final class AuditEntry {

    private final AuditAction action;
    private final AuditTargetType targetType;
    private UUID targetId;
    private String targetLabel;
    private UUID workspaceId;
    private final Map<String, Object> detail = new LinkedHashMap<>();

    private AuditEntry(AuditAction action, AuditTargetType targetType) {
        this.action = action;
        this.targetType = targetType;
    }

    public static AuditEntry of(AuditAction action, AuditTargetType targetType) {
        return new AuditEntry(action, targetType);
    }

    public AuditEntry target(UUID id, String label) {
        this.targetId = id;
        this.targetLabel = label;
        return this;
    }

    /** Scopes the event to a workspace, so a team can read its own history. */
    public AuditEntry inWorkspace(UUID workspaceId) {
        this.workspaceId = workspaceId;
        return this;
    }

    /** A specific worth recording: which role, which field, what it was before. */
    public AuditEntry with(String key, Object value) {
        if (value != null) {
            detail.put(key, value);
        }
        return this;
    }

    /**
     * Records a field that changed, and skips it when it did not.
     *
     * <p>Keeps an "updated" entry meaningful: without this every save would list
     * every field, and the one thing that actually changed would be buried.
     */
    public AuditEntry changed(String field, Object before, Object after) {
        if (before == null ? after != null : !before.equals(after)) {
            detail.put(field, Map.of("from", String.valueOf(before), "to", String.valueOf(after)));
        }
        return this;
    }

    public AuditAction action() {
        return action;
    }

    public AuditTargetType targetType() {
        return targetType;
    }

    public UUID targetId() {
        return targetId;
    }

    public String targetLabel() {
        return targetLabel;
    }

    public UUID workspaceId() {
        return workspaceId;
    }

    public Map<String, Object> detail() {
        return Map.copyOf(detail);
    }
}
