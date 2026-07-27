package com.devforge.sync.application;

import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.AuthoringOrigin;
import com.devforge.document.contract.DeclaredReference;
import com.devforge.document.contract.DocumentChanged;
import com.devforge.document.contract.DocumentType;
import com.devforge.document.contract.ReferenceType;
import com.devforge.identity.contract.UserDirectory;
import com.devforge.identity.contract.UserRef;
import com.devforge.sync.domain.DeletionPolicy;
import com.devforge.sync.domain.SyncConfiguration;
import com.devforge.sync.domain.SyncConfigurationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a change to a document should become in the repository.
 *
 * <p>Written against a fake repository rather than a real one on purpose: every
 * decision here — which path a page belongs at, whether a rename leaves a file
 * behind, what an echoed change should do — is documentation policy, and none of it
 * needs an object database to be wrong. The JGit side is tested separately, against
 * a repository that really exists.
 */
class DocumentMirrorServiceTest {

    /** Records what it was asked to commit, and can be told what it already holds. */
    private static final class FakeMirror implements RepositoryMirror {

        private boolean hosted = true;
        private List<String> paths = new ArrayList<>();
        private final List<MirrorChange> commits = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public boolean hosts(UUID workspaceId) {
            return hosted;
        }

        @Override
        public List<String> markdownPaths(UUID workspaceId) {
            return List.copyOf(paths);
        }

        @Override
        public boolean commit(UUID workspaceId, MirrorChange change) {
            if (failure != null) {
                throw failure;
            }
            commits.add(change);
            return true;
        }

        MirrorChange only() {
            assertThat(commits).hasSize(1);
            return commits.getFirst();
        }
    }

    private FakeMirror mirror;
    private SyncConfigurationRepository configurations;
    private UserDirectory users;
    private AuditTrail auditTrail;
    private DocumentMirrorService service;

    private UUID workspaceId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        mirror = new FakeMirror();
        configurations = mock(SyncConfigurationRepository.class);
        users = mock(UserDirectory.class);
        auditTrail = mock(AuditTrail.class);
        service = new DocumentMirrorService(mirror, configurations, users, auditTrail);

        workspaceId = UUID.randomUUID();
        actorId = UUID.randomUUID();

