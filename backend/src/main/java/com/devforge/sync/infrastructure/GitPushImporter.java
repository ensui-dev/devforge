package com.devforge.sync.infrastructure;

import com.devforge.sync.application.PushedTree;
import com.devforge.sync.application.SourceFile;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Applies a pushed branch to its workspace.
 *
 * <p>Reads the tree at the ref that just moved and hands the files to the same
 * planner and authoring contract the webhook path uses, so pushing and syncing from
 * a remote cannot drift apart — there is one import, reached two ways.
 *
 * <p>Runs as a post-receive hook. The objects are already stored and the refs
 * already moved by then, which is the honest ordering: the push succeeded, and
 * whether DevForge could make sense of the documentation in it is a separate
 * question whose answer is reported back on the same connection.
 */
@Component
public class GitPushImporter {

    private static final Logger log = LoggerFactory.getLogger(GitPushImporter.class);

    /** A single documentation file past this is not documentation. */
    private static final long MAX_FILE_BYTES = 2L * 1024 * 1024;

    private final PushedTree pushedTree;

    public GitPushImporter(PushedTree pushedTree) {
        this.pushedTree = pushedTree;
    }

    /**
     * Imports on the next successful push to the workspace's default branch.
     *
     * <p>Only that branch. A repository is a working space — feature branches, drafts
     * — and importing every one of them would make the workspace show whatever was
     * pushed last rather than what the documentation says.
     */
    void attachTo(ReceivePack receivePack, UUID workspaceId, UUID actorId) {
        receivePack.setPostReceiveHook((PostReceiveHook) (pack, commands) -> {
            ReceiveCommand branch = defaultBranchUpdate(commands);
            if (branch == null) {
                return;
            }

            try {
                List<SourceFile> files = read(pack.getRepository(), branch.getNewId());
                String summary = pushedTree.apply(workspaceId, actorId, files, branch.getNewId().name());
                // Sent down the side band, so it appears in the pusher's terminal
                // under `remote:` — the one moment they are looking.
                pack.sendMessage("DevForge: " + summary);
            } catch (IOException e) {
                log.error("Could not read the pushed tree for workspace {}", workspaceId, e);
                pack.sendError("DevForge could not read the pushed tree: " + e.getMessage());
            } catch (RuntimeException e) {
                log.error("Could not import the pushed tree for workspace {}", workspaceId, e);
                pack.sendError("DevForge could not import this push: " + e.getMessage());
            }
        });
    }

    private ReceiveCommand defaultBranchUpdate(Collection<ReceiveCommand> commands) {
        String ref = Constants.R_HEADS + GitRepositoryStore.defaultBranch();
        return commands.stream()
                .filter(command -> command.getResult() == ReceiveCommand.Result.OK)
                .filter(command -> ref.equals(command.getRefName()))
                .filter(command -> command.getType() != ReceiveCommand.Type.DELETE)
                .findFirst()
                .orElse(null);
    }

    /** Every markdown file in the pushed tree, with its repository-relative path. */
    private List<SourceFile> read(Repository repository, ObjectId commitId) throws IOException {
        List<SourceFile> files = new ArrayList<>();

        try (RevWalk walk = new RevWalk(repository);
             TreeWalk tree = new TreeWalk(repository)) {

            tree.addTree(walk.parseCommit(commitId).getTree());
            tree.setRecursive(true);

            while (tree.next()) {
                String path = tree.getPathString();
                if (!isMarkdown(path)) {
                    continue;
                }

                ObjectLoader blob = repository.open(tree.getObjectId(0));
                if (blob.getSize() > MAX_FILE_BYTES) {
                    log.warn("Skipping {}: larger than {} bytes", path, MAX_FILE_BYTES);
                    continue;
                }
                files.add(new SourceFile(path, new String(blob.getBytes(), StandardCharsets.UTF_8)));
            }
        }

        return files;
    }

    private static boolean isMarkdown(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".markdown");
    }
}
