package com.devforge.sync.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * What a git host said it just pushed.
 *
 * <p>Read for one reason: <strong>the commit</strong>. Syncing the branch name
 * instead looks equivalent and is not — a host serves {@code archive/main.zip}
 * from a cache, and a webhook arrives at the instant the push lands, so the
 * download can return the tree from <em>before</em> the push that triggered it.
 * The workspace then sits one commit behind and looks perfectly healthy, because
 * every push applies the previous one.
 *
 * <p>Naming the commit is immune to that, and is the more honest thing to do
 * anyway: it applies the tree that was actually pushed rather than whatever the
 * branch happens to point at by the time the download runs.
 *
 * <p>The branch matters too. Without it, a push to any branch syncs the configured
 * one, so a draft branch triggers work that has nothing to do with it.
 *
 * <p>GitHub, GitLab, Gitea and Forgejo all send {@code ref} and {@code after} at
 * the top level, so one shape covers every host DevForge claims to support.
 *
 * @param ref      the full ref that moved, such as {@code refs/heads/main}
 * @param commitId the ref's new tip
 */
public record PushEvent(String ref, String commitId) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** All zeros is how a host says a branch was deleted; there is no tree to fetch. */
    private static final String DELETED = "0000000000000000000000000000000000000000";

    /**
     * @return empty for a body that is not a push — a ping, a pull request, a
     *         branch deletion, or anything unparseable. The caller falls back to
     *         syncing the configured branch, which is what happened before any of
     *         this was read and is never worse than refusing.
     */
    public static Optional<PushEvent> parse(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return Optional.empty();
        }

        JsonNode root;
        try {
            root = MAPPER.readTree(new String(payload, StandardCharsets.UTF_8));
        } catch (JacksonException notJson) {
            return Optional.empty();
        }

        if (root == null || !root.isObject()) {
            return Optional.empty();
        }

        String ref = text(root, "ref");
        String after = text(root, "after");
        // GitLab sends `checkout_sha` alongside `after`; they agree on a push, and
        // this is only a fallback for a host that omits one of them.
        String commit = after != null ? after : text(root, "checkout_sha");

        if (ref == null || commit == null || commit.equals(DELETED)) {
            return Optional.empty();
        }
        return Optional.of(new PushEvent(ref, commit));
    }

    /** Whether this push moved the branch a workspace follows. */
    public boolean movedBranch(String branch) {
        return branch != null
                && (ref.equals("refs/heads/" + branch) || ref.equals(branch));
    }

    /** The branch's short name, for saying which one was ignored. */
    public String branchName() {
        return ref.startsWith("refs/heads/") ? ref.substring("refs/heads/".length()) : ref;
    }

    private static String text(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual() || value.asString().isBlank()) {
            return null;
        }
        return value.asString();
    }
}
