package com.devforge.document.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Turning a search box into something PostgreSQL will accept.
 *
 * <p>Two properties matter. It must never produce something {@code to_tsquery}
 * refuses — a search box is where stray operators arrive, and a syntax error
 * reaching the user as a 500 is not a search result. And every term must be a
 * prefix, because a word should find its page before it has finished being typed.
 */
class SearchQueryTest {

    @Test
    void makesEveryTermAPrefix() {
        assertThat(SearchQuery.toTsQuery("authenti")).isEqualTo("authenti:*");
    }

    /** Two words narrow the search; they do not widen it. */
    @Test
    void combinesTermsWithAnd() {
        assertThat(SearchQuery.toTsQuery("consumer lag")).isEqualTo("consumer:* & lag:*");
    }

    @Test
    void collapsesRepeatedWhitespace() {
        assertThat(SearchQuery.toTsQuery("  consumer   lag  ")).isEqualTo("consumer:* & lag:*");
    }

    // ------------------------------------------------------------------ safety

    /**
     * The reason this is built in Java rather than assembled in SQL: every one of
     * these is a syntax error to {@code to_tsquery}, and all of them are things a
     * person types into a search box without thinking about it.
     */
    @Test
    void stripsEverythingThatCouldBeAnOperator() {
        assertThat(SearchQuery.toTsQuery("auth & | ! ( ) : * <->")).isEqualTo("auth:*");
        assertThat(SearchQuery.toTsQuery("a:b")).isEqualTo("ab:*");
        assertThat(SearchQuery.toTsQuery("'quoted'")).isEqualTo("quoted:*");
        assertThat(SearchQuery.toTsQuery("100%")).isEqualTo("100:*");
    }

    @Test
    void answersNothingWhenNoWordsSurvive() {
        assertThat(SearchQuery.toTsQuery("!!! ???")).isNull();
        assertThat(SearchQuery.toTsQuery("   ")).isNull();
        assertThat(SearchQuery.toTsQuery("")).isNull();
        assertThat(SearchQuery.toTsQuery(null)).isNull();
    }

    /** A pasted paragraph is not a search; it is a way to make one query expensive. */
    @Test
    void stopsAfterAReasonableNumberOfTerms() {
        String many = "a b c d e f g h i j k l m n o p";

        assertThat(SearchQuery.toTsQuery(many).split(" & ")).hasSize(10);
    }

    /** A self-hosted handbook is not necessarily written in English, or in Latin. */
    @Test
    void keepsLettersFromEveryScript() {
        assertThat(SearchQuery.toTsQuery("café")).isEqualTo("café:*");
        assertThat(SearchQuery.toTsQuery("設計")).isEqualTo("設計:*");
    }

    // ------------------------------------------------------------------ substrings

    /**
     * The half full-text cannot do: find a fragment inside a word. No amount of
     * stemming makes "lag" a token of "consumer-lag".
     */
    @Test
    void buildsAPatternThatMatchesInsideAWord() {
        assertThat(SearchQuery.toLikePattern("lag")).isEqualTo("%lag%");
    }

    /** Terms may be separated by anything in the title, so the gap is a wildcard. */
    @Test
    void allowsAnythingBetweenTerms() {
        assertThat(SearchQuery.toLikePattern("consumer lag")).isEqualTo("%consumer%lag%");
    }

    @Test
    void hasNoPatternWhenThereAreNoWords() {
        assertThat(SearchQuery.toLikePattern("!!!")).isNull();
    }
}
