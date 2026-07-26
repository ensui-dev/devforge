package com.devforge.document.domain;

import com.devforge.document.contract.DocumentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * What a document said at one point in time.
 *
 * <p>A snapshot rather than a diff against its predecessor — the same choice git
 * makes, where deltas are a packfile concern rather than the logical model. A
 * snapshot reads in one indexed lookup, while a diff chain has to be replayed from
 * the beginning and goes wrong only on the oldest data, where it is hardest to
 * notice.
 *
 * <p>The body is referenced by hash and stored in {@link DocumentContent}, so
 * revisions sharing content share one copy of it.
 *
 * <p>Immutable, like {@link com.devforge.audit.domain.AuditEvent} and for the same
 * reason: history that can be edited is not history. Restoring an old revision
 * appends a new one rather than removing what came after.
 */
@Entity
@Table(name = "document_revisions")
public class DocumentRevision {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    /** 1-based and contiguous per document, so it can be shown to a reader. */
    @Column(nullable = false, updatable = false)
    private int revision;

    @Column(nullable = false, length = 255, updatable = false)
    private String title;

    @Column(nullable = false, length = 255, updatable = false)
    private String slug;

    /** SHA-256 of the body; the body itself lives in {@link DocumentContent}. */
    @Column(name = "content_hash", nullable = false, length = 64, updatable = false)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32, updatable = false)
    private DocumentType documentType;

    @Column(nullable = false, updatable = false)
    private boolean internal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private RevisionReason reason;

    @Column(name = "restored_from", updatable = false)
    private Integer restoredFrom;

    @Column(name = "author_id", updatable = false)
    private UUID authorId;

    /** Denormalised so history still reads correctly after the author leaves. */
    @Column(name = "author_label", length = 320, updatable = false)
    private String authorLabel;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentRevision() {
        // for JPA
    }

    public DocumentRevision(
            UUID documentId,
            int revision,
            Document source,
            RevisionReason reason,
            Integer restoredFrom,
            UUID authorId,
            String authorLabel
    ) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.revision = revision;
        this.title = source.getTitle();
        this.slug = source.getSlug();
        this.contentHash = DocumentContent.hash(source.getContent());
        this.documentType = source.getDocumentType();
        this.internal = source.isInternal();
        this.reason = reason;
        this.restoredFrom = restoredFrom;
        this.authorId = authorId;
        this.authorLabel = authorLabel;
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public int getRevision() {
        return revision;
    }

    public String getTitle() {
        return title;
    }

    public String getSlug() {
        return slug;
    }

    public String getContentHash() {
        return contentHash;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public boolean isInternal() {
        return internal;
    }

    public RevisionReason getReason() {
        return reason;
    }

    public Integer getRestoredFrom() {
        return restoredFrom;
    }

    public UUID getAuthorId() {
        return authorId;
    }

    public String getAuthorLabel() {
        return authorLabel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
