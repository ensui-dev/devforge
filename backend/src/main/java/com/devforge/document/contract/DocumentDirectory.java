package com.devforge.document.contract;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Published interface for resolving documents from other modules.
 *
 * <p>Every lookup is scoped by workspace. That is not a convenience — it is the
 * tenancy boundary: it makes it impossible for the {@code task} module to link a
 * task to a document belonging to a different team, even by supplying a valid
 * document id.
 */
public interface DocumentDirectory {

    Optional<DocumentRef> find(UUID workspaceId, UUID documentId);

    Map<UUID, DocumentRef> findAllByIds(UUID workspaceId, Collection<UUID> documentIds);

    /**
     * @throws com.devforge.shared.exception.ResourceNotFoundException if no such
     *         document exists in that workspace
     */
    DocumentRef require(UUID workspaceId, UUID documentId);

    /**
     * How a workspace's pages divide between public and held back.
     *
     * <p>Published here because the workspace module needs it to describe what
     * publishing would expose, and counting documents is not its business.
     */
    VisibilityCounts countByVisibility(UUID workspaceId);

    record VisibilityCounts(int publicPages, int internalPages) {
    }
}
