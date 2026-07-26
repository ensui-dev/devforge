package com.devforge.document.application;

import com.devforge.document.contract.DocumentType;
import com.devforge.document.domain.Document;

import java.time.Instant;
import java.util.UUID;

/**
 * Listing projection: heading plus a short excerpt instead of the full body.
 *
 * <p>Document bodies are unbounded text. Returning them in list and search
 * responses would make payload size scale with total content rather than with
 * the number of rows shown.
 *
 * @param excerpt first {@value #EXCERPT_LENGTH} characters of the body, trimmed
 *                at a word boundary
 */
public record DocumentSummaryResponse(
        UUID id,
        UUID workspaceId,
        String title,
        String slug,
        String excerpt,
        DocumentType documentType,
        boolean internal,
        Instant createdAt,
        Instant updatedAt
) {

    private static final int EXCERPT_LENGTH = 200;

    public static DocumentSummaryResponse from(Document document) {
        return new DocumentSummaryResponse(
                document.getId(),
                document.getWorkspaceId(),
                document.getTitle(),
                document.getSlug(),
                excerptOf(document.getContent()),
                document.getDocumentType(),
                document.isInternal(),
                document.getCreatedAt(),
                document.getUpdatedAt()
        );
    }

    private static String excerptOf(String content) {
        if (content == null) {
            return "";
        }
        String flattened = content.replaceAll("\\s+", " ").trim();
        if (flattened.length() <= EXCERPT_LENGTH) {
            return flattened;
        }
        String clipped = flattened.substring(0, EXCERPT_LENGTH);
        int lastSpace = clipped.lastIndexOf(' ');
        return (lastSpace > 0 ? clipped.substring(0, lastSpace) : clipped) + "…";
    }
}
