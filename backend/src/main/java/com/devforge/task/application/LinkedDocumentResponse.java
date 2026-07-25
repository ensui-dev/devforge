package com.devforge.task.application;

import com.devforge.document.contract.DocumentRef;
import com.devforge.document.contract.DocumentType;

import java.util.UUID;

/** A document cited by a task, enough to render and navigate to it. */
public record LinkedDocumentResponse(UUID id, String title, String slug, DocumentType documentType) {

    public static LinkedDocumentResponse from(DocumentRef document) {
        return new LinkedDocumentResponse(
                document.id(),
                document.title(),
                document.slug(),
                document.documentType()
        );
    }
}
