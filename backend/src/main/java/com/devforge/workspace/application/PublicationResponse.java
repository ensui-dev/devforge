package com.devforge.workspace.application;

import com.devforge.workspace.domain.Workspace;

import java.time.Instant;

/**
 * The publication state of a workspace's documentation.
 *
 * @param publicPath    where the documentation is served, or null while private
 * @param publicPages   pages currently readable by anyone
 * @param internalPages pages held back from the public site
 */
public record PublicationResponse(
        boolean published,
        Instant publishedAt,
        String publicPath,
        int publicPages,
        int internalPages
) {

    public static PublicationResponse of(
            Workspace workspace,
            String ownerHandle,
            int publicPages,
            int internalPages
    ) {
        return new PublicationResponse(
                workspace.isPublished(),
                workspace.getPublishedAt(),
                // Namespaced by the owner's handle, so two teams may both publish a
                // workspace called "nokia".
                workspace.isPublished() ? "/docs/" + ownerHandle + "/" + workspace.getSlug() : null,
                publicPages,
                internalPages
        );
    }
}
