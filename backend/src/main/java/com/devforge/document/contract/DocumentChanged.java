package com.devforge.document.contract;

import java.util.List;
import java.util.UUID;

/**
 * Published whenever a document's stored form changes.
 *
 * <p>The document module announces; it does not know or care who listens. That is
 * what lets a workspace's git repository stay in step with edits made in the
 * interface without {@code DocumentService} ever hearing about git — and what keeps
 * a failure to write a commit from being able to fail the edit.
 *
 * <p>Carries the whole document rather than an identifier, for two reasons. A
 * listener would otherwise need read access to the document module, which nothing
 * outside it has; and a removal has nothing left to read by the time it is heard.
 *
 * @param previousSlug the slug before this change, or {@code null} when the document
 *                     is new — a listener mirroring documents onto paths has to know
 *                     that a page moved rather than that a second one appeared
 * @param origin       where the change came from. A listener that applied the change
 *                     in the first place uses this to recognise its own echo.
 */
public record DocumentChanged(
        UUID workspaceId,
        UUID documentId,
        Change change,
        String slug,
        String previousSlug,
        String title,
        String content,
        DocumentType documentType,
        boolean internal,
        List<DeclaredReference> references,
        UUID actorId,
        AuthoringOrigin origin
) {

    public enum Change {
        /** The document now exists and says this. */
        WRITTEN,
        /** The document is gone. Only the slug is meaningful. */
        REMOVED
    }

    /** Whether this change moved the document to a different slug. */
    public boolean renamed() {
        return previousSlug != null && !previousSlug.equals(slug);
    }
}
