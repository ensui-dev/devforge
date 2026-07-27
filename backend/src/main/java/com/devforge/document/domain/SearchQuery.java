package com.devforge.document.domain;

import java.util.Arrays;
import java.util.List;

/**
 * Turns what somebody typed into something PostgreSQL will search with.
 *
 * <p>The search used {@code websearch_to_tsquery}, which matches whole lexemes and
 * nothing else. That is correct and useless while typing: "authenti" finds no page
 * about authentication, because the stemmer reduced the document to {@code authent}
 * and the query to {@code authenti}, and the two are simply different words. Every
 * search only worked once the word was finished.
 *
 * <p>So every term becomes a prefix — {@code authenti:*} matches {@code authent}'s
 * document the moment enough has been typed to be unambiguous. Terms are combined
 * with AND, which is what people expect from two words: narrowing, not widening.
 *
 * <p>Built here rather than in SQL because the input is untrusted and
 * {@code to_tsquery} is not forgiving — it throws on a stray operator, and a
 * search box is exactly where stray operators arrive. Only letters and digits
 * survive, so there is nothing left to be an operator.
 *
 * <p>The cost is that {@code websearch_to_tsquery}'s own syntax is gone: quoted
 * phrases and {@code -exclusions} are now read as ordinary words. That syntax was
 * undiscoverable in a search box with no help text, and prefix matching is worth
 * more than a feature nobody could find.
 */
public final class SearchQuery {

    /** Long enough to be worth a query; shorter matches most of the workspace. */
    private static final int MIN_TERM = 1;

    private SearchQuery() {
    }

    /**
     * @return a {@code to_tsquery} expression, or null when nothing usable was
     *         typed — punctuation alone is not a search, and passing it on would
     *         have PostgreSQL raise a syntax error over an empty query
     */
    public static String toTsQuery(String typed) {
        List<String> terms = terms(typed);
        if (terms.isEmpty()) {
            return null;
        }
        return String.join(" & ", terms.stream().map(term -> term + ":*").toList());
    }

    /**
     * The same words, for matching against a title directly.
     *
     * <p>Full-text search is stemmed and word-based, so it cannot find a fragment
     * inside a word — "lag" does not match "Consumer-lag-runbook" as one token.
     * A plain substring match on the title covers that, and a workspace's titles
     * are few enough that scanning them costs nothing worth measuring.
     */
    public static String toLikePattern(String typed) {
        List<String> terms = terms(typed);
        return terms.isEmpty() ? null : "%" + String.join("%", terms) + "%";
    }

    /**
     * Words, stripped of everything that is not a letter or a digit.
     *
     * <p>Accents and non-Latin scripts are kept: {@code \p{L}} is every letter in
     * Unicode, not just the ASCII ones, and a handbook written in Japanese is not a
     * hypothetical for a self-hosted product.
     */
    private static List<String> terms(String typed) {
        if (typed == null || typed.isBlank()) {
            return List.of();
        }
        return Arrays.stream(typed.strip().split("\\s+"))
                .map(term -> term.replaceAll("[^\\p{L}\\p{N}]", ""))
                .filter(term -> term.length() >= MIN_TERM)
                .limit(10)
                .toList();
    }
}
