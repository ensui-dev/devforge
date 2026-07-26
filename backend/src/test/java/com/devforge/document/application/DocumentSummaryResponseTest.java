package com.devforge.document.application;

import com.devforge.document.contract.DocumentType;
import com.devforge.document.domain.Document;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentSummaryResponseTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    @Test
    void keepsShortContentIntact() {
        DocumentSummaryResponse summary = summaryOf("Short and sweet.");

        assertThat(summary.excerpt()).isEqualTo("Short and sweet.");
    }

    @Test
    void collapsesWhitespaceAndNewlines() {
        DocumentSummaryResponse summary = summaryOf("# Heading\n\nSome   body\ttext.");

        assertThat(summary.excerpt()).isEqualTo("# Heading Some body text.");
    }

    @Test
    void truncatesLongContentAtAWordBoundary() {
        String content = "word ".repeat(100);

        DocumentSummaryResponse summary = summaryOf(content);

        assertThat(summary.excerpt()).endsWith("…");
        assertThat(summary.excerpt()).doesNotContain("wor…");
        assertThat(summary.excerpt().length()).isLessThanOrEqualTo(201);
    }

    @Test
    void handlesEmptyContent() {
        assertThat(summaryOf("").excerpt()).isEmpty();
    }

    @Test
    void carriesIdentityAndType() {
        Document document = new Document(
                WORKSPACE_ID, "Auth Flow", "auth-flow", "body", DocumentType.ARCHITECTURE, false);

        DocumentSummaryResponse summary = DocumentSummaryResponse.from(document);

        assertThat(summary.id()).isEqualTo(document.getId());
        assertThat(summary.workspaceId()).isEqualTo(WORKSPACE_ID);
        assertThat(summary.title()).isEqualTo("Auth Flow");
        assertThat(summary.slug()).isEqualTo("auth-flow");
        assertThat(summary.documentType()).isEqualTo(DocumentType.ARCHITECTURE);
    }

    private static DocumentSummaryResponse summaryOf(String content) {
        return DocumentSummaryResponse.from(
                new Document(WORKSPACE_ID, "Title", "title", content, DocumentType.GENERAL, false));
    }
}
