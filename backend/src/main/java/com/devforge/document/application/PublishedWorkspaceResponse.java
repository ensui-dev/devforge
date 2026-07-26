package com.devforge.document.application;

import com.devforge.workspace.contract.WorkspaceLookup.PublishedWorkspace;

import java.time.Instant;

/**
 * One entry in the public documentation directory.
 *
 * @param slug      the URL segment this workspace's documentation is served under
 * @param pageCount how many pages are public, excluding any held back as internal
 */
public record PublishedWorkspaceResponse(
        String name,
        String slug,
        String ownerHandle,
        /** Where this documentation lives: {@code /docs/{ownerHandle}/{slug}}. */
        String publicPath,
        String description,
        int pageCount,
        Instant publishedAt
) {

    public static PublishedWorkspaceResponse of(PublishedWorkspace workspace, int pageCount) {
        return new PublishedWorkspaceResponse(
                workspace.name(),
                workspace.slug(),
                workspace.ownerHandle(),
                workspace.publicPath(),
                workspace.description(),
                pageCount,
                workspace.publishedAt()
        );
    }
}
