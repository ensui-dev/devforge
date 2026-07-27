package com.devforge.sync.application;

import com.devforge.document.contract.DeclaredReference;
import com.devforge.document.contract.DocumentDraft;
import com.devforge.document.contract.DocumentType;
import com.devforge.sync.domain.DeletionPolicy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * What a fetched snapshot means for a workspace, decided before anything is written.
 *
 * <p>Planning is separated from applying so that the hard part is a pure function:
 * given the files that arrived and the slugs already present, work out what to
 * upsert and what has disappeared. No database, no network, no ordering surprises —
 * which is what makes the interesting cases (a collision, an empty snapshot, a
 * misconfigured path) testable in isolation.
 *
 * @param documents documents to write, each with the links its file declared, in a
 *                  stable order so a sync is reproducible
 * @param archived  slugs present in the workspace but absent from the source
 * @param problems  files that could not be used, and why
 */
public record SyncPlan(
        List<PlannedDocument> documents,
        List<String> archived,
        List<String> problems
) {

    /**
     * A document to write, with the links its file declared.
     *
     * @param references empty when the file declared none, which is different from
     *                   declaring an empty set — see {@link #managesReferences}
     */
    public record PlannedDocument(DocumentDraft draft, List<DeclaredReference> references) {

        /**
         * Whether the repository is managing this page's outgoing links.
         *
         * <p>Opt-in per file. A repository of prose that mentions no relationships
         * must not silently delete links someone made in the interface, so only a
         * file that declares at least one has its links reconciled.
         */
        public boolean managesReferences() {
            return !references.isEmpty();
        }
    }

    /** The drafts alone, for callers that do not care about links. */
    public List<DocumentDraft> drafts() {
        return documents.stream().map(PlannedDocument::draft).toList();
    }

    /** Only markdown is considered; a repository holds plenty that is not documentation. */
    private static boolean isMarkdown(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }

    /**
     * Builds a plan.
     *
     * @param snapshot      what the source returned
     * @param existingSlugs every slug already in the workspace
     * @param documentPath  the configured subdirectory, '' for the whole repository
     */
    public static SyncPlan from(
            SourceSnapshot snapshot,
            Set<String> existingSlugs,
            String documentPath,
            DocumentType defaultType,
            DeletionPolicy deletionPolicy
    ) {
        List<String> problems = new ArrayList<>();
        Map<String, PlannedDocument> bySlug = new HashMap<>();
        Map<String, String> pathBySlug = new HashMap<>();

        List<SourceFile> considered = snapshot.files().stream()
                .filter(file -> isMarkdown(file.path()))
                .filter(file -> withinPath(file.path(), documentPath))
                .sorted(Comparator.comparing(SourceFile::path))
                .toList();

        for (SourceFile file : considered) {
            MarkdownFile parsed =
                    MarkdownFile.parse(file.path(), documentPath, file.text(), defaultType);
            parsed.warnings().forEach(warning -> problems.add(file.path() + ": " + warning));

            String existingPath = pathBySlug.get(parsed.slug());
            if (existingPath != null) {
                // Slugs are flat within a workspace, so two files with the same name
                // in different directories would silently overwrite one another.
                // Reported and skipped rather than resolved by an arbitrary rule.
                problems.add("%s: slug '%s' is already taken by %s; skipped"
                        .formatted(file.path(), parsed.slug(), existingPath));
                continue;
            }

            pathBySlug.put(parsed.slug(), file.path());
            bySlug.put(parsed.slug(), new PlannedDocument(
                    new DocumentDraft(
                            parsed.slug(),
                            parsed.title(),
                            parsed.content(),
                            parsed.documentType(),
                            parsed.internal()),
                    parsed.references()));
        }

        // Sorted so two syncs of the same snapshot produce the same order of writes,
        // which makes the audit log readable and the tests deterministic.
        List<PlannedDocument> documents = bySlug.values().stream()
                .sorted(Comparator.comparing(planned -> planned.draft().slug()))
                .toList();

        Set<String> missing = new TreeSet<>(existingSlugs);
        missing.removeAll(bySlug.keySet());

        List<String> archived = deletionPolicy == DeletionPolicy.IGNORE
                ? List.of()
                : List.copyOf(missing);

        return new SyncPlan(documents, archived, List.copyOf(problems));
    }

    /**
     * Whether a path lies under the configured document path.
     *
     * <p>Compared segment-wise: a configured path of {@code docs} must match
     * {@code docs/a.md} but not {@code docsite/a.md}, which a plain
     * {@code startsWith} would.
     */
    private static boolean withinPath(String path, String documentPath) {
        if (documentPath == null || documentPath.isEmpty()) {
            return true;
        }
        return path.equals(documentPath) || path.startsWith(documentPath + "/");
    }

    public boolean isEmpty() {
        return documents.isEmpty() && archived.isEmpty();
    }
}