        when(configurations.findByWorkspaceId(workspaceId)).thenReturn(Optional.empty());
        when(users.findById(actorId)).thenReturn(Optional.of(
                new UserRef(actorId, "ada@example.test", "Ada Lovelace", "ada")));
    }

    private DocumentChanged written(String slug, String previousSlug, String content) {
        return new DocumentChanged(
                workspaceId, UUID.randomUUID(), DocumentChanged.Change.WRITTEN,
                slug, previousSlug, "A page", content,
                DocumentType.GENERAL, false, List.of(), actorId, AuthoringOrigin.DIRECT);
    }

    private DocumentChanged removed(String slug) {
        return new DocumentChanged(
                workspaceId, UUID.randomUUID(), DocumentChanged.Change.REMOVED,
                slug, null, "A page", "",
                DocumentType.GENERAL, false, List.of(), actorId, AuthoringOrigin.DIRECT);
    }

    private void configureFolder(String documentPath) {
        SyncConfiguration configuration = new SyncConfiguration(workspaceId);
        configuration.configure("", "main", documentPath, DocumentType.GENERAL, DeletionPolicy.ARCHIVE, true);
        when(configurations.findByWorkspaceId(workspaceId)).thenReturn(Optional.of(configuration));
    }

    // ------------------------------------------------------------------ writing

    @Test
    void writesANewPageAtThePathItsSlugImplies() {
        service.onDocumentChanged(written("runbooks/consumer-lag", null, "steps"));

        assertThat(mirror.only().written()).containsOnlyKeys("runbooks/consumer-lag.md");
        assertThat(mirror.only().written().get("runbooks/consumer-lag.md"))
                .startsWith("---\n")
                .contains("title: A page")
                .endsWith("steps\n");
        assertThat(mirror.only().message()).isEqualTo("Add runbooks/consumer-lag");
    }

    @Test
    void writesBelowTheConfiguredDocumentationFolder() {
        configureFolder("docs");

        service.onDocumentChanged(written("design", null, "body"));

        assertThat(mirror.only().written()).containsOnlyKeys("docs/design.md");
    }

    /**
     * The case that makes matching by slug rather than by name necessary: a file
     * whose name is not its slug must be rewritten, not duplicated.
     */
    @Test
    void rewritesTheFileAlreadyHoldingTheSlugWhateverItIsCalled() {
        mirror.paths = List.of("Getting Started.md");

        service.onDocumentChanged(written("getting-started", null, "body"));

        assertThat(mirror.only().written()).containsOnlyKeys("Getting Started.md");
        assertThat(mirror.only().message()).isEqualTo("Update getting-started");
    }

    @Test
    void ignoresAFileOutsideTheDocumentationFolderThatSlugsAlike() {
        configureFolder("docs");
        // Slugs to `guide` as well, but it is not documentation and must be left be.
        mirror.paths = List.of("guide.md");

        service.onDocumentChanged(written("guide", null, "body"));

        assertThat(mirror.only().written()).containsOnlyKeys("docs/guide.md");
    }

    @Test
    void writesTypedLinksAsFrontMatter() {
        DocumentChanged change = new DocumentChanged(
                workspaceId, UUID.randomUUID(), DocumentChanged.Change.WRITTEN,
                "design", null, "Design", "body",
                DocumentType.ARCHITECTURE, false,
                List.of(new DeclaredReference(ReferenceType.DEPENDS_ON, "queue"),
                        new DeclaredReference(ReferenceType.DEPENDS_ON, "cache"),
                        new DeclaredReference(ReferenceType.IMPLEMENTS, "rfc-7")),
                actorId, AuthoringOrigin.DIRECT);

        service.onDocumentChanged(change);

        assertThat(mirror.only().written().get("design.md"))
                .contains("depends_on: cache, queue")
                .contains("implements: rfc-7");
    }

    // ------------------------------------------------------------------ renaming

    @Test
    void aRenameRemovesTheFileItLeftBehind() {
        mirror.paths = List.of("old-name.md");

        service.onDocumentChanged(written("new-name", "old-name", "body"));

        assertThat(mirror.only().written()).containsOnlyKeys("new-name.md");
        assertThat(mirror.only().removed()).containsExactly("old-name.md");
        assertThat(mirror.only().message()).isEqualTo("Rename old-name to new-name");
    }

    // ------------------------------------------------------------------ removing

    @Test
    void aDeletedDocumentDeletesItsFile() {
        mirror.paths = List.of("docs/gone.md");
        configureFolder("docs");

        service.onDocumentChanged(removed("gone"));

        assertThat(mirror.only().removed()).containsExactly("docs/gone.md");
        assertThat(mirror.only().written()).isEmpty();
        assertThat(mirror.only().message()).isEqualTo("Delete gone");
    }

    @Test
    void deletingADocumentThatWasNeverInTheRepositoryCommitsNothing() {
        service.onDocumentChanged(removed("never-there"));

        assertThat(mirror.commits).isEmpty();
    }

    // ------------------------------------------------------------------- refusals

    /**
     * The loop breaker. Without it a push would be imported, mirrored back out as a
     * commit, and — on a repository someone else was watching — pushed onward.
     */
    @Test
    void doesNotCommitBackAChangeThatArrivedFromGit() {
        DocumentChanged echoed = new DocumentChanged(
                workspaceId, UUID.randomUUID(), DocumentChanged.Change.WRITTEN,
                "design", null, "Design", "body",
                DocumentType.GENERAL, false, List.of(), actorId, AuthoringOrigin.SYNC);

        service.onDocumentChanged(echoed);

        assertThat(mirror.commits).isEmpty();
    }

    @Test
    void doesNothingForAWorkspaceWithNoRepository() {
        mirror.hosted = false;

        service.onDocumentChanged(written("design", null, "body"));

        assertThat(mirror.commits).isEmpty();
    }

    // ---------------------------------------------------------------- attribution

    @Test
    void commitsAsWhoeverMadeTheEdit() {
        service.onDocumentChanged(written("design", null, "body"));

        assertThat(mirror.only().author())
                .isEqualTo(new RepositoryMirror.Author("Ada Lovelace", "ada@example.test"));
    }

    @Test
    void commitsAsDevForgeWhenNoAccountIsResponsible() {
        DocumentChanged change = new DocumentChanged(
                workspaceId, UUID.randomUUID(), DocumentChanged.Change.WRITTEN,
                "design", null, "Design", "body",
                DocumentType.GENERAL, false, List.of(), null, AuthoringOrigin.DIRECT);

        service.onDocumentChanged(change);

        assertThat(mirror.only().author().name()).isEqualTo("DevForge");
    }

    // -------------------------------------------------------------------- failure

    /**
     * The guarantee the whole design exists for: the edit has already been
     * committed and answered by the time this runs, so nothing here may escape.
     */
    @Test
    void aBrokenRepositoryDoesNotEscapeAsAnException() {
        mirror.failure = new IllegalStateException("disk is full");

        assertThatCode(() -> service.onDocumentChanged(written("design", null, "body")))
                .doesNotThrowAnyException();
    }

    @Test
    void recordsAFailureWhereAnOperatorWillSeeIt() {
        mirror.failure = new IllegalStateException("disk is full");

        service.onDocumentChanged(written("design", null, "body"));

        verify(auditTrail).record(any(), any(AuditEntry.class));
    }

    /** Committing nothing is not failing; a no-op change must stay silent. */
    @Test
    void doesNotReportAnythingWhenThereWasNothingToCommit() {
        service.onDocumentChanged(removed("never-there"));

        verify(auditTrail, org.mockito.Mockito.never()).record(any(), any(AuditEntry.class));
    }

    /** Both halves of a change reach the repository in one commit, not two. */
    @Test
    void aRenameIsASingleCommit() {
        mirror.paths = List.of("old.md");

        service.onDocumentChanged(written("new", "old", "body"));

        assertThat(mirror.commits).hasSize(1);
        RepositoryMirror.MirrorChange change = mirror.only();
        assertThat(change.written().keySet()).isEqualTo(Set.of("new.md"));
        assertThat(change.removed()).isEqualTo(Set.of("old.md"));
    }

    /**
     * Deterministic when a slug is spelled several ways: the shortest path wins.
     * An arbitrary rule, but a fixed one — the alternative is a document that
     * alternates between two files and shows up as a change every time.
     */
    @Test
    void picksOnePathWhenSeveralFilesSlugAlike() {
        mirror.paths = List.of("design.markdown", "design.md");

        service.onDocumentChanged(written("design", null, "body"));

        assertThat(mirror.only().written()).containsOnlyKeys("design.md");
    }
}
