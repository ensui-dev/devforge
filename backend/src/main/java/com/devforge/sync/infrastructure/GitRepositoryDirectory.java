package com.devforge.sync.infrastructure;

import com.devforge.sync.application.HostedRepositories;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Answers questions about hosted repositories from the filesystem.
 *
 * <p>Thin on purpose: everything here is already in {@link GitRepositoryStore}, and
 * this exists so that the application layer can ask without depending on it —
 * {@code GitStorageProperties} in particular is a configuration binding, which is
 * not a thing a service should be reading.
 */
@Component
public class GitRepositoryDirectory implements HostedRepositories {

    private final GitRepositoryStore store;
    private final GitStorageProperties properties;

    public GitRepositoryDirectory(GitRepositoryStore store, GitStorageProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    @Override
    public boolean enabled() {
        return properties.enabled();
    }

    @Override
    public boolean exists(UUID workspaceId) {
        return store.exists(workspaceId);
    }

    @Override
    public Optional<Long> sizeOf(UUID workspaceId) {
        return store.sizeOf(workspaceId);
    }
}
