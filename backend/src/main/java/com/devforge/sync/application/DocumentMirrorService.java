package com.devforge.sync.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.AuthoringOrigin;
import com.devforge.document.contract.DocumentChanged;
import com.devforge.identity.contract.UserDirectory;
import com.devforge.sync.domain.SyncConfiguration;
import com.devforge.sync.domain.SyncConfigurationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Commits documentation edited in DevForge back to the workspace's repository.
 *
 * <p>The other half of two-way syncing. A page edited in the interface becomes a
 * real commit, authored by whoever edited it, so a clone or a pull sees the change
 * and {@code git log} answers the same question the revision history does.
 *
 * <h2>Why this listens rather than being called</h2>
 *
 * <p>It runs from a {@linkplain TransactionPhase#AFTER_COMMIT committed}
 * transaction, which is what makes the guarantee "git trouble cannot fail an edit"
 * structural rather than a promise. By the time this runs the document is saved and
 * the response is on its way; there is no transaction left to roll back, so the
 * worst a broken repository can do is leave the mirror behind — reported, not
 * silent. The document module, for its part, has never heard of git.
 *
 * <h2>What happens when both sides changed a page</h2>
 *
 * <p>Nothing is merged. The commit replaces exactly one file, on top of whatever
 * the branch currently points at, and touches nothing else — so a push that changed
 * other pages survives untouched, and a push that changed <em>this</em> page is
 * overwritten by the edit.
 *
 * <p>That is the right way round. An import applies a push to the document
 * immediately, so anyone editing in the interface is editing what the push left
 * behind: the document is always the later of the two. The only genuine race — a
 * push landing between reading the branch and writing to it — is caught by the
 * compare-and-swap in the writer, which retries from the new tip rather than
 * clobbering it.
 */
@Service
public class DocumentMirrorService {

    private static final Logger log = LoggerFactory.getLogger(DocumentMirrorService.class);

    private final RepositoryMirror repositories;
    private final SyncConfigurationRepository configurations;
    private final UserDirectory userDirectory;
    private final AuditTrail auditTrail;

    public DocumentMirrorService(
            RepositoryMirror repositories,
            SyncConfigurationRepository configurations,
            UserDirectory userDirectory,
            AuditTrail auditTrail
    ) {
        this.repositories = repositories;
        this.configurations = configurations;
        this.userDirectory = userDirectory;
        this.auditTrail = auditTrail;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onDocumentChanged(DocumentChanged change) {
        // A change that arrived from git is already in git. Committing it back would
        // produce an identical tree at best, and at worst a push, an import and a
        // commit chasing each other. This is the loop breaker, and it is one line
        // because the origin was carried the whole way rather than inferred.
        if (change.origin() == AuthoringOrigin.SYNC) {
            return;
        }

        try {
            mirror(change);
        } catch (RuntimeException e) {
            // Never rethrown: the edit has already been committed and answered, and
            // an exception here would only be logged by the framework anyway. It is
            // recorded instead, so an operator finds out from the activity log
            // rather than from a repository that quietly stopped keeping up.
            log.error("Could not mirror {} to the repository for workspace {}",
                    change.slug(), change.workspaceId(), e);
            reportFailure(change, e);
        }
    }

    private void mirror(DocumentChanged change) {
        UUID workspaceId = change.workspaceId();
        if (!repositories.hosts(workspaceId)) {
            return;
        }

        String folder = configurations.findByWorkspaceId(workspaceId)
                .map(SyncConfiguration::getDocumentPath)
                .orElse("");
        List<String> paths = repositories.markdownPaths(workspaceId);
        Optional<String> held = pathHolding(paths, change.slug(), folder);

        Map<String, String> written = new LinkedHashMap<>();
        Set<String> removed = new LinkedHashSet<>();

        // A rename leaves the old file behind unless it is named here; the import
        // would then read the page twice, under both slugs.
        if (change.renamed()) {
            pathHolding(paths, change.previousSlug(), folder).ifPresent(removed::add);
        }

        if (change.change() == DocumentChanged.Change.REMOVED) {
            held.ifPresent(removed::add);
        } else {
            // Rewritten where it already lives, whatever it is called there. A file
            // named `Getting Started.md` keeps its name rather than being replaced
            // by a second file called `getting-started.md` saying the same thing.
            written.put(held.orElseGet(() -> MarkdownFile.pathFor(change.slug(), folder)),
                    MarkdownFile.render(
                            change.title(),
                            change.content(),
                            change.documentType(),
                            change.internal(),
                            change.references()));
        }

        if (written.isEmpty() && removed.isEmpty()) {
            return;
        }

        repositories.commit(workspaceId, new RepositoryMirror.MirrorChange(
                written, removed, authorOf(change.actorId()), messageFor(change, held.isPresent())));
    }

    /**
     * The file currently holding a slug, if any.
     *
     * <p>Matched by slug rather than by name because the two are not the same
     * mapping: several filenames slugify alike. When more than one does, the
     * shortest wins and ties break alphabetically — an arbitrary rule, but a fixed
     * one, so the same document does not alternate between two files.
     */
    private static Optional<String> pathHolding(List<String> paths, String slug, String folder) {
        return paths.stream()
                .filter(path -> under(path, folder))
                .filter(path -> MarkdownFile.slugFrom(path, folder).equals(slug))
                .min(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));
    }

    /**
     * Whether a path is inside the configured documentation folder.
     *
     * <p>Checked separately because slugging is forgiving: a file outside the folder
     * still yields a slug, and without this a root {@code guide.md} would be taken
     * for {@code docs/guide.md} and overwritten.
     */
    private static boolean under(String path, String folder) {
        if (folder == null || folder.isBlank()) {
            return true;
        }
        String prefix = folder.strip().replaceAll("^/+", "").replaceAll("/+$", "") + "/";
        return path.startsWith(prefix);
    }

    /**
     * The commit's author.
     *
     * <p>Falls back to DevForge itself only when there is no account to name — a
     * change made by something other than a person. Attributing that to whoever
     * happened to be nearby would be worse than saying so.
     */
    private RepositoryMirror.Author authorOf(UUID actorId) {
        if (actorId == null) {
            return new RepositoryMirror.Author("DevForge", "devforge@localhost");
        }
        return userDirectory.findById(actorId)
                .map(user -> new RepositoryMirror.Author(user.displayName(), user.email()))
                .orElseGet(() -> new RepositoryMirror.Author("DevForge", "devforge@localhost"));
    }

    /** What {@code git log --oneline} will say, in the vocabulary git users expect. */
    private static String messageFor(DocumentChanged change, boolean existed) {
        if (change.change() == DocumentChanged.Change.REMOVED) {
            return "Delete " + change.slug();
        }
        if (change.renamed()) {
            return "Rename %s to %s".formatted(change.previousSlug(), change.slug());
        }
        return (existed ? "Update " : "Add ") + change.slug();
    }

    private void reportFailure(DocumentChanged change, RuntimeException cause) {
        auditTrail.record(change.actorId(), AuditEntry
                .of(AuditAction.WORKSPACE_SYNCED, AuditTargetType.WORKSPACE)
                .target(change.workspaceId(), null)
                .inWorkspace(change.workspaceId())
                .with("status", "FAILED")
                .with("direction", "out")
                .with("trigger", "edit")
                .with("slug", change.slug())
                .with("message", "could not commit this change to the repository: " + cause.getMessage()));
    }
}
