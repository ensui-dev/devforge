package com.devforge.document.application;

import com.devforge.document.contract.DocumentType;
import com.devforge.document.domain.Document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        UUID workspaceId,
        String title,
        String slug,
        String content,
        DocumentType documentType,
        Instant createdAt,
        Instant updatedAt
) {

    public static DocumentResponse from(Document document) {
        return new DocumentResponse(
                document.getId(),
                document.getWorkspaceId(),
                document.getTitle(),
                document.getSlug(),
                document.getContent(),
                document.getDocumentType(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }
}
