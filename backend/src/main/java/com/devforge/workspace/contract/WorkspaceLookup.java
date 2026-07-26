package com.devforge.workspace.contract;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resolves workspaces that have published their documentation.
 *
 * <p>Deliberately separate from {@link WorkspaceAccess}, which authorises a
 * caller. There is no caller to authorise here — the whole point is that these
 * workspaces are readable by anyone — so the safety property is built into the
 * lookup instead: <strong>an unpublished workspace is never returned</strong>.
 * Callers cannot ask for one, and so cannot leak one by forgetting a check.
 */
public interface WorkspaceLookup {

    /**
     * @param handle the owner's handle, which namespaces the slug
     * @return the workspace only if it exists, belongs to that owner, and is published
     */
    Optional<PublishedWorkspace> findPublished(String handle, String slug);

    /** Everything one owner has published. */
    List<PublishedWorkspace> findPublishedByOwner(String handle);

    /** Every published workspace, for the public documentation directory. */
    List<PublishedWorkspace> findAllPublished();

    /**
     * Resolves a bare slug for links made before slugs were namespaced.
     *
     * @return every published workspace with that slug; the caller redirects only
     *         when there is exactly one, since the slug is otherwise ambiguous
     */
    List<PublishedWorkspace> findPublishedBySlug(String slug);

    /**
     * A published workspace, described without reference to any caller.
     *
     * @param ownerHandle namespaces {@code slug}; together they form the public path
     */
    record PublishedWorkspace(
            UUID id,
            String name,
            String slug,
            String ownerHandle,
            String description,
            Instant publishedAt
    ) {

        /** The canonical public address of this documentation. */
        public String publicPath() {
            return "/docs/" + ownerHandle + "/" + slug;
        }
    }
}
