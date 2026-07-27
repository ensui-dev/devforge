package com.devforge.document.application;

import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.DocumentType;
import com.devforge.document.contract.ReferenceType;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentReference;
import com.devforge.document.domain.DocumentReferenceRepository;
import com.devforge.document.domain.DocumentRevisionRepository;
import com.devforge.document.domain.DocumentRepository;
import com.devforge.shared.exception.DomainValidationException;
import com.devforge.shared.exception.DuplicateResourceException;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRef;
import com.devforge.workspace.contract.WorkspaceRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentReferenceServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocumentReferenceRepository referenceRepository;

    @Mock
    private DocumentRevisionRepository revisionRepository;

    @Mock
    private DocumentHistoryService history;

    @Mock
    private WorkspaceAccess workspaceAccess;

    @Mock
    private AuditTrail auditTrail;

    @Mock
    private DocumentChangeAnnouncer announcer;

    @InjectMocks
    private DocumentReferenceService referenceService;

    private UUID workspaceId;
    private UUID userId;
    private Document source;
    private Document target;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        source = new Document(workspaceId, "Auth Flow", "auth-flow", "", DocumentType.ARCHITECTURE, false);
        target = new Document(workspaceId, "OAuth Setup", "oauth-setup", "", DocumentType.PROCEDURE, false);
    }

    @Test
    void createsATypedLink() {
        givenAccess(WorkspaceRole.MEMBER);
        givenDocument(source);
        givenDocument(target);
        when(referenceRepository.existsBySourceDocumentIdAndTargetDocumentIdAndReferenceType(
                source.getId(), target.getId(), ReferenceType.DEPENDS_ON)).thenReturn(false);
        when(referenceRepository.save(any(DocumentReference.class))).thenAnswer(call -> call.getArgument(0));

        DocumentReferenceResponse response = referenceService.createReference(
                workspaceId,
                source.getId(),
                new CreateDocumentReferenceRequest(target.getId(), ReferenceType.DEPENDS_ON),
                userId);

        assertThat(response.referenceType()).isEqualTo(ReferenceType.DEPENDS_ON);
        assertThat(response.outgoing()).isTrue();
        assertThat(response.relatedDocumentId()).isEqualTo(target.getId());
        assertThat(response.relatedDocumentTitle()).isEqualTo("OAuth Setup");
    }

    @Test
    void rejectsASelfReference() {
        givenAccess(WorkspaceRole.MEMBER);

        assertThatThrownBy(() -> referenceService.createReference(
                workspaceId,
                source.getId(),
                new CreateDocumentReferenceRequest(source.getId(), ReferenceType.RELATED),
                userId))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("cannot reference itself");

        verify(referenceRepository, never()).save(any());
    }

    @Test
    void rejectsADuplicateTypedLink() {
        givenAccess(WorkspaceRole.MEMBER);
        givenDocument(source);
        givenDocument(target);
        when(referenceRepository.existsBySourceDocumentIdAndTargetDocumentIdAndReferenceType(
                source.getId(), target.getId(), ReferenceType.RELATED)).thenReturn(true);

        assertThatThrownBy(() -> referenceService.createReference(
                workspaceId,
                source.getId(),
                new CreateDocumentReferenceRequest(target.getId(), ReferenceType.RELATED),
                userId))
                .isInstanceOf(DuplicateResourceException.class);
    }

    /** Cross-workspace linking must be impossible even with a valid document id. */
    @Test
    void rejectsLinkingToADocumentInAnotherWorkspace() {
        givenAccess(WorkspaceRole.MEMBER);
        givenDocument(source);
        UUID foreignId = UUID.randomUUID();
        when(documentRepository.findByIdAndWorkspaceId(foreignId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> referenceService.createReference(
                workspaceId,
                source.getId(),
                new CreateDocumentReferenceRequest(foreignId, ReferenceType.RELATED),
                userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reportsOutgoingLinksAndBacklinksWithDirection() {
        givenAccess(WorkspaceRole.VIEWER);
        givenDocument(source);

        DocumentReference outgoing = new DocumentReference(
                source.getId(), target.getId(), ReferenceType.DEPENDS_ON);
        DocumentReference incoming = new DocumentReference(
                target.getId(), source.getId(), ReferenceType.IMPLEMENTS);

        when(referenceRepository.findAllTouching(source.getId())).thenReturn(List.of(outgoing, incoming));
        when(documentRepository.findByWorkspaceIdAndIdIn(workspaceId, List.of(target.getId())))
                .thenReturn(List.of(target));

        List<DocumentReferenceResponse> references =
                referenceService.findReferences(workspaceId, source.getId(), userId);

        assertThat(references).hasSize(2);
        assertThat(references).filteredOn(DocumentReferenceResponse::outgoing)
                .singleElement()
                .satisfies(reference -> assertThat(reference.referenceType())
                        .isEqualTo(ReferenceType.DEPENDS_ON));
        // The backlink resolves to the same far-end document, seen from the other side.
        assertThat(references).filteredOn(reference -> !reference.outgoing())
                .singleElement()
                .satisfies(reference -> {
                    assertThat(reference.referenceType()).isEqualTo(ReferenceType.IMPLEMENTS);
                    assertThat(reference.relatedDocumentId()).isEqualTo(target.getId());
                });
    }

    @Test
    void returnsAnEmptyListWhenNothingLinks() {
        givenAccess(WorkspaceRole.VIEWER);
        givenDocument(source);
        when(referenceRepository.findAllTouching(source.getId())).thenReturn(List.of());

        assertThat(referenceService.findReferences(workspaceId, source.getId(), userId)).isEmpty();
        verify(documentRepository, never()).findByWorkspaceIdAndIdIn(any(), any());
    }

    @Test
    void deletesAReferenceDeclaredByTheDocument() {
        givenAccess(WorkspaceRole.MEMBER);
        givenDocument(source);
        DocumentReference reference = new DocumentReference(
                source.getId(), target.getId(), ReferenceType.RELATED);
        when(referenceRepository.findByIdAndSourceDocumentId(reference.getId(), source.getId()))
                .thenReturn(Optional.of(reference));

        referenceService.deleteReference(workspaceId, source.getId(), reference.getId(), userId);

        verify(referenceRepository).delete(reference);
    }

    /** A backlink belongs to the other document, so it cannot be deleted from here. */
    @Test
    void refusesToDeleteALinkItDoesNotOwn() {
        givenAccess(WorkspaceRole.MEMBER);
        givenDocument(source);
        UUID referenceId = UUID.randomUUID();
        when(referenceRepository.findByIdAndSourceDocumentId(referenceId, source.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                referenceService.deleteReference(workspaceId, source.getId(), referenceId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void givenAccess(WorkspaceRole role) {
        when(workspaceAccess.requireAccess(any(), any(), any()))
                .thenReturn(new WorkspaceRef(workspaceId, "Platform", "platform", role));
    }

    private void givenDocument(Document document) {
        when(documentRepository.findByIdAndWorkspaceId(document.getId(), workspaceId))
                .thenReturn(Optional.of(document));
    }
}
