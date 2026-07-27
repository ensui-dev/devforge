package com.devforge.sync.infrastructure;

import com.devforge.sync.application.RepositoryMirror;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The plumbing that turns paths and bytes into a commit.
 *
 * <p>Against a real repository, because everything worth getting wrong here is
 * about JGit's object model: whether a nested path becomes nested trees, whether a
 * commit that changes nothing is refused, whether files nobody mentioned survive.
 */
class JGitRepositoryMirrorTest {

    @TempDir
    Path root;

    private JGitRepositoryMirror mirror;
    private GitRepositoryStore store;
    private UUID workspaceId;

    @BeforeEach
    void setUp() {
        store = new GitRepositoryStore(new GitStorageProperties(root.toString(), true));
        mirror = new JGitRepositoryMirror(store);
        workspaceId = UUID.randomUUID();
    }

    private static RepositoryMirror.Author ada() {
        return new RepositoryMirror.Author("Ada Lovelace", "ada@example.test");
    }

    private boolean commit(Map<String, String> written, Set<String> removed, String message) {
        return mirror.commit(workspaceId, new RepositoryMirror.MirrorChange(
                written, removed, ada(), message));
    }

    /** The file at a path on the default branch, if it is there. */
    private Optional<String> read(String path) throws IOException {
        try (Repository repository = store.open(workspaceId);
             RevWalk walk = new RevWalk(repository)) {
            ObjectId head = repository.resolve(Constants.R_HEADS + "main");
            if (head == null) {
                return Optional.empty();
            }
            try (TreeWalk found = TreeWalk.forPath(
                    repository, path, walk.parseCommit(head).getTree())) {
                return found == null
                        ? Optional.empty()
                        : Optional.of(new String(
                                repository.open(found.getObjectId(0)).getBytes(),
                                StandardCharsets.UTF_8));
            }
        }
    }

    private RevCommit head() throws IOException {
        try (Repository repository = store.open(workspaceId);
             RevWalk walk = new RevWalk(repository)) {
            return walk.parseCommit(repository.resolve(Constants.R_HEADS + "main"));
        }
    }

    // ------------------------------------------------------------------ committing

    @Test
    void commitsIntoARepositoryThatHasNeverBeenPushedTo() throws Exception {
        store.open(workspaceId).close();

        assertThat(commit(Map.of("design.md", "a body\n"), Set.of(), "Add design")).isTrue();

        assertThat(read("design.md")).contains("a body\n");
        assertThat(head().getFullMessage()).isEqualTo("Add design\n");
    }

    /** A path with folders is several tree objects, only one of which is the file. */
    @Test
    void writesNestedPathsAsNestedTrees() throws Exception {
        store.open(workspaceId).close();

        commit(Map.of("docs/runbooks/consumer-lag.md", "steps\n"), Set.of(), "Add runbook");

        assertThat(read("docs/runbooks/consumer-lag.md")).contains("steps\n");
        assertThat(mirror.markdownPaths(workspaceId)).containsExactly("docs/runbooks/consumer-lag.md");
    }

    @Test
    void buildsOnTheExistingTreeRatherThanReplacingIt() throws Exception {
        store.open(workspaceId).close();
        commit(Map.of("a.md", "first\n", "b.md", "second\n"), Set.of(), "Add two");

        commit(Map.of("a.md", "changed\n"), Set.of(), "Update a");

        assertThat(read("a.md")).contains("changed\n");
        // The file the edit never mentioned is the one a lost-update bug would eat.
        assertThat(read("b.md")).contains("second\n");
        assertThat(head().getParentCount()).isEqualTo(1);
    }

    @Test
    void removesAPath() throws Exception {
        store.open(workspaceId).close();
        commit(Map.of("a.md", "first\n", "b.md", "second\n"), Set.of(), "Add two");

        commit(Map.of(), Set.of("a.md"), "Delete a");

        assertThat(read("a.md")).isEmpty();
        assertThat(read("b.md")).contains("second\n");
    }

    /** Rewriting a file with what it already says is not a change. */
    @Test
    void refusesACommitThatWouldChangeNothing() throws Exception {
        store.open(workspaceId).close();
        commit(Map.of("a.md", "same\n"), Set.of(), "Add a");
        ObjectId before = head().getId();

        assertThat(commit(Map.of("a.md", "same\n"), Set.of(), "Update a")).isFalse();

        assertThat(head().getId()).isEqualTo(before);
    }

    @Test
    void removingAPathThatIsNotThereChangesNothing() throws Exception {
        store.open(workspaceId).close();
        commit(Map.of("a.md", "first\n"), Set.of(), "Add a");

        assertThat(commit(Map.of(), Set.of("never-there.md"), "Delete")).isFalse();
    }

    // ---------------------------------------------------------------- attribution

    @Test
    void recordsWhoMadeTheEditAsBothAuthorAndCommitter() throws Exception {
        store.open(workspaceId).close();

        commit(Map.of("a.md", "body\n"), Set.of(), "Add a");

        RevCommit head = head();
        assertThat(head.getAuthorIdent().getName()).isEqualTo("Ada Lovelace");
        assertThat(head.getAuthorIdent().getEmailAddress()).isEqualTo("ada@example.test");
        assertThat(head.getCommitterIdent().getName()).isEqualTo("Ada Lovelace");
    }

    // -------------------------------------------------------------------- reading

    @Test
    void listsOnlyMarkdownFiles() throws Exception {
        store.open(workspaceId).close();

        commit(Map.of(
                "docs/a.md", "a\n",
                "docs/b.markdown", "b\n",
                "docs/logo.png", "not documentation\n",
                "README", "nor this\n"), Set.of(), "Add files");

        assertThat(mirror.markdownPaths(workspaceId))
                .containsExactlyInAnyOrder("docs/a.md", "docs/b.markdown");
    }

    @Test
    void answersNothingForAWorkspaceWithNoRepository() {
        assertThat(mirror.hosts(workspaceId)).isFalse();
        assertThat(mirror.markdownPaths(workspaceId)).isEmpty();
    }

    /**
     * A repository can exist without a branch — it is created the moment somebody
     * clones it, which is before anyone has pushed anything into it.
     */
    @Test
    void answersNothingForARepositoryWithNoCommits() throws Exception {
        store.open(workspaceId).close();

        assertThat(mirror.hosts(workspaceId)).isTrue();
        assertThat(mirror.markdownPaths(workspaceId)).isEqualTo(List.of());
    }
}
