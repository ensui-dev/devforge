package com.devforge.sync.application;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading what a git host said it pushed.
 *
 * <p>The reason this exists at all is the commit id. Syncing the branch name looks
 * equivalent and is not: a host serves the branch archive from a cache, and a
 * webhook arrives the instant the push lands, so the download can return the tree
 * from before the push that triggered it — leaving the workspace one commit behind
 * and looking perfectly healthy, because every push applies the previous one.
 */
class PushEventTest {

    private static byte[] json(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    /** GitHub, GitLab, Gitea and Forgejo all send this shape. */
    @Test
    void readsTheBranchAndTheCommit() {
        var push = PushEvent.parse(json("""
                {"ref": "refs/heads/main", "after": "9f8e7d6c5b4a", "before": "1234567"}"""));

        assertThat(push).isPresent();
        assertThat(push.get().ref()).isEqualTo("refs/heads/main");
        assertThat(push.get().commitId()).isEqualTo("9f8e7d6c5b4a");
    }

    @Test
    void fallsBackToCheckoutShaForAHostThatOmitsAfter() {
        var push = PushEvent.parse(json("""
                {"ref": "refs/heads/main", "checkout_sha": "abc123"}"""));

        assertThat(push).isPresent();
        assertThat(push.get().commitId()).isEqualTo("abc123");
    }

    // ------------------------------------------------------------------ matching

    @Test
    void recognisesTheBranchAWorkspaceFollows() {
        var push = PushEvent.parse(json("""
                {"ref": "refs/heads/main", "after": "abc"}""")).orElseThrow();

        assertThat(push.movedBranch("main")).isTrue();
        assertThat(push.movedBranch("draft")).isFalse();
        assertThat(push.movedBranch(null)).isFalse();
    }

    /** A tag is not a branch, whatever it is called. */
    @Test
    void doesNotMistakeATagForABranch() {
        var push = PushEvent.parse(json("""
                {"ref": "refs/tags/main", "after": "abc"}""")).orElseThrow();

        assertThat(push.movedBranch("main")).isFalse();
    }

    @Test
    void namesTheBranchForAMessageAboutIt() {
        assertThat(PushEvent.parse(json("""
                {"ref": "refs/heads/feature/redesign", "after": "abc"}"""))
                .orElseThrow().branchName())
                .isEqualTo("feature/redesign");
    }

    // ----------------------------------------------------------------- refusals

    /**
     * Every one of these falls back to syncing the configured branch, which is
     * what happened before any of this was read — never worse than refusing.
     */
    @Test
    void answersNothingForABodyThatIsNotAPush() {
        assertThat(PushEvent.parse(json("""
                {"zen": "Design for failure.", "hook_id": 1}"""))).isEmpty();
        assertThat(PushEvent.parse(json("{}"))).isEmpty();
        assertThat(PushEvent.parse(json("not json at all"))).isEmpty();
        assertThat(PushEvent.parse(json("[1, 2, 3]"))).isEmpty();
        assertThat(PushEvent.parse(new byte[0])).isEmpty();
        assertThat(PushEvent.parse(null)).isEmpty();
    }

    /** A deleted branch has no tree to fetch; the zeros are how a host says so. */
    @Test
    void answersNothingForABranchDeletion() {
        assertThat(PushEvent.parse(json("""
                {"ref": "refs/heads/old", "after": "0000000000000000000000000000000000000000"}""")))
                .isEmpty();
    }

    @Test
    void answersNothingWhenAFieldIsTheWrongShape() {
        assertThat(PushEvent.parse(json("""
                {"ref": 7, "after": "abc"}"""))).isEmpty();
        assertThat(PushEvent.parse(json("""
                {"ref": "refs/heads/main", "after": ""}"""))).isEmpty();
    }
}
