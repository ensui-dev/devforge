package com.devforge.sync.application;

/**
 * The workspace's repository, as the settings screen shows it.
 *
 * @param enabled   whether this instance serves git at all
 * @param exists    whether this workspace has a repository yet; one appears the
 *                  first time somebody clones or pushes, so "not yet" is normal
 * @param clonePath the path half of the remote URL, without a host. The browser
 *                  supplies the origin, which is the only way to be right about it
 *                  behind a reverse proxy, a tunnel, or a hostname nobody told the
 *                  server about.
 * @param sizeBytes size on disk, absent until the repository exists
 */
public record GitRepositoryResponse(
        boolean enabled,
        boolean exists,
        String clonePath,
        Long sizeBytes
) {

    public static GitRepositoryResponse disabled() {
        return new GitRepositoryResponse(false, false, null, null);
    }
}
