package com.devforge.sync.application;

import com.devforge.document.contract.DocumentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Front-matter parsing.
 *
 * <p>The grammar is a deliberately tiny subset of YAML, so these tests are mostly
 * about what it does with input it was not designed for: unclosed fences, unknown
 * keys, values that are not booleans. The rule throughout is that it never silently
 * discards anything — it either understands the file or says what it could not read.
 */
class MarkdownFileTest {

    private MarkdownFile parse(String path, String raw) {
        return MarkdownFile.parse(path, raw, DocumentType.GENERAL);
    }

    @Test
    void readsFrontMatterAndBody() {
        MarkdownFile file = parse("docs/event-ingestion.md", """
                ---
                title: Event ingestion pipeline
                type: ARCHITECTURE
                internal: false
                ---

                # Event ingestion pipeline

                Consumes from the orders topic.""");

        assertThat(file.slug()).isEqualTo("event-ingestion");
        assertThat(file.title()).isEqualTo("Event ingestion pipeline");
        assertThat(file.documentType()).isEqualTo(DocumentType.ARCHITECTURE);
        assertThat(file.internal()).isFalse();
        assertThat(file.content()).startsWith("# Event ingestion pipeline");
        assertThat(file.content()).doesNotContain("---");
        assertThat(file.warnings()).isEmpty();
    }

    /** Most documentation already opens with its title; repeating it would go stale. */
    @Test
    void takesTheTitleFromTheFirstHeadingWhenFrontMatterOmitsIt() {
        MarkdownFile file = parse("docs/runbook.md", """
                # Consumer lag runbook

                Diagnose first, then act.""");

        assertThat(file.title()).isEqualTo("Consumer lag runbook");
        assertThat(file.documentType()).isEqualTo(DocumentType.GENERAL);
    }

    @Test
    void fallsBackToTheFilenameWhenThereIsNoHeadingEither() {
        MarkdownFile file = parse("docs/consumer-lag.md", "Just prose, no heading.");

        assertThat(file.title()).isEqualTo("Consumer lag");
        assertThat(file.content()).isEqualTo("Just prose, no heading.");
    }

    @Test
    void handlesAFileWithNoFrontMatterAtAll() {
        MarkdownFile file = parse("a.md", "plain");

        assertThat(file.warnings()).isEmpty();
        assertThat(file.content()).isEqualTo("plain");
    }

    @Test
    void readsInternalAsABoolean() {
        assertThat(parse("a.md", "---\ninternal: true\n---\nx").internal()).isTrue();
        assertThat(parse("a.md", "---\ninternal: yes\n---\nx").internal()).isTrue();
        assertThat(parse("a.md", "---\ninternal: false\n---\nx").internal()).isFalse();
        assertThat(parse("a.md", "---\ninternal: no\n---\nx").internal()).isFalse();
    }

    /** Silently treating "maybe" as false would hide a mistake about visibility. */
    @Test
    void warnsRatherThanGuessingAtANonBoolean() {
        MarkdownFile file = parse("a.md", "---\ninternal: maybe\n---\nx");

        assertThat(file.internal()).isFalse();
        assertThat(file.warnings()).anyMatch(w -> w.contains("maybe"));
    }

    @Test
    void warnsAboutAnUnknownDocumentTypeAndKeepsTheFallback() {
        MarkdownFile file = MarkdownFile.parse(
                "a.md", "---\ntype: SPREADSHEET\n---\nx", DocumentType.RUNBOOK);

        assertThat(file.documentType()).isEqualTo(DocumentType.RUNBOOK);
        assertThat(file.warnings()).anyMatch(w -> w.contains("SPREADSHEET"));
    }

    @Test
    void acceptsTypeInAnyCaseAndWithHyphens() {
        assertThat(parse("a.md", "---\ntype: tech-stack\n---\nx").documentType())
                .isEqualTo(DocumentType.TECH_STACK);
        assertThat(parse("a.md", "---\ntype: Decision\n---\nx").documentType())
                .isEqualTo(DocumentType.DECISION);
    }

    @Test
    void stripsQuotesFromValues() {
        assertThat(parse("a.md", "---\ntitle: \"Quoted title\"\n---\nx").title())
                .isEqualTo("Quoted title");
        assertThat(parse("a.md", "---\ntitle: 'Single'\n---\nx").title()).isEqualTo("Single");
    }

    /** A colon in the value must not be mistaken for the separator. */
    @Test
    void keepsAColonThatBelongsToTheValue() {
        assertThat(parse("a.md", "---\ntitle: Kafka: a primer\n---\nx").title())
                .isEqualTo("Kafka: a primer");
    }

    @Test
    void reportsAKeyItDoesNotRecognise() {
        MarkdownFile file = parse("a.md", "---\ntitle: A\nauthor: Ada\n---\nx");

        assertThat(file.title()).isEqualTo("A");
        assertThat(file.warnings()).anyMatch(w -> w.contains("author"));
    }

    /**
     * A file that opens with `---` and never closes it is much more likely to be a
     * mistake than a document that genuinely starts that way, so the parser says so
     * instead of swallowing the file.
     */
    @Test
    void warnsAboutAnUnclosedFenceAndKeepsTheContent() {
        MarkdownFile file = parse("a.md", "---\ntitle: A\n\nbody text");

        assertThat(file.warnings()).anyMatch(w -> w.contains("not closed"));
        assertThat(file.content()).contains("body text");
    }

    @Test
    void ignoresCommentsAndBlankLinesInFrontMatter() {
        MarkdownFile file = parse("a.md", """
                ---
                # a comment

                title: A
                ---
                x""");

        assertThat(file.title()).isEqualTo("A");
        assertThat(file.warnings()).isEmpty();
    }

    @Test
    void normalisesWindowsLineEndings() {
        MarkdownFile file = parse("a.md", "---\r\ntitle: A\r\n---\r\n\r\nbody");

        assertThat(file.title()).isEqualTo("A");
        assertThat(file.content()).isEqualTo("body");
    }

    @Test
    void handlesAnEmptyFile() {
        MarkdownFile file = parse("empty.md", "");

        assertThat(file.slug()).isEqualTo("empty");
        assertThat(file.title()).isEqualTo("Empty");
        assertThat(file.content()).isEmpty();
    }

    @Test
    void handlesFrontMatterWithNothingAfterIt() {
        MarkdownFile file = parse("a.md", "---\ntitle: Only metadata\n---\n");

        assertThat(file.title()).isEqualTo("Only metadata");
        assertThat(file.content()).isEmpty();
    }
}
