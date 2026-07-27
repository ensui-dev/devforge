package com.devforge.sync.application;

import com.devforge.document.contract.DeclaredReference;
import com.devforge.document.contract.DocumentType;
import com.devforge.document.contract.ReferenceType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One markdown file from a repository, parsed into what a document needs.
 *
 * <p>Front matter is a deliberately tiny subset of YAML: a {@code ---} fence, then
 * {@code key: value} lines, then a closing fence. No nesting, no lists, no anchors,
 * no multi-line scalars.
 *
 * <p>That is a choice, not a shortcut. A real YAML parser would be another
 * dependency in a project that ships six, and would accept a great deal of syntax
 * whose meaning here would be undefined. A strict, small grammar can be read in a
 * minute and reports exactly what it did not understand.
 *
 * <pre>
 * ---
 * title: Event ingestion pipeline
 * type: ARCHITECTURE
 * internal: false
 * ---
 *
 * # Event ingestion pipeline
 * </pre>
 *
 * <p>Typed links are declared with one key per relationship, naming target slugs:
 *
 * <pre>
 * ---
 * title: Event ingestion pipeline
 * type: ARCHITECTURE
 * depends_on: kafka-topic-conventions, event-schema
 * implements: event-driven-design
 * ---
 * </pre>
 *
 * <p>A flat key per relationship rather than a nested {@code references:} list,
 * which would need list syntax this grammar deliberately does not have. It also
 * reads better: the relationship is the key, so a reader sees "depends on these"
 * rather than parsing a list of single-entry maps.
 *
 * <p>Every key is optional. A file with no front matter at all is still a valid
 * document: the title is taken from its first heading, or failing that its filename.
 */
