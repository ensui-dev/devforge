package com.devforge.document.application;

import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.AuthoringOrigin;
import com.devforge.document.contract.DocumentAuthoring;
import com.devforge.document.contract.DocumentDraft;
import com.devforge.document.contract.DocumentType;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentRepository;
import com.devforge.document.domain.RevisionReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The authoring contract's implementation.
 *
 * <p>Written against the revision reason each write records, because that is what a
 * reader relies on to answer "why did this page change?". An earlier version
 * recorded a creation through this path as {@code UPDATED}, which read as though
 * someone had edited a page that had just come into existence — the sync module was
 * the only caller, so nothing noticed.
 */
@ExtendWith(MockitoExtension.class)
class DocumentAuthoringServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentHistoryService history;

    @Mock
    private AuditTrail auditTrail;

    @InjectMocks
    private DocumentAuthoringService authoring;

    private UUID workspaceId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        actorId = UUID.randomUUID();
    }

    private DocumentDraft draft(String slug, String title, String content) {
        return new DocumentDraft(slug, title, content, DocumentType.GENERAL, false);
    }

    private Document existing(String slug, String title, String content) {
        return new Document(workspaceId, title, slug, content, DocumentType.GENERAL, false);
    }

    private RevisionReason capturedReason() {
        ArgumentCaptor<RevisionReason> reason = ArgumentCaptor.forClass(RevisionReason.class);
        verify(history).snapshot(any(), reason.capture(), any(), any());
        return reason.getValue();
    }

    // ------------------------------------------------------------------ creating

    /** The regression: a creation is CREATED, whatever else it is. */
    @Test
    void recordsADirectCreationAsCreated() {
        when(documentRepository.findByWorkspaceIdAndSlug(workspaceId, "a")).thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        DocumentAuthoring.AuthoringResult result = authoring.upsert(
                workspaceId, draft("a", "A", "body"), actorId, AuthoringOrigin.DIRECT);

        assertThat(result).isEqualTo(DocumentAuthoring.AuthoringResult.CREATED);
        assertThat(capturedReason()).isEqualTo(RevisionReason.CREATED);
    }

    /**
     * A synced creation says SYNCED. Being revision 1 already conveys that it was a
     * creation; where it came from is not recoverable from anything else.
     */
    @Test
    void recordsASyncedCreationAsSynced() {
        when(documentRepository.findByWorkspaceIdAndSlug(workspaceId, "a")).thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        authoring.upsert(workspaceId, draft("a", "A", "body"), actorId, AuthoringOrigin.SYNC);

        assertThat(capturedReason()).isEqualTo(RevisionReason.SYNCED);
    }

    // ------------------------------------------------------------------ updating

    @Test
    void recordsADirectEditAsUpdated() {
        Document document = existing("a", "A", "old");
        when(documentRepository.findByWorkspaceIdAndSlug(workspaceId, "a"))
                .thenReturn(Optional.of(document));
        when(history.differsFromLatest(document)).thenReturn(true);

        DocumentAuthoring.AuthoringResult result = authoring.upsert(
                workspaceId, draft("a", "A", "new"), actorId, AuthoringOrigin.DIRECT);

        assertThat(result).isEqualTo(DocumentAuthoring.AuthoringResult.UPDATED);
        assertThat(capturedReason()).isEqualTo(RevisionReason.UPDATED);
        assertThat(document.getContent()).isEqualTo("new");
    }

    @Test
    void recordsASyncedEditAsSynced() {
        Document document = existing("a", "A", "old");
        when(documentRepository.findByWorkspaceIdAndSlug(workspaceId, "a"))
                .thenReturn(Optional.of(document));
        when(history.differsFromLatest(document)).thenReturn(true);

        authoring.upsert(workspaceId, draft("a", "A", "new"), actorId, AuthoringOrigin.SYNC);

        assertThat(capturedReason()).isEqualTo(RevisionReason.SYNCED);
    }

    /**
     * A sync re-applies every file on every run, so without this one push would
     * append a revision to every page in the workspace.
     */
    @Test
    void writesNothingWhenTheDraftMatchesWhatIsStored() {
        Document document = existing("a", "A", "same");
        when(documentRepository.findByWorkspaceIdAndSlug(workspaceId, "a"))
                .thenReturn(Optional.of(document));
        when(history.differsFromLatest(document)).thenReturn(false);

        DocumentAuthoring.AuthoringResult result = authoring.upsert(
                workspaceId, draft("a", "A", "same"), actorId, AuthoringOrigin.SYNC);

        assertThat(result).isEqualTo(DocumentAuthoring.AuthoringResult.UNCHANGED);
        verify(history, never()).snapshot(any(), any(), any(), any());
        verify(auditTrail, never()).record(any(), any());
    }

    // ----------------------------------------------------------------- archiving

    @Test
    void archivingMarksTheDocumentInternal() {
        Document document = existing("a", "A", "body");
        when(documentRepository.findByWorkspaceIdAndSlug(workspaceId, "a"))
                .thenReturn(Optional.of(document));

        boolean archived = authoring.archiveBySlug(
                workspaceId, "a", actorId, AuthoringOrigin.SYNC);

        assertThat(archived).isTrue();
        assertThat(document.isInternal()).isTrue();
        // Still present: archiving withdraws a page, it does not destroy it.
        verify(documentRepository, never()).delete(any());
    }

    /** Re-archiving would append a revision that says nothing. */
    @Test
    void archivingAnAlreadyWithdrawnDocumentDoesNothing() {
        Document document = new Document(
                workspaceId, "A", "a", "body", DocumentType.GENERAL, true);
        when(documentRepository.findByWorkspaceIdAndSlug(workspaceId, "a"))
                .thenReturn(Optional.of(document));

        assertThat(authoring.archiveBySlug(workspaceId, "a", actorId, AuthoringOrigin.SYNC))
                .isFalse();
        verify(history, never()).snapshot(any(), any(), any(), any());
    }

    @Test
    void reportsAnUnknownSlugRatherThanFailing() {
        when(documentRepository.findByWorkspaceIdAndSlug(workspaceId, "gone"))
                .thenReturn(Optional.empty());

        assertThat(authoring.archiveBySlug(workspaceId, "gone", actorId, AuthoringOrigin.SYNC))
                .isFalse();
        assertThat(authoring.deleteBySlug(workspaceId, "gone", actorId, AuthoringOrigin.SYNC))
                .isFalse();
    }

    /** The audit entry has to exist before the row it describes is gone. */
    @Test
    void recordsADeletionBeforePerformingIt() {
        Document document = existing("a", "Doomed", "body");
        when(documentRepository.findByWorkspaceIdAndSlug(workspaceId, "a"))
                .thenReturn(Optional.of(document));

        assertThat(authoring.deleteBySlug(workspaceId, "a", actorId, AuthoringOrigin.SYNC)).isTrue();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(auditTrail, documentRepository);
        order.verify(auditTrail).record(eq(actorId), any(AuditEntry.class));
        order.verify(documentRepository).delete(document);
    }

    /** Every write says where it came from, so the log can distinguish the two. */
    @Test
    void recordsTheOriginOnEveryChange() {
        when(documentRepository.findByWorkspaceIdAndSlug(workspaceId, "a")).thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        authoring.upsert(workspaceId, draft("a", "A", "body"), actorId, AuthoringOrigin.SYNC);

        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditTrail).record(eq(actorId), entry.capture());
        assertThat(entry.getValue().detail()).containsEntry("origin", AuthoringOrigin.SYNC);
    }

    /** A webhook has no signed-in user, and the contract permits that. */
    @Test
    void acceptsAnAbsentActor() {
        when(documentRepository.findByWorkspaceIdAndSlug(workspaceId, "a")).thenReturn(Optional.empty());
        when(documentRepository.save(any())).thenAnswer(call -> call.getArgument(0));

        authoring.upsert(workspaceId, draft("a", "A", "body"), null, AuthoringOrigin.SYNC);

        verify(auditTrail).record(eq(null), any(AuditEntry.class));
        verify(history).snapshot(any(), eq(RevisionReason.SYNCED), any(), eq(null));
    }
}
