package com.devforge.document.application;

import java.time.Instant;

/**
 * What a linked page has changed since this one last kept up with it.
 *
 * <p>Two bodies rather than a computed diff. The client already knows how to diff
 * two texts — it does it for revision history — and sending the diff instead would
 * mean choosing the granularity here, where nothing knows how wide the screen is.
 *
 * @param since        the moment being compared from: when the page doing the
 *                     depending was itself last revised
 * @param before       what the linked page said then, or null when it did not
 *                     exist yet — which is worth saying rather than showing as an
 *                     enormous addition
 * @param beforeRevision the revision {@code before} came from, null with it
 * @param after        what the linked page says now
 */
public record ReferenceChangesResponse(
        String relatedDocumentTitle,
        String relatedDocumentSlug,
        Instant since,
        Integer beforeRevision,
        String before,
        int afterRevision,
        String after,
        Instant afterChangedAt
) {
}