public record MarkdownFile(
        String slug,
        String title,
        String content,
        DocumentType documentType,
        boolean internal,
        /**
         * Typed links this file declares, by target slug.
         *
         * <p>Empty means the file declared none, which is different from declaring
         * an empty set — a file that mentions no relationship is not asking for its
         * links to be managed. {@link #declaresReferences()} distinguishes the two.
         */
        List<DeclaredReference> references,
        /** Keys that were present but not understood, surfaced rather than ignored. */
        List<String> warnings
) {

    /**
     * Whether this file is managing its own links.
     *
     * <p>Opting in per file matters: a repository holding prose but no relationships
     * must not silently delete links someone made in the interface.
     */
    public boolean declaresReferences() {
        return !references.isEmpty();
    }

    private static final String FENCE = "---";

    /**
     * Parses a file's text.
     *
     * @param path    repository-relative path, used for the slug and as the last
     *                resort for a title
     * @param raw     file contents
     * @param fallbackType the type to use when the file does not declare one
     */
    public static MarkdownFile parse(String path, String raw, DocumentType fallbackType) {
        return parse(path, "", raw, fallbackType);
    }

    /**
     * @param documentPath the configured documentation folder, so the slug mirrors
     *                     the tree below it rather than below the repository root
     */
    public static MarkdownFile parse(
            String path,
            String documentPath,
            String raw,
            DocumentType fallbackType
    ) {
        String text = raw == null ? "" : raw.replace("\r\n", "\n");
        List<String> warnings = new java.util.ArrayList<>();

        Map<String, String> front = new LinkedHashMap<>();
        String body = text;

        if (text.startsWith(FENCE + "\n") || text.equals(FENCE)) {
            int close = text.indexOf("\n" + FENCE, FENCE.length());
            if (close < 0) {
                // An unterminated fence is far more likely to be a mistake than a
                // document that genuinely opens with `---`, so say so and treat the
                // whole file as body rather than silently swallowing it.
                warnings.add("front matter is not closed by a --- line; treated as content");
            } else {
                String block = text.substring(FENCE.length() + 1, close);
                body = afterFence(text, close);
                parseFrontMatter(block, front, warnings);
            }
        }

        String slug = slugFrom(path, documentPath);
        return new MarkdownFile(
                slug,
                title(front.get("title"), body, slug),
                body.strip(),
                type(front.get("type"), fallbackType, warnings),
                bool(front.get("internal"), warnings),
                references(front, slug, warnings),
                List.copyOf(warnings));
    }

    /**
     * Writes a document back out as a file, in the form {@link #parse} reads.
     *
     * <p>The inverse lives beside the parser deliberately. Two-way syncing means a
     * file DevForge wrote is a file DevForge will later read, and a grammar whose
     * two halves live apart is a grammar that will disagree with itself — the
     * round-trip test below this class only stays honest while both are here.
     *
     * <p>Every field is written explicitly rather than relying on the parser's
     * fallbacks. A title inferred from a heading is a guess that happened to be
     * right; once DevForge holds the answer, the file should say it.
     *
     * @param references the page's outgoing links, written one line per relationship
     */
    public static String render(
            String title,
            String content,
            DocumentType documentType,
            boolean internal,
            List<DeclaredReference> references
    ) {
        StringBuilder text = new StringBuilder(FENCE).append('\n');
        text.append("title: ").append(scalar(title)).append('\n');
        text.append("type: ").append(documentType.name()).append('\n');
        text.append("internal: ").append(internal).append('\n');

        // Grouped by relationship, in the enum's order, so a file rewritten twice
        // without changing produces identical bytes and therefore no commit.
        for (ReferenceType type : ReferenceType.values()) {
            List<String> targets = references.stream()
                    .filter(reference -> reference.referenceType() == type)
                    .map(DeclaredReference::targetSlug)
                    .distinct()
                    .sorted()
                    .toList();
            if (!targets.isEmpty()) {
                text.append(type.name().toLowerCase()).append(": ")
                        .append(String.join(", ", targets)).append('\n');
            }
        }

        text.append(FENCE).append("\n\n");
        text.append(content == null ? "" : content.strip().replace("\r\n", "\n"));
        // A trailing newline, because every other tool that touches this file
        // expects one and would otherwise add it as a spurious diff.
        text.append('\n');
        return text.toString();
    }

    /**
     * Where a slug's file belongs, below the configured documentation folder.
     *
     * <p>The inverse of {@link #slugFrom}, but only of the part that is invertible:
     * a slug written here reads back as itself, while a file named anything else
     * that happens to slugify the same way keeps its own name. Deciding which of the
     * two a given document should be written to is the mirror's job, not this one's.
     */
    public static String pathFor(String slug, String documentPath) {
        String folder = documentPath == null ? "" : documentPath.strip();
        folder = folder.replaceAll("^/+", "").replaceAll("/+$", "");
        return (folder.isEmpty() ? "" : folder + "/") + slug + ".md";
    }

    /**
     * A front-matter value, quoted only when leaving it bare would change it.
     *
     * <p>{@link #unquote} strips a matching pair of surrounding quotes, so a value
     * that already looks quoted has to be quoted again to survive; everything else
     * is left alone, which keeps the common file readable.
     */
    private static String scalar(String raw) {
        String value = raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ');
        boolean wouldChange = !value.equals(value.strip())
                || !value.equals(unquote(value));
        if (!wouldChange) {
            return value;
        }
        // Whichever quote the value does not end with, so no escaping is needed —
        // this grammar has none, and inventing one for a title is not worth it.
        return value.contains("\"") ? "'" + value + "'" : "\"" + value + "\"";
    }

    /** Skips the closing fence and the blank line that usually follows it. */
    private static String afterFence(String text, int close) {
        int from = close + 1 + FENCE.length();
        if (from < text.length() && text.charAt(from) == '\n') {
            from++;
        }
        return from >= text.length() ? "" : text.substring(from);
    }

    private static void parseFrontMatter(
            String block,
            Map<String, String> into,
            List<String> warnings
    ) {
        for (String line : block.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }

            int colon = trimmed.indexOf(':');
            if (colon <= 0) {
                warnings.add("could not read front matter line: " + trimmed);
                continue;
            }

            String key = trimmed.substring(0, colon).strip().toLowerCase();
            String value = unquote(trimmed.substring(colon + 1).strip());
            if (!KNOWN_KEYS.contains(key)) {
                warnings.add("unknown front matter key: " + key);
                continue;
            }
            into.put(key, value);
        }
    }

    /** Reference relationships, lowercased, as front matter spells them. */
    private static final java.util.Map<String, ReferenceType> REFERENCE_KEYS =
            java.util.Arrays.stream(ReferenceType.values())
                    .collect(java.util.stream.Collectors.toMap(
                            type -> type.name().toLowerCase(), type -> type));

    private static final java.util.Set<String> KNOWN_KEYS = java.util.stream.Stream.concat(
                    java.util.stream.Stream.of("title", "type", "internal"),
                    REFERENCE_KEYS.keySet().stream())
            .collect(java.util.stream.Collectors.toUnmodifiableSet());

    /**
     * Reads the relationship keys into declared links.
     *
     * <p>Targets are comma separated and slugified the same way a path is, so a link
     * may name a slug, a filename, or a path with folders —
     * {@code runbooks/consumer-lag}, {@code runbooks/Consumer Lag.md}, and
     * {@code runbooks/consumer-lag.md} all resolve to the same page.
     */
    private static List<DeclaredReference> references(
            Map<String, String> front,
            String sourceSlug,
            List<String> warnings
    ) {
        List<DeclaredReference> declared = new java.util.ArrayList<>();
        java.util.Set<String> seen = new java.util.LinkedHashSet<>();

        for (Map.Entry<String, ReferenceType> entry : REFERENCE_KEYS.entrySet()) {
            String value = front.get(entry.getKey());
            if (value == null || value.isBlank()) {
                continue;
            }
            for (String raw : value.split(",")) {
                String target = slugFrom(raw.strip());
                if (target.isBlank() || target.equals("untitled")) {
                    continue;
                }
                if (target.equals(sourceSlug)) {
                    warnings.add("%s: a document cannot reference itself".formatted(entry.getKey()));
                    continue;
                }
                // The same link declared twice is a duplicate, not two links.
                if (seen.add(entry.getKey() + ":" + target)) {
                    declared.add(new DeclaredReference(entry.getValue(), target));
                }
            }
        }
        return List.copyOf(declared);
    }

    private static String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    /**
     * Title from front matter, else the first markdown heading, else the filename.
     *
     * <p>The heading fallback matters: most documentation already opens with its own
     * title, and requiring it to be repeated in front matter would be the kind of
     * duplication that goes stale.
     */
    private static String title(String declared, String body, String slug) {
        if (declared != null && !declared.isBlank()) {
            return declared.strip();
        }
        for (String line : body.split("\n", -1)) {
            String trimmed = line.strip();
            if (trimmed.startsWith("# ")) {
                return trimmed.substring(2).strip();
            }
        }
        // The filename, not the whole path: a slug of `runbooks/consumer-lag` should
        // fall back to "Consumer lag", not "Runbooks/consumer lag".
        int lastSlash = slug.lastIndexOf('/');
        return humanise(lastSlash >= 0 ? slug.substring(lastSlash + 1) : slug);
    }

    private static DocumentType type(String declared, DocumentType fallback, List<String> warnings) {
        if (declared == null || declared.isBlank()) {
            return fallback;
        }
        try {
            return DocumentType.valueOf(declared.strip().toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException e) {
            warnings.add("unknown document type '%s'; used %s".formatted(declared, fallback));
            return fallback;
        }
    }

    private static boolean bool(String declared, List<String> warnings) {
        if (declared == null || declared.isBlank()) {
            return false;
        }
        String value = declared.strip().toLowerCase();
        if (value.equals("true") || value.equals("yes")) {
            return true;
        }
        if (value.equals("false") || value.equals("no")) {
            return false;
        }
        warnings.add("could not read '%s' as true or false; treated as false".formatted(declared));
        return false;
    }

    /**
     * The slug is the file's path below the documentation folder, without its
     * extension, with each segment lowercased and hyphenated.
     *
     * <pre>
     * docs/design.md                 -> design
     * docs/runbooks/consumer-lag.md  -> runbooks/consumer-lag
     * </pre>
     *
     * <p>The folder structure is kept rather than flattened. An earlier version
     * dropped directories, on the reasoning that a URL should not bake in a layout
     * that will be reorganised — but that made two files of the same name in
     * different folders collide, which is not rare at all: a repository with a
     * {@code README.md} at its root and another in a subdirectory hits it
     * immediately. Mirroring the tree is also what someone moving documentation out
     * of a folder-based tool expects.
     *
     * @param path         repository-relative path of the file
     * @param documentPath the configured documentation folder, stripped from the
     *                     front so the slug is relative to it rather than to the
     *                     repository root
     */
    public static String slugFrom(String path, String documentPath) {
        String relative = relativise(path, documentPath);

        int dot = relative.lastIndexOf('.');
        int lastSlash = relative.lastIndexOf('/');
        // Only an extension on the final segment; a dot in a folder name is not one.
        if (dot > lastSlash + 1) {
            relative = relative.substring(0, dot);
        }

        String slug = java.util.Arrays.stream(relative.split("/"))
                .map(MarkdownFile::slugifySegment)
                .filter(segment -> !segment.isEmpty())
                .collect(java.util.stream.Collectors.joining("/"));

        return slug.isEmpty() ? "untitled" : slug;
    }

    /** Kept for callers that have already relativised the path. */
    public static String slugFrom(String path) {
        return slugFrom(path, "");
    }

    private static String relativise(String path, String documentPath) {
        if (documentPath == null || documentPath.isEmpty()) {
            return path;
        }
        String prefix = documentPath.endsWith("/") ? documentPath : documentPath + "/";
        return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
    }

    private static String slugifySegment(String segment) {
        return segment.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
    }

    private static String humanise(String slug) {
        String spaced = slug.replace('-', ' ').strip();
        return spaced.isEmpty()
                ? "Untitled"
                : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
