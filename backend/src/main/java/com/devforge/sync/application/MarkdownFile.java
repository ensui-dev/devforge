package com.devforge.sync.application;

import com.devforge.document.contract.DocumentType;

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
 * <p>Every key is optional. A file with no front matter at all is still a valid
 * document: the title is taken from its first heading, or failing that its filename.
 */
public record MarkdownFile(
        String slug,
        String title,
        String content,
        DocumentType documentType,
        boolean internal,
        /** Keys that were present but not understood, surfaced rather than ignored. */
        List<String> warnings
) {

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

        String slug = slugFrom(path);
        return new MarkdownFile(
                slug,
                title(front.get("title"), body, slug),
                body.strip(),
                type(front.get("type"), fallbackType, warnings),
                bool(front.get("internal"), warnings),
                List.copyOf(warnings));
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

    private static final java.util.Set<String> KNOWN_KEYS =
            java.util.Set.of("title", "type", "internal");

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
        return humanise(slug);
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
     * The slug is the file's name without its extension, lowercased.
     *
     * <p>Directories are dropped rather than folded into the slug: DevForge slugs
     * are flat within a workspace, and turning {@code runbooks/consumer-lag.md} into
     * {@code runbooks-consumer-lag} would bake the repository's layout into a URL
     * that outlives it. Two files with the same name in different directories
     * collide, which the planner reports rather than resolving silently.
     */
    public static String slugFrom(String path) {
        String name = path;
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        String slug = name.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? "untitled" : slug;
    }

    private static String humanise(String slug) {
        String spaced = slug.replace('-', ' ').strip();
        return spaced.isEmpty()
                ? "Untitled"
                : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
