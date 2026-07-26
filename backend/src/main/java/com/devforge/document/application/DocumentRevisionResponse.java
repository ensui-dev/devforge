package com.devforge.document.application;

import com.devforge.document.contract.DocumentType;
import com.devforge.document.domain.DocumentRevision;
import com.devforge.document.domain.RevisionReason;

import java.time.Instant;
import java.util.UUID;

/**
 * One revision in a document's history.
 *
 * @param authorLabel who wrote it, as they were called at the time. {@code null}
 *                    for revisions backfilled when history was introduced —
 *                    nothing recorded an author then, and inventing one would be
 *                    worse than admitting it.
 * @param content     omitted from list responses; see {@link #summary}
 */
public record DocumentRevisionResponse(
        int revision,
        String title,
        String slug,
        String content,
        DocumentType documentType,
        boolean internal,
        RevisionReason reason,
        Integer restoredFrom,
        UUID authorId,
        String authorLabel,
        Instant createdAt
) {

    /** Without a body — the shape a history list returns. */
    public static DocumentRevisionResponse summary(DocumentRevision revision) {
        return withBody(revision, null);
    }

    public static DocumentRevisionResponse withBody(DocumentRevision revision, String body) {
        return new DocumentRevisionResponse(
                revision.getRevision(),
                revision.getTitle(),
                revision.getSlug(),
                body,
                revision.getDocumentType(),
                revision.isInternal(),
                revision.getReason(),
                revision.getRestoredFrom(),
                revision.getAuthorId(),
                revision.getAuthorLabel(),
                revision.getCreatedAt()
        );
    }

}
