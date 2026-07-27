package com.devforge.sync.application;

import com.devforge.document.contract.DocumentDraft;
import com.devforge.document.contract.DocumentType;
import com.devforge.sync.domain.DeletionPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Turning a fetched snapshot into decisions.
 *
 * <p>This is a pure function, which is the point: the cases that actually go wrong
 * in a sync — a mis-set path making every file look deleted, two files claiming one
 * slug, a repository full of things that are not documentation — are all decided
 * here, so they can be tested without a git host or a database.
 */
class SyncPlanTest {

    private SourceFile file(String path, String text) {
        return new SourceFile(path, text);
    }

    private SyncPlan plan(List<SourceFile> files, Set<String> existing, String documentPath) {
        return SyncPlan.from(
                new SourceSnapshot("abc123", files),
                existing,
                documentPath,
                DocumentType.GENERAL,
                DeletionPolicy.ARCHIVE);
    }

    @Test
    void turnsMarkdownFilesIntoDrafts() {
        SyncPlan result = plan(
                List.of(
                        file("docs/design.md", "---\ntitle: Design\ntype: ARCHITECTURE\n---\nbody"),
                        file("docs/runbook.md", "# Runbook\n\nsteps")),
                Set.of(),
                "docs");

        assertThat(result.drafts()).extracting(DocumentDraft::slug)
                .containsExactly("design", "runbook");
        assertThat(result.drafts().getFirst().documentType()).isEqualTo(DocumentType.ARCHITECTURE);
        assertThat(result.archived()).isEmpty();
        assertThat(result.problems()).isEmpty();
    }

    /** A repository holds a great deal that is not documentation. */
    @Test
    void ignoresEverythingThatIsNotMarkdown()  {
        SyncPlan result = plan(
                List.of(
                        file("docs/design.md", "# Design"),
                        file("docs/logo.png", "binary"),
                        file("docs/config.yaml", "key: value"),
                        file("docs/notes.markdown", "# Notes")),
                Set.of(),
                "docs");

        assertThat(result.drafts()).extracting(DocumentDraft::slug)
                .containsExactly("design", "notes");
    }

    /**
     * The bug a plain startsWith would cause: `docs` must not match `docsite`.
     */
    @Test
    void comparesTheDocumentPathBySegmentNotByPrefix() {
        SyncPlan result = plan(
                List.of(
                        file("docs/wanted.md", "# Wanted"),
                        file("docsite/unwanted.md", "# Unwanted"),
                        file("docs/nested/deep.md", "# Deep")),
                Set.of(),
                "docs");

        assertThat(result.drafts()).extracting(DocumentDraft::slug)
                .containsExactly("deep", "wanted");
    }

    @Test
    void takesTheWholeRepositoryWhenNoPathIsConfigured() {
        SyncPlan result = plan(
                List.of(file("README.md", "# Readme"), file("docs/a.md", "# A")),
                Set.of(),
                "");

        assertThat(result.drafts()).hasSize(2);
    }

    @Test
    void archivesSlugsTheSourceNoLongerContains() {
        SyncPlan result = plan(
                List.of(file("docs/kept.md", "# Kept")),
                Set.of("kept", "removed-upstream", "also-gone"),
                "docs");

        assertThat(result.archived()).containsExactly("also-gone", "removed-upstream");
    }

    @Test
    void archivesNothingUnderTheIgnorePolicy() {
        SyncPlan result = SyncPlan.from(
                new SourceSnapshot("abc", List.of(file("docs/kept.md", "# Kept"))),
                Set.of("kept", "hand-written"),
                "docs",
                DocumentType.GENERAL,
                DeletionPolicy.IGNORE);

        assertThat(result.archived()).isEmpty();
    }

    /**
     * Slugs are flat within a workspace, so two files with the same name in
     * different directories collide. Reported rather than silently letting whichever
     * sorted last overwrite the other.
     */
    @Test
    void reportsASlugCollisionInsteadOfOverwriting() {
        SyncPlan result = plan(
                List.of(
                        file("docs/architecture/overview.md", "# Architecture overview"),
                        file("docs/runbooks/overview.md", "# Runbook overview")),
                Set.of(),
                "docs");

        assertThat(result.drafts()).hasSize(1);
        assertThat(result.drafts().getFirst().title()).isEqualTo("Architecture overview");
        assertThat(result.problems()).anySatisfy(problem -> assertThat(problem)
                .contains("runbooks/overview.md")
                .contains("already taken")
                .contains("architecture/overview.md"));
    }

    /** Front-matter warnings must reach the operator, with the file that caused them. */
    @Test
    void surfacesParseWarningsAgainstTheirFile() {
        SyncPlan result = plan(
                List.of(file("docs/a.md", "---\ntype: NONSENSE\nauthor: Ada\n---\nbody")),
                Set.of(),
                "docs");

        assertThat(result.drafts()).hasSize(1);
        assertThat(result.problems()).hasSize(2);
        assertThat(result.problems()).allSatisfy(p -> assertThat(p).startsWith("docs/a.md:"));
    }

    /**
     * The dangerous case. A wrong document path returns nothing, which under the
     * archive policy would withdraw every page in the workspace. The plan states
     * that plainly so the caller can refuse it.
     */
    @Test
    void anEmptySnapshotPlansToArchiveEverything() {
        SyncPlan result = plan(List.of(), Set.of("one", "two", "three"), "wrong-path");

        assertThat(result.drafts()).isEmpty();
        assertThat(result.archived()).containsExactly("one", "three", "two");
        assertThat(result.isEmpty()).isFalse();
    }

    @Test
    void aSnapshotMatchingTheWorkspaceExactlyIsStillAPlan() {
        SyncPlan result = plan(
                List.of(file("docs/a.md", "# A")),
                Set.of("a"),
                "docs");

        assertThat(result.drafts()).hasSize(1);
        assertThat(result.archived()).isEmpty();
    }

    @Test
    void reportsNothingToDoForAnEmptyRepositoryAndEmptyWorkspace() {
        SyncPlan result = plan(List.of(), Set.of(), "docs");

        assertThat(result.isEmpty()).isTrue();
    }

    /** Two syncs of one snapshot must produce the same writes in the same order. */
    @Test
    void ordersDraftsDeterministically() {
        List<SourceFile> shuffled = List.of(
                file("docs/zebra.md", "# Zebra"),
                file("docs/alpha.md", "# Alpha"),
                file("docs/middle.md", "# Middle"));

        assertThat(plan(shuffled, Set.of(), "docs").drafts())
                .extracting(DocumentDraft::slug)
                .containsExactly("alpha", "middle", "zebra");
    }

    @Test
    void appliesTheConfiguredDefaultTypeToFilesThatDeclareNone() {
        SyncPlan result = SyncPlan.from(
                new SourceSnapshot("abc", List.of(file("docs/a.md", "# A"))),
                Set.of(),
                "docs",
                DocumentType.RUNBOOK,
                DeletionPolicy.ARCHIVE);

        assertThat(result.drafts().getFirst().documentType()).isEqualTo(DocumentType.RUNBOOK);
    }

    @Test
    void carriesTheInternalFlagFromFrontMatter() {
        SyncPlan result = plan(
                List.of(file("docs/private.md", "---\ninternal: true\n---\nsecret")),
                Set.of(),
                "docs");

        assertThat(result.drafts().getFirst().internal()).isTrue();
    }
}
