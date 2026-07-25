package com.devforge.document.application;

import com.devforge.document.contract.DocumentRef;
import com.devforge.document.contract.DocumentType;
import com.devforge.document.contract.ReferenceType;
import com.devforge.document.domain.DocumentReference;

import java.time.Instant;
import java.util.UUID;

/**
 * One edge, described from the perspective of the document being viewed.
 *
 * <p>Carrying the far end's title and the edge direction means a client can
 * render the full reference panel — outgoing links and backlinks — from a single
 * response without resolving ids itself.
 *
 * @param outgoing {@code true} when the viewed document is the source of the edge
 */
public record DocumentReferenceResponse(
        UUID id,
        ReferenceType referenceType,
        boolean outgoing,
        UUID relatedDocumentId,
        String relatedDocumentTitle,
        String relatedDocumentSlug,
        DocumentType relatedDocumentType,
        Instant createdAt
) {

    public static DocumentReferenceResponse of(
            DocumentReference reference,
            UUID viewedDocumentId,
            DocumentRef relatedDocument
    ) {
        return new DocumentReferenceResponse(
                reference.getId(),
                reference.getReferenceType(),
                reference.isOutgoingFrom(viewedDocumentId),
                reference.otherEnd(viewedDocumentId),
                relatedDocument == null ? null : relatedDocument.title(),
                relatedDocument == null ? null : relatedDocument.slug(),
                relatedDocument == null ? null : relatedDocument.documentType(),
                reference.getCreatedAt()
        );
    }
}
