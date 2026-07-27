package com.devforge.sync.infrastructure;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryCache;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.util.FS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

/**
 * The bare repositories DevForge hosts, one per workspace.
 *
 * <p>Named by workspace id rather than by slug, so renaming a workspace does not
 * orphan its repository or make the directory name a second thing to keep in step.
 * The URL still uses handle and slug; resolving one to the other is the resolver's
 * job, not the filesystem's.
 *
 * <p>Repositories are created on first use. That is what makes
 * {@code git remote add devforge …} followed by {@code git push} work without
 * anyone having to press a button first — the same way every git host behaves.
 */
@Component
public class GitRepositoryStore {

    private static final Logger log = LoggerFactory.getLogger(GitRepositoryStore.class);

    private final Path root;

    public GitRepositoryStore(GitStorageProperties properties) {
        this.root = Path.of(properties.root()).toAbsolutePath().normalize();
    }

    /** Where a workspace's repository lives, whether or not it exists yet. */
    public Path pathFor(UUID workspaceId) {
        return root.resolve(workspaceId + ".git");
    }

    public boolean exists(UUID workspaceId) {
        return Files.isDirectory(pathFor(workspaceId));
    }

    /**
     * Opens the workspace's repository, creating an empty bare one if needed.
     *
     * <p>The caller must close it. JGit reference-counts through
     * {@link RepositoryCache}, so closing an instance does not evict shared state.
     */
    public Repository open(UUID workspaceId) throws IOException {
        Path path = pathFor(workspaceId);
        boolean fresh = !Files.isDirectory(path);

        if (fresh) {
            Files.createDirectories(path);
        }

        Repository repository = new FileRepositoryBuilder()
                .setGitDir(path.toFile())
                .setFS(FS.DETECTED)
                .build();

        if (fresh || repository.getObjectDatabase() == null || !repository.getObjectDatabase().exists()) {
            repository.create(true);
            // A default branch, so `git push` to a fresh repository has somewhere to
            // land and `git clone` of an empty one does not warn about HEAD.
            repository.updateRef(Constants.HEAD, true)
                    .link(Constants.R_HEADS + Constants.MASTER.replace("master", "main"));
            log.info("Created git repository for workspace {}", workspaceId);
        }

        return repository;
    }

    /** The default branch a fresh repository points HEAD at. */
    public static String defaultBranch() {
        return "main";
    }

    /**
     * Removes a workspace's repository.
     *
     * <p>Called when a workspace is deleted. Repositories are outside the database,
     * so nothing cascades them away — without this they would accumulate as orphaned
     * directories nobody could account for.
     */
    public void delete(UUID workspaceId) {
        Path path = pathFor(workspaceId);
        if (!Files.isDirectory(path)) {
            return;
        }
        try (var walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder()).forEach(entry -> {
                try {
                    Files.deleteIfExists(entry);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            log.info("Removed git repository for workspace {}", workspaceId);
        } catch (IOException | UncheckedIOException e) {
            // Worth knowing about, not worth failing the workspace deletion over.
            log.error("Could not remove the git repository for workspace {}", workspaceId, e);
        }
    }

    /** Total size on disk, so the settings screen can be honest about it. */
    public Optional<Long> sizeOf(UUID workspaceId) {
        Path path = pathFor(workspaceId);
        if (!Files.isDirectory(path)) {
            return Optional.empty();
        }
        try (var walk = Files.walk(path)) {
            return Optional.of(walk.filter(Files::isRegularFile).mapToLong(entry -> {
                try {
                    return Files.size(entry);
                } catch (IOException e) {
                    return 0L;
                }
            }).sum());
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Path root() {
        return root;
    }
}
