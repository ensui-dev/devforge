package com.devforge.document.application;

import com.devforge.document.contract.DocumentType;
import com.devforge.document.domain.Document;
import com.devforge.workspace.contract.WorkspaceLookup.PublishedWorkspace;

import java.util.List;
import java.util.UUID;

/**
 * A published workspace's table of contents.
 *
 * @param slug the URL segment this documentation is served under
 */
public record PublicHandbookResponse(
        String name,
        String slug,
        String ownerHandle,
        List<Entry> entries,
        String description
) {

    /** One page: enough to build navigation, without the body. */
    public record Entry(UUID id, String title, String slug, DocumentType documentType) {
    }

    public static PublicHandbookResponse of(PublishedWorkspace workspace, List<Document> documents) {
        return new PublicHandbookResponse(
                workspace.name(),
                workspace.slug(),
                workspace.ownerHandle(),
                documents.stream()
                        .map(document -> new Entry(
                                document.getId(),
                                document.getTitle(),
                                document.getSlug(),
                                document.getDocumentType()))
                        .toList(),
                workspace.description()
        );
    }
}
