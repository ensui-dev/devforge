package com.devforge.audit.domain;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditTargetType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * One recorded change.
 *
 * <p>Deliberately not a {@code BaseEntity}. That superclass brings
 * {@code updated_at} and an optimistic-lock version, both of which imply a row
 * can be edited — and an audit row that can be edited is worthless. There are no
 * setters here, and nothing in the application updates or deletes one.
 */
@Entity
@Table(name = "audit_events")
public class AuditEvent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "actor_id", updatable = false)
    private UUID actorId;

    /** What the actor was called at the time; survives their rename or deletion. */
    @Column(name = "actor_label", length = 320, updatable = false)
    private String actorLabel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 48, updatable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 32, updatable = false)
    private AuditTargetType targetType;

    @Column(name = "target_id", updatable = false)
    private UUID targetId;

    @Column(name = "target_label", length = 255, updatable = false)
    private String targetLabel;

    @Column(name = "workspace_id", updatable = false)
    private UUID workspaceId;

    /**
     * Serialised JSON. Its shape varies per action, so it is stored opaquely.
     *
     * <p>{@code @JdbcTypeCode(JSON)} is required: without it Hibernate binds a
     * String as {@code varchar}, and PostgreSQL will not implicitly cast that to
     * {@code jsonb}. Keeping the column {@code jsonb} rather than {@code text} is
     * worth the annotation — the database then rejects a malformed payload
     * instead of storing it.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", updatable = false)
    private String detail;

    protected AuditEvent() {
        // for JPA
    }

    public AuditEvent(
            UUID actorId,
            String actorLabel,
            AuditAction action,
            AuditTargetType targetType,
            UUID targetId,
            String targetLabel,
            UUID workspaceId,
            String detail
    ) {
        this.id = UUID.randomUUID();
        // Matches PostgreSQL's precision, so a value read back equals the one written.
        this.occurredAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        this.actorId = actorId;
        this.actorLabel = actorLabel;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.targetLabel = targetLabel;
        this.workspaceId = workspaceId;
        this.detail = detail;
    }

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getActorId() {
        return actorId;
    }

    public String getActorLabel() {
        return actorLabel;
    }

    public AuditAction getAction() {
        return action;
    }

    public AuditTargetType getTargetType() {
        return targetType;
    }

    public UUID getTargetId() {
        return targetId;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getDetail() {
        return detail;
    }
}
