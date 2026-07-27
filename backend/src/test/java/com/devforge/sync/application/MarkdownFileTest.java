package com.devforge.sync.application;

import com.devforge.document.contract.DeclaredReference;
import com.devforge.document.contract.DocumentType;
import com.devforge.document.contract.ReferenceType;
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

    // ---------------------------------------------------------------- references

    @Test
    void readsTypedLinksFromRelationshipKeys() {
        MarkdownFile file = parse("docs/ingestion.md", """
                ---
                title: Event ingestion
                depends_on: kafka-topic-conventions
                implements: event-driven-design
                ---
                body""");

        assertThat(file.references()).containsExactlyInAnyOrder(
                new DeclaredReference(ReferenceType.DEPENDS_ON, "kafka-topic-conventions"),
                new DeclaredReference(ReferenceType.IMPLEMENTS, "event-driven-design"));
        assertThat(file.declaresReferences()).isTrue();
        assertThat(file.warnings()).isEmpty();
    }

    @Test
    void readsSeveralTargetsFromOneKey() {
        MarkdownFile file = parse("a.md",
                "---\ndepends_on: one, two , three\n---\nbody");

        assertThat(file.references()).extracting(DeclaredReference::targetSlug)
                .containsExactly("one", "two", "three");
        assertThat(file.references()).allSatisfy(reference ->
                assertThat(reference.referenceType()).isEqualTo(ReferenceType.DEPENDS_ON));
    }

    @Test
    void supportsEveryRelationship() {
        MarkdownFile file = parse("a.md", """
                ---
                related: r
                depends_on: d
                implements: i
                documents: doc
                supersedes: s
                ---
                body""");

        assertThat(file.references()).extracting(DeclaredReference::referenceType)
                .containsExactlyInAnyOrder(
                        ReferenceType.RELATED, ReferenceType.DEPENDS_ON, ReferenceType.IMPLEMENTS,
                        ReferenceType.DOCUMENTS, ReferenceType.SUPERSEDES);
    }

    /** A link may name the file rather than the slug; both resolve the same way. */
    @Test
    void slugifiesTargetsSoAFilenameWorksToo() {
        MarkdownFile file = parse("a.md",
                "---\ndepends_on: Kafka Topic Conventions.md, already-a-slug\n---\nbody");

        assertThat(file.references()).extracting(DeclaredReference::targetSlug)
                .containsExactly("kafka-topic-conventions", "already-a-slug");
    }

    /**
     * Declaring nothing is not the same as declaring an empty set. A repository of
     * prose must not silently delete links made in the interface.
     */
    @Test
    void distinguishesDeclaringNoLinksFromDeclaringNone() {
        assertThat(parse("a.md", "# Just prose").declaresReferences()).isFalse();
        assertThat(parse("a.md", "---\ntitle: A\n---\nbody").declaresReferences()).isFalse();
        assertThat(parse("a.md", "---\ndepends_on:\n---\nbody").declaresReferences()).isFalse();
    }

    @Test
    void refusesASelfReference() {
        MarkdownFile file = parse("docs/loop.md", "---\ndepends_on: loop\n---\nbody");

        assertThat(file.references()).isEmpty();
        assertThat(file.warnings()).anyMatch(w -> w.contains("cannot reference itself"));
    }

    /** The same link twice is a duplicate, not two links. */
    @Test
    void collapsesADuplicatedTarget() {
        MarkdownFile file = parse("a.md", "---\ndepends_on: same, same\n---\nbody");

        assertThat(file.references()).hasSize(1);
    }

    /** Two relationships to one target are two different links, and both are kept. */
    @Test
    void keepsTwoDifferentRelationshipsToTheSameTarget() {
        MarkdownFile file = parse("a.md",
                "---\ndepends_on: shared\nrelated: shared\n---\nbody");

        assertThat(file.references()).hasSize(2);
    }

    @Test
    void ignoresEmptyEntriesInAList() {
        MarkdownFile file = parse("a.md", "---\ndepends_on: one, , two,\n---\nbody");

        assertThat(file.references()).extracting(DeclaredReference::targetSlug)
                .containsExactly("one", "two");
    }
}
