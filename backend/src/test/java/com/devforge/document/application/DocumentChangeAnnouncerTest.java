package com.devforge.document.application;

import com.devforge.document.contract.AuthoringOrigin;
import com.devforge.document.contract.DeclaredReference;
import com.devforge.document.contract.DocumentChanged;
import com.devforge.document.contract.DocumentType;
import com.devforge.document.contract.ReferenceType;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentReference;
import com.devforge.document.domain.DocumentReferenceRepository;
import com.devforge.document.domain.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the rest of the instance is told when a document changes.
 *
 * <p>The event has to stand on its own: a listener runs after the transaction has
 * committed, and for a removal there is nothing left to look up. These tests are
 * about whether it really does — particularly the links, which are stored as ids
 * and have to leave here as slugs.
 */
@ExtendWith(MockitoExtension.class)
class DocumentChangeAnnouncerTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentReferenceRepository referenceRepository;

    @Mock
    private ApplicationEventPublisher events;

    @InjectMocks
    private DocumentChangeAnnouncer announcer;

    private UUID workspaceId;
    private UUID actorId;
    private Document document;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        document = new Document(
                workspaceId, "Event ingestion", "design", "the body",
                DocumentType.ARCHITECTURE, false);
    }

    private DocumentChanged published() {
        ArgumentCaptor<DocumentChanged> captor = ArgumentCaptor.forClass(DocumentChanged.class);
        verify(events).publishEvent(captor.capture());
        return captor.getValue();
    }

    @Test
    void describesTheDocumentInFull() {
        when(referenceRepository.findBySourceDocumentId(document.getId())).thenReturn(List.of());

        announcer.written(document, AuthoringOrigin.DIRECT, null, actorId);

        DocumentChanged change = published();
        assertThat(change.change()).isEqualTo(DocumentChanged.Change.WRITTEN);
        assertThat(change.workspaceId()).isEqualTo(workspaceId);
        assertThat(change.slug()).isEqualTo("design");
        assertThat(change.title()).isEqualTo("Event ingestion");
        assertThat(change.content()).isEqualTo("the body");
        assertThat(change.documentType()).isEqualTo(DocumentType.ARCHITECTURE);
        assertThat(change.internal()).isFalse();
        assertThat(change.actorId()).isEqualTo(actorId);
        assertThat(change.origin()).isEqualTo(AuthoringOrigin.DIRECT);
        assertThat(change.renamed()).isFalse();
    }

    /** Ids mean nothing outside DevForge; a file names what it points at by slug. */
    @Test
    void namesLinkedDocumentsByTheirSlug()  {
        Document target = new Document(
                workspaceId, "Kafka topics", "kafka-topics", "", DocumentType.GENERAL, false);
        when(referenceRepository.findBySourceDocumentId(document.getId())).thenReturn(
                List.of(new DocumentReference(document.getId(), target.getId(), ReferenceType.DEPENDS_ON)));
        when(documentRepository.findByWorkspaceIdAndIdIn(eq(workspaceId), anyList()))
                .thenReturn(List.of(target));

        announcer.written(document, AuthoringOrigin.DIRECT, null, actorId);

        assertThat(published().references()).containsExactly(
                new DeclaredReference(ReferenceType.DEPENDS_ON, "kafka-topics"));
    }

    /**
     * A link whose far end has gone is dropped rather than announced with nothing
     * on the other side — a file cannot name a page that is not there.
     */
    @Test
    void dropsALinkWhoseTargetNoLongerExists() {
        when(referenceRepository.findBySourceDocumentId(document.getId())).thenReturn(
                List.of(new DocumentReference(document.getId(), UUID.randomUUID(), ReferenceType.RELATED)));
        when(documentRepository.findByWorkspaceIdAndIdIn(eq(workspaceId), anyList()))
                .thenReturn(List.of());

        announcer.written(document, AuthoringOrigin.DIRECT, null, actorId);

        assertThat(published().references()).isEmpty();
    }

    @Test
    void saysWhatTheSlugUsedToBe() {
        when(referenceRepository.findBySourceDocumentId(document.getId())).thenReturn(List.of());

        announcer.written(document, AuthoringOrigin.DIRECT, "old-design", actorId);

        assertThat(published().renamed()).isTrue();
        assertThat(published().previousSlug()).isEqualTo("old-design");
    }

    /**
     * A removal is announced without reading anything: by the time a listener acts
     * the row is gone, and the slug is all it needs anyway.
     */
    @Test
    void describesARemovalFromWhatItAlreadyHolds() {
        announcer.removed(document, AuthoringOrigin.DIRECT, actorId);

        DocumentChanged change = published();
        assertThat(change.change()).isEqualTo(DocumentChanged.Change.REMOVED);
        assertThat(change.slug()).isEqualTo("design");
        assertThat(change.references()).isEmpty();
        verify(referenceRepository, never()).findBySourceDocumentId(any());
    }

    @Test
    void carriesTheOriginSoAListenerCanRecogniseItsOwnEcho() {
        when(referenceRepository.findBySourceDocumentId(document.getId())).thenReturn(List.of());

        announcer.written(document, AuthoringOrigin.SYNC, null, null);

        assertThat(published().origin()).isEqualTo(AuthoringOrigin.SYNC);
    }
}
