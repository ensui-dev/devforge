package com.devforge.sync.application;

import java.util.Optional;
import java.util.UUID;

/**
 * The hosted repositories, as an operator needs to see them.
 *
 * <p>Separate from {@link RepositoryMirror}, which writes commits. This one answers
 * questions about the repository as a thing on disk — is the feature on, is there
 * one here yet, how much space is it taking — which is what a settings screen and a
 * backup policy care about, and none of which involves an object database.
 */
public interface HostedRepositories {

    /** Whether this instance serves git at all. */
    boolean enabled();

    /** Whether this workspace has a repository yet; one appears on first use. */
    boolean exists(UUID workspaceId);

    /**
     * Total size on disk.
     *
     * <p>Worth showing because repositories are the one thing on an instance that
     * {@code pg_dump} does not capture, so an operator has to know they are there.
     */
    Optional<Long> sizeOf(UUID workspaceId);
}
