package com.devforge.workspace.domain;

import com.devforge.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
public class Workspace extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String slug;

    /**
     * The user whose handle namespaces this workspace.
     *
     * <p>Held as a bare identifier rather than an association: identity is another
     * module, and the database foreign key already guarantees integrity.
     */
    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    /**
     * When this workspace's documentation became publicly readable; {@code null}
     * while private.
     *
     * <p>A timestamp rather than a flag, so "published" carries when it happened.
     */
    @Column(name = "published_at")
    private Instant publishedAt;

    protected Workspace() {
    }

    public Workspace(String name, String description, String slug, UUID ownerUserId) {
        this.name = name;
        this.description = description;
        this.slug = slug;
        this.ownerUserId = ownerUserId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSlug() {
        return slug;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public void describe(String name, String description, String slug) {
        this.name = name;
        this.description = description;
        this.slug = slug;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public boolean isPublished() {
        return publishedAt != null;
    }

    /** Idempotent: re-publishing an already published workspace keeps its original date. */
    public void publish() {
        if (publishedAt == null) {
            // Truncated to the precision PostgreSQL stores, so the timestamp
            // returned by the call that publishes matches the one read back
            // afterwards instead of losing sub-microsecond digits in transit.
            publishedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        }
    }

    public void unpublish() {
        publishedAt = null;
    }
}
