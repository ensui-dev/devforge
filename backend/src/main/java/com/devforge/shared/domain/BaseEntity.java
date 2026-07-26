package com.devforge.shared.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Identity, auditing, and optimistic locking for every persistent entity.
 *
 * <p>Identifiers are assigned in the constructor rather than by the database.
 * That gives an entity stable identity before it is ever flushed, which is what
 * makes {@link #equals(Object)} safe inside collections and lets aggregates wire
 * up their children in memory before a save.
 *
 * <p>Because the id is pre-assigned it cannot signal newness, so Spring Data
 * relies on the {@link Version} property instead: {@code version == null} means
 * "not yet persisted".
 */
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected BaseEntity() {
        this.id = UUID.randomUUID();
    }

    @PrePersist
    void onCreate() {
        Instant now = now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = now();
    }

    /**
     * Truncated to the precision PostgreSQL stores.
     *
     * <p>{@code Instant.now()} carries nanoseconds while {@code TIMESTAMPTZ} keeps
     * microseconds, so without this the timestamp a write returns differs in its
     * final digits from the one a later read produces.
     */
    private static Instant now() {
        return Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Long getVersion() {
        return version;
    }

    /** Entity equality is identifier equality within the same entity type. */
    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof BaseEntity that)) {
            return false;
        }
        return persistentClass(this).equals(persistentClass(that)) && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "%s(id=%s)".formatted(persistentClass(this).getSimpleName(), id);
    }

    /**
     * Hibernate subclasses entities to build lazy proxies. Unwrapping one level
     * lets a proxy and its initialised counterpart compare equal.
     */
    private static Class<?> persistentClass(BaseEntity entity) {
        Class<?> type = entity.getClass();
        return type.getName().contains("$HibernateProxy$") ? type.getSuperclass() : type;
    }
}
