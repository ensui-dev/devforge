package com.devforge.sync.infrastructure;

import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bare repository storage.
 *
 * <p>Against a real temporary directory rather than a mocked filesystem: the whole
 * class is filesystem behaviour, and a mock would only assert that it calls the
 * methods it calls.
 */
class GitRepositoryStoreTest {

    @TempDir
    Path root;

    private GitRepositoryStore store() {
        return new GitRepositoryStore(new GitStorageProperties(root.toString(), true));
    }

    @Test
    void createsABareRepositoryOnFirstUse() throws IOException {
        UUID workspace = UUID.randomUUID();
        GitRepositoryStore store = store();

        assertThat(store.exists(workspace)).isFalse();

        try (Repository repository = store.open(workspace)) {
            assertThat(repository.isBare()).isTrue();
            assertThat(repository.getObjectDatabase().exists()).isTrue();
        }

        assertThat(store.exists(workspace)).isTrue();
    }

    /**
     * Creating on first use is what makes `git remote add` followed by `git push`
     * work with no button pressed first, as every git host behaves.
     */
    @Test
    void opensTheSameRepositoryAgainWithoutRecreatingIt() throws IOException {
        UUID workspace = UUID.randomUUID();
        GitRepositoryStore store = store();

        try (Repository first = store.open(workspace)) {
            Files.writeString(first.getDirectory().toPath().resolve("marker"), "written");
        }
        try (Repository second = store.open(workspace)) {
            assertThat(second.getDirectory().toPath().resolve("marker")).exists();
        }
    }

    /** A fresh repository must have somewhere for a first push to land. */
    @Test
    void pointsHeadAtTheDefaultBranch() throws IOException {
        try (Repository repository = store().open(UUID.randomUUID())) {
            assertThat(repository.exactRef(Constants.HEAD).getTarget().getName())
                    .isEqualTo(Constants.R_HEADS + "main");
        }
    }

    /**
     * Named by id, not slug: renaming a workspace must not orphan its repository or
     * make the directory name a second thing to keep in step.
     */
    @Test
    void namesTheDirectoryAfterTheWorkspaceId() {
        UUID workspace = UUID.randomUUID();

        assertThat(store().pathFor(workspace))
                .isEqualTo(root.toAbsolutePath().normalize().resolve(workspace + ".git"));
    }

    /**
     * Repositories live outside the database, so nothing cascades them away when a
     * workspace is deleted. Without this they accumulate as directories nobody can
     * account for.
     */
    @Test
    void removesARepositoryAndEverythingUnderIt() throws IOException {
        UUID workspace = UUID.randomUUID();
        GitRepositoryStore store = store();
        store.open(workspace).close();
        assertThat(store.exists(workspace)).isTrue();

        store.delete(workspace);

        assertThat(store.exists(workspace)).isFalse();
        assertThat(root.resolve(workspace + ".git")).doesNotExist();
    }

    /** Deleting a workspace that never pushed must not fail. */
    @Test
    void deletingSomethingThatIsNotThereIsHarmless() {
        store().delete(UUID.randomUUID());
    }

    @Test
    void reportsSizeOnlyForARepositoryThatExists() throws IOException {
        UUID workspace = UUID.randomUUID();
        GitRepositoryStore store = store();

        assertThat(store.sizeOf(workspace)).isEmpty();

        store.open(workspace).close();
        assertThat(store.sizeOf(workspace)).hasValueSatisfying(size ->
                assertThat(size).isPositive());
    }

    /** A blank configured root falls back rather than resolving to the process directory. */
    @Test
    void fallsBackToADefaultRootWhenNoneIsConfigured() {
        assertThat(new GitStorageProperties("", true).root()).isEqualTo("/var/lib/devforge/git");
        assertThat(new GitStorageProperties(null, true).root()).isEqualTo("/var/lib/devforge/git");
    }
}
