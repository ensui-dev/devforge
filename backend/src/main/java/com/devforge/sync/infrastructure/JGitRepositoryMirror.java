package com.devforge.sync.infrastructure;

import com.devforge.sync.application.RepositoryMirror;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheBuilder;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.dircache.DirCacheEntry;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes commits into a hosted repository with JGit.
 *
 * <p>Builds the tree in memory. A bare repository has no working copy to stage
 * anything in, and adding one would mean a checkout per edit and a directory that
 * could disagree with the branch — so the commit is assembled from the tree that is
 * already there, with the affected paths replaced.
 *
 * <p>Every write is a compare-and-swap on the branch: it names the commit it
 * expected to find and fails if something else moved it first. That is what makes a
 * push arriving mid-edit safe — the write is retried against the new tip instead of
 * discarding it.
 */
@Component
public class JGitRepositoryMirror implements RepositoryMirror {

    private static final Logger log = LoggerFactory.getLogger(JGitRepositoryMirror.class);

    /** Enough for a push to land and be noticed; a genuine contention loop is not. */
    private static final int ATTEMPTS = 3;

    private final GitRepositoryStore store;

    public JGitRepositoryMirror(GitRepositoryStore store) {
        this.store = store;
    }

    @Override
    public boolean hosts(UUID workspaceId) {
        return store.exists(workspaceId);
    }

    @Override
    public List<String> markdownPaths(UUID workspaceId) {
        if (!hosts(workspaceId)) {
            return List.of();
        }

        try (Repository repository = store.open(workspaceId)) {
            ObjectId head = repository.resolve(branch());
            if (head == null) {
                // A repository that exists but has never been pushed to.
                return List.of();
            }

            List<String> paths = new ArrayList<>();
            try (RevWalk walk = new RevWalk(repository);
                 TreeWalk tree = new TreeWalk(repository)) {
                tree.addTree(walk.parseCommit(head).getTree());
                tree.setRecursive(true);
                while (tree.next()) {
                    if (isMarkdown(tree.getPathString())) {
                        paths.add(tree.getPathString());
                    }
                }
            }
            return List.copyOf(paths);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public boolean commit(UUID workspaceId, MirrorChange change) {
        try (Repository repository = store.open(workspaceId)) {
            for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
                switch (attempt(repository, change)) {
                    case COMMITTED -> {
                        return true;
                    }
                    case NOTHING_TO_DO -> {
                        return false;
                    }
                    case CONTENDED -> log.info(
                            "The branch moved while mirroring workspace {}; retrying ({}/{})",
                            workspaceId, attempt, ATTEMPTS);
                }
            }
            throw new IllegalStateException(
                    "Gave up mirroring workspace %s after %d attempts: the branch kept moving"
                            .formatted(workspaceId, ATTEMPTS));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private enum Attempt { COMMITTED, NOTHING_TO_DO, CONTENDED }

    private Attempt attempt(Repository repository, MirrorChange change) throws IOException {
        ObjectId head = repository.resolve(branch());

        try (ObjectInserter inserter = repository.newObjectInserter();
             ObjectReader reader = repository.newObjectReader();
             RevWalk walk = new RevWalk(repository)) {

            RevCommit parent = head == null ? null : walk.parseCommit(head);
            ObjectId previousTree = parent == null ? null : parent.getTree().getId();
            ObjectId tree = buildTree(inserter, reader, previousTree, change);

            // The same rule a document save obeys: producing what is already there
            // is not a change, and an empty commit would be noise in a log people
            // read to find out what actually happened.
            if (tree.equals(previousTree)) {
                return Attempt.NOTHING_TO_DO;
            }

            PersonIdent author = new PersonIdent(change.author().name(), change.author().email());
            CommitBuilder commit = new CommitBuilder();
            commit.setTreeId(tree);
            if (parent != null) {
                commit.setParentId(parent);
            }
            commit.setAuthor(author);
            // Committer and author are the same person: DevForge is where the edit
            // was made, not a third party relaying someone else's work.
            commit.setCommitter(author);
            commit.setMessage(change.message() + "\n");

            ObjectId commitId = inserter.insert(commit);
            inserter.flush();

            RefUpdate update = repository.updateRef(branch());
            update.setNewObjectId(commitId);
            // Names what this write assumed. If a push landed in between, the branch
            // no longer points here and the update is rejected rather than losing it.
            update.setExpectedOldObjectId(head == null ? ObjectId.zeroId() : head);
            update.setRefLogMessage("devforge: " + change.message(), false);

            return switch (update.update(walk)) {
                case NEW, FAST_FORWARD, FORCED, NO_CHANGE -> Attempt.COMMITTED;
                case LOCK_FAILURE, REJECTED, REJECTED_MISSING_OBJECT, REJECTED_OTHER_REASON ->
                        Attempt.CONTENDED;
                case IO_FAILURE -> throw new IOException(
                        "Could not update " + branch() + " while mirroring an edit");
                default -> Attempt.CONTENDED;
            };
        }
    }

    /**
     * The previous tree with the changed paths applied.
     *
     * <p>Assembled through an in-core index because that is what knows how to turn
     * flat paths into nested trees — {@code docs/runbooks/lag.md} is three objects,
     * and only one of them is the file.
     */
    private ObjectId buildTree(
            ObjectInserter inserter,
            ObjectReader reader,
            ObjectId previousTree,
            MirrorChange change
    ) throws IOException {
        DirCache index = DirCache.newInCore();

        DirCacheBuilder seed = index.builder();
        if (previousTree != null) {
            seed.addTree(new byte[0], DirCacheEntry.STAGE_0, reader, previousTree);
        }
        seed.finish();

        DirCacheEditor editor = index.editor();
        for (String path : change.removed()) {
            editor.add(new DirCacheEditor.DeletePath(path));
        }
        for (Map.Entry<String, String> file : change.written().entrySet()) {
            ObjectId blob = inserter.insert(
                    Constants.OBJ_BLOB, file.getValue().getBytes(StandardCharsets.UTF_8));
            editor.add(new DirCacheEditor.PathEdit(file.getKey()) {
                @Override
                public void apply(DirCacheEntry entry) {
                    entry.setFileMode(FileMode.REGULAR_FILE);
                    entry.setObjectId(blob);
                }
            });
        }
        editor.finish();

        return index.writeTree(inserter);
    }

    private static String branch() {
        return Constants.R_HEADS + GitRepositoryStore.defaultBranch();
    }

    private static boolean isMarkdown(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }
}
