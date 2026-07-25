package com.devforge.document.contract;

import java.util.UUID;

/**
 * The document module's public view of a document — identity and heading only.
 *
 * <p>Body content is deliberately excluded: the modules that link to documents
 * (boards, tasks) only ever render a label, and excluding it keeps large text
 * out of unrelated queries.
 */
public record DocumentRef(UUID id, UUID workspaceId, String title, String slug, DocumentType documentType) {
}
