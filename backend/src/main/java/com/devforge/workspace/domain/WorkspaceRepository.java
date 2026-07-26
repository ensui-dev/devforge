package com.devforge.workspace.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {

    /** Slugs are unique per owner, so a lookup by slug needs the namespace. */
    Optional<Workspace> findByOwnerUserIdAndSlug(UUID ownerUserId, String slug);

    boolean existsByOwnerUserIdAndSlug(UUID ownerUserId, String slug);

    /**
     * Resolves a workspace only while it is published.
     *
     * <p>Used by the public endpoints. Expressing "published" in the query means an
     * unpublished workspace is not merely filtered out later — it is never loaded,
     * so there is nothing for a caller to leak.
     */
    Optional<Workspace> findByOwnerUserIdAndSlugAndPublishedAtIsNotNull(
            UUID ownerUserId, String slug);

    List<Workspace> findAllByOwnerUserIdAndPublishedAtIsNotNullOrderByNameAsc(UUID ownerUserId);

    /**
     * Legacy resolution for links created before slugs were namespaced. Used only to
     * redirect to the canonical path when exactly one published workspace matches.
     */
    List<Workspace> findAllBySlugAndPublishedAtIsNotNull(String slug);

    List<Workspace> findAllByPublishedAtIsNotNullOrderByNameAsc();
}
