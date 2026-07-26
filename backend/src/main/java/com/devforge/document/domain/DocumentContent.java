package com.devforge.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * A document body, stored once per distinct content.
 *
 * <p>Content addressing borrowed from git: the row is keyed by the hash of what it
 * holds, so identical content is stored once however many revisions share it.
 * Restoring a revision produces a body byte-identical to one already stored, and
 * reverting an edit by hand does too — without this, both would duplicate a whole
 * document.
 *
 * <p>Unlike git's object store this is scoped to one document. Git's is global,
 * which is why git needs {@code gc} to collect blobs nothing references any more;
 * scoping to a document means deletion cascades and there is nothing to collect.
 * Duplication happens within a single document's history anyway.
 */
@Entity
@Table(name = "document_contents")
public class DocumentContent {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "content_hash", nullable = false, length = 64, updatable = false)
    private String contentHash;

    @Column(nullable = false, columnDefinition = "text", updatable = false)
    private String body;

    protected DocumentContent() {
        // for JPA
    }

    public DocumentContent(UUID documentId, String body) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.body = body == null ? "" : body;
        this.contentHash = hash(this.body);
    }

    /**
     * SHA-256 of the body, hex encoded.
     *
     * <p>Matches {@code encode(sha256(content::bytea), 'hex')} in the migration, so
     * rows backfilled by SQL and rows written by the application agree. A mismatch
     * would silently create a second copy of content that already exists.
     */
    public static String hash(String body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of()
                    .formatHex(digest.digest((body == null ? "" : body).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is required of every JVM, so this cannot happen.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getContentHash() {
        return contentHash;
    }

    public String getBody() {
        return body;
    }
}
