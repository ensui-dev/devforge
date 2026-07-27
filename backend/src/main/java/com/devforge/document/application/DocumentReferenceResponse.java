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
 * @param behind   whether the two ends have fallen out of step, and which way
 *                 round. On an outgoing edge it means the page this one points at
 *                 has changed since this one was last revised — so what is written
 *                 here may no longer be true. On a backlink it means the opposite:
 *                 this page changed and the page depending on it has not been
 *                 touched since, which is the "what breaks if I change this?"
 *                 question answered without anyone having to ask it.
 * @param relatedChangedAt when the far end last actually changed, so a client can
 *                 say how long the two have disagreed
 */
public record DocumentReferenceResponse(
        UUID id,
        ReferenceType referenceType,
        boolean outgoing,
        UUID relatedDocumentId,
        String relatedDocumentTitle,
        String relatedDocumentSlug,
        DocumentType relatedDocumentType,
        Instant createdAt,
        boolean behind,
        Instant relatedChangedAt
) {

    public static DocumentReferenceResponse of(
            DocumentReference reference,
            UUID viewedDocumentId,
            DocumentRef relatedDocument
    ) {
        return of(reference, viewedDocumentId, relatedDocument, null, null);
    }

    /**
     * @param viewedChangedAt  when the document being looked at last changed
     * @param relatedChangedAt when the far end last changed
     */
    public static DocumentReferenceResponse of(
            DocumentReference reference,
            UUID viewedDocumentId,
            DocumentRef relatedDocument,
            Instant viewedChangedAt,
            Instant relatedChangedAt
    ) {
        boolean outgoing = reference.isOutgoingFrom(viewedDocumentId);
        return new DocumentReferenceResponse(
                reference.getId(),
                reference.getReferenceType(),
                outgoing,
                reference.otherEnd(viewedDocumentId),
                relatedDocument == null ? null : relatedDocument.title(),
                relatedDocument == null ? null : relatedDocument.slug(),
                relatedDocument == null ? null : relatedDocument.documentType(),
                reference.getCreatedAt(),
                behind(outgoing, viewedChangedAt, relatedChangedAt),
                relatedChangedAt
        );
    }

    /**
     * Whichever end was revised last is the one that has moved on.
     *
     * <p>Strictly after, not at-or-after: two pages written in the same import are
     * in step with each other, and flagging every page in a fresh workspace would
     * teach people to ignore the marker on the first day.
     */
    private static boolean behind(boolean outgoing, Instant viewed, Instant related) {
        if (viewed == null || related == null) {
            return false;
        }
        return outgoing ? related.isAfter(viewed) : viewed.isAfter(related);
    }
}
