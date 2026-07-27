package com.devforge.sync.application;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Writing to a workspace's hosted repository, as the mirror needs it.
 *
 * <p>The port exists so that deciding <em>what</em> a commit should contain — which
 * path a slug belongs at, what the file says, whether anything moved — is separable
 * from the JGit machinery that makes it a commit. The first is documentation
 * policy and is tested with no repository at all; the second is plumbing and is
 * tested against a real one.
 *
 * <p>Deliberately has no notion of a document. It moves paths and bytes, which is
 * all a repository knows about.
 */
public interface RepositoryMirror {

    /**
     * Whether DevForge holds a repository for this workspace.
     *
     * <p>The gate on the whole feature. A repository exists because somebody pushed
     * or cloned, so this answers "does anyone use git for this workspace?" without
     * a setting to forget — and stops every workspace on an instance from growing a
     * repository nobody asked for the first time a page is edited.
     */
    boolean hosts(UUID workspaceId);

    /**
     * Every markdown file on the default branch, by repository-relative path.
     *
     * <p>Returned rather than searched here because matching a path to a document is
     * the mirror's decision: the same slug can be spelled several ways as a filename.
     */
    List<String> markdownPaths(UUID workspaceId);

    /**
     * Commits the change on top of the default branch.
     *
     * @return false when the tree it produced was identical to the one already
     *         there, which is not a failure — it is the same rule as a save that
     *         changes nothing, and git refuses such a commit too
     */
    boolean commit(UUID workspaceId, MirrorChange change);

    /**
     * @param written full contents by repository-relative path
     * @param removed paths to delete, which may not exist
     */
    record MirrorChange(
            Map<String, String> written,
            Set<String> removed,
            Author author,
            String message
    ) {
    }

    /**
     * Who the commit is by.
     *
     * <p>The person who made the edit, not DevForge. Attribution is the whole point
     * of committing back rather than dumping a tree: {@code git log} and
     * {@code git blame} should answer the same question the revision history does.
     */
    record Author(String name, String email) {
    }
}
