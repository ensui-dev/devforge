package com.devforge.document.application;

import com.devforge.document.contract.DocumentType;
import com.devforge.document.domain.Document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A published document with the reference edges touching it. */
public record PublicDocumentResponse(
        UUID id,
        String title,
        String slug,
        String content,
        DocumentType documentType,
        List<DocumentReferenceResponse> references,
        Instant updatedAt
) {

    public static PublicDocumentResponse of(Document document, List<DocumentReferenceResponse> references) {
        return new PublicDocumentResponse(
                document.getId(),
                document.getTitle(),
                document.getSlug(),
                document.getContent(),
                document.getDocumentType(),
                references,
                document.getUpdatedAt()
        );
    }
}
