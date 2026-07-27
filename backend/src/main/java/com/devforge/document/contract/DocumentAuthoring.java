package com.devforge.document.contract;

import java.util.List;
import java.util.UUID;

/**
 * Published interface for writing documents on behalf of another module.
 *
 * <p>Exists so the {@code sync} module can apply documentation from a git
 * repository without depending on {@code DocumentService}. Everything the document
 * module guarantees about a write — a revision per change, content stored once per
 * distinct body, no revision when nothing changed — happens behind this interface
 * rather than being reimplemented by each caller.
 *
 * <p>Deliberately narrow. It cannot read bodies, search, or reorganise anything;
 * it upserts by slug and archives by slug, which is the whole vocabulary an
 * external source of truth needs.
 *
 * <p>Access control is <em>not</em> applied here. The caller has already decided
 * that the change is permitted — a webhook has no signed-in user to check — so
 * this interface trusts its caller, exactly as {@code AccountProvisioning} does.
 */
public interface DocumentAuthoring {

    /**
     * Creates the document if its slug is new, updates it if not.
     *
     * @param actorId whoever is responsible, or {@code null} when no account is —
     *                a webhook fires without a session
     * @return what the write did, so a caller can report counts
     */
    AuthoringResult upsert(UUID workspaceId, DocumentDraft draft, UUID actorId, AuthoringOrigin origin);

    /**
     * Marks a document internal so it disappears from published documentation
     * without being destroyed.
     *
     * <p>The cautious answer to a file being deleted upstream: the page stops being
     * public, its history survives, and a human can decide whether to delete it.
     *
     * @return false when no document has that slug
     */
    boolean archiveBySlug(UUID workspaceId, String slug, UUID actorId, AuthoringOrigin origin);

    /** Permanently removes the document, its revisions, and its links. */
    boolean deleteBySlug(UUID workspaceId, String slug, UUID actorId, AuthoringOrigin origin);

    /** Every slug in the workspace, for working out what a source no longer contains. */
    List<String> slugsIn(UUID workspaceId);

    /**
     * Makes a document's outgoing references exactly those declared.
     *
     * <p>Called in a second pass, after every document exists, so a file may point at
     * one that appears later in the same import — which is the normal case, since
     * documentation is written as a graph rather than in dependency order.
     *
     * <p>Only outgoing references are touched. Backlinks are derived from the far
     * side's declarations, so a page is never edited by something it points at.
     *
     * @param declared the complete set for this source; anything currently stored and
     *                 not listed here is removed
     * @return what changed, and any target slug that matched no document
     */
    ReferenceOutcome replaceReferences(
            UUID workspaceId,
            String sourceSlug,
            List<DeclaredReference> declared,
            UUID actorId,
            AuthoringOrigin origin);

    /**
     * @param unresolved target slugs that matched no document, reported rather than
     *                   dropped — a typo in a link is exactly the thing a reader
     *                   would otherwise discover much later
     */
    record ReferenceOutcome(int added, int removed, List<String> unresolved) {

        public static ReferenceOutcome nothing() {
            return new ReferenceOutcome(0, 0, List.of());
        }
    }

    enum AuthoringResult {
        CREATED,
        UPDATED,
        /** The draft matched what was already stored, so nothing was written. */
        UNCHANGED
    }
}
