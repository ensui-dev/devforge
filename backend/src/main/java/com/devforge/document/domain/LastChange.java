package com.devforge.document.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * When a document last actually changed.
 *
 * <p>Taken from its newest revision rather than from {@code updated_at}, because
 * the two answer different questions. A save that changes nothing still touches
 * {@code updated_at}; it does not write a revision. Only the second is a change
 * worth telling anyone about.
 *
 * @param at null for a document with no revisions at all, which cannot happen for
 *           one created through the product but is not worth crashing over
 */
public record LastChange(UUID documentId, Instant at) {

    /** Keyed for lookup, since callers ask about a set of documents at once. */
    public static Map<UUID, Instant> byDocument(Collection<LastChange> changes) {
        return changes.stream()
                .filter(change -> change.at() != null)
                .collect(Collectors.toMap(LastChange::documentId, LastChange::at));
    }
}
