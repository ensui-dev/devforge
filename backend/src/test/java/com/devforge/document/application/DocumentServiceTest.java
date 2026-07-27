package com.devforge.document.application;

import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.DocumentType;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentRepository;
import com.devforge.shared.application.PageResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Note what this test needs: a {@link WorkspaceAccess} stub and nothing else from
 * the workspace module. Before the refactor a document test had to build a real
 * {@code Workspace} entity and stub {@code WorkspaceService}.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private WorkspaceAccess workspaceAccess;

    @Mock
    private AuditTrail auditTrail;

    @Mock
    private DocumentHistoryService documentHistoryService;

    @Mock
    private DocumentChangeAnnouncer announcer;

    @InjectMocks
    private DocumentService documentService;

    private UUID workspaceId;
    private UUID userId;
    private Document document;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        document = new Document(workspaceId, "Auth Flow", "auth-flow", "Details", DocumentType.ARCHITECTURE, false);
    }

    @Test
    void createsADocumentForAMember() {
        givenAccess(WorkspaceRole.MEMBER);
        when(documentRepository.existsByWorkspaceIdAndSlug(workspaceId, "auth-flow")).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));

        DocumentResponse response = documentService.create(
                workspaceId,
                new CreateDocumentRequest("Auth Flow", "auth-flow", "Details", DocumentType.ARCHITECTURE, false),
                userId);

        assertThat(response.title()).isEqualTo("Auth Flow");
        assertThat(response.workspaceId()).isEqualTo(workspaceId);
        verify(workspaceAccess).requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
    }

    @Test
    void defaultsNullContentToEmptyString() {
        givenAccess(WorkspaceRole.MEMBER);
        when(documentRepository.existsByWorkspaceIdAndSlug(any(), any())).thenReturn(false);
        when(documentRepository.save(any(Document.class))).thenAnswer(call -> call.getArgument(0));

        DocumentResponse response = documentService.create(
                workspaceId,
                new CreateDocumentRequest("Empty", "empty", null, DocumentType.GENERAL, false),
                userId);

        assertThat(response.content()).isEmpty();
    }

    @Test
    void rejectsADuplicateSlug() {
        givenAccess(WorkspaceRole.MEMBER);
        when(documentRepository.existsByWorkspaceIdAndSlug(workspaceId, "auth-flow")).thenReturn(true);

        assertThatThrownBy(() -> documentService.create(
                workspaceId,
                new CreateDocumentRequest("Auth Flow", "auth-flow", "Details", DocumentType.ARCHITECTURE, false),
                userId))
                .isInstanceOf(DuplicateResourceException.class);

        verify(documentRepository, never()).save(any());
    }

    @Test
    void listingRequiresOnlyViewerAccess() {
        givenAccess(WorkspaceRole.VIEWER);
        when(documentRepository.findByWorkspaceIdOrderByTitleAsc(eq(workspaceId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document)));

        PageResponse<DocumentSummaryResponse> page = documentService.findByWorkspace(
                workspaceId, userId, null, PageRequest.of(0, 25));

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().title()).isEqualTo("Auth Flow");
        verify(workspaceAccess).requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
    }

    @Test
    void filtersByDocumentTypeWhenRequested() {
        givenAccess(WorkspaceRole.VIEWER);
        when(documentRepository.findByWorkspaceIdAndDocumentTypeOrderByTitleAsc(
                eq(workspaceId), eq(DocumentType.ARCHITECTURE), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document)));

        PageResponse<DocumentSummaryResponse> page = documentService.findByWorkspace(
                workspaceId, userId, DocumentType.ARCHITECTURE, PageRequest.of(0, 25));

        assertThat(page.content()).hasSize(1);
        verify(documentRepository, never())
                .findByWorkspaceIdOrderByTitleAsc(any(), any(Pageable.class));
    }

    @Test
    void searchesThroughTheRepository() {
        givenAccess(WorkspaceRole.VIEWER);
        when(documentRepository.search(eq(workspaceId), eq("auth"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(document)));

        assertThat(documentService.search(workspaceId, userId, "auth", PageRequest.of(0, 25)).content())
                .hasSize(1);
    }

    @Test
    void updatesADocument() {
        givenAccess(WorkspaceRole.MEMBER);
        when(documentRepository.findByIdAndWorkspaceId(document.getId(), workspaceId))
                .thenReturn(Optional.of(document));

        DocumentResponse response = documentService.update(
                workspaceId,
                document.getId(),
                new UpdateDocumentRequest("Renamed", "auth-flow", "New body", DocumentType.DECISION, false),
                userId);

        assertThat(response.title()).isEqualTo("Renamed");
        assertThat(response.content()).isEqualTo("New body");
        assertThat(response.documentType()).isEqualTo(DocumentType.DECISION);
        // Slug unchanged, so no uniqueness probe was needed.
        verify(documentRepository, never()).existsByWorkspaceIdAndSlug(any(), any());
    }

    @Test
    void rejectsAnUpdateThatTakesAnotherDocumentsSlug() {
        givenAccess(WorkspaceRole.MEMBER);
        when(documentRepository.findByIdAndWorkspaceId(document.getId(), workspaceId))
                .thenReturn(Optional.of(document));
        when(documentRepository.existsByWorkspaceIdAndSlug(workspaceId, "taken")).thenReturn(true);

        assertThatThrownBy(() -> documentService.update(
                workspaceId,
                document.getId(),
                new UpdateDocumentRequest("Auth Flow", "taken", "body", DocumentType.GENERAL, false),
                userId))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void reportsNotFoundForADocumentInAnotherWorkspace() {
        givenAccess(WorkspaceRole.VIEWER);
        UUID documentId = UUID.randomUUID();
        when(documentRepository.findByIdAndWorkspaceId(documentId, workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.findById(workspaceId, documentId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void findsBySlugForDeepLinks() {
        givenAccess(WorkspaceRole.VIEWER);
        when(documentRepository.findByWorkspaceIdAndSlug(workspaceId, "auth-flow"))
                .thenReturn(Optional.of(document));

        assertThat(documentService.findBySlug(workspaceId, "auth-flow", userId).slug()).isEqualTo("auth-flow");
    }

    @Test
    void deletesADocument() {
        givenAccess(WorkspaceRole.MEMBER);
        when(documentRepository.findByIdAndWorkspaceId(document.getId(), workspaceId))
                .thenReturn(Optional.of(document));

        documentService.delete(workspaceId, document.getId(), userId);

        verify(documentRepository).delete(document);
    }

    private void givenAccess(WorkspaceRole role) {
        when(workspaceAccess.requireAccess(any(), any(), any()))
                .thenReturn(new WorkspaceRef(workspaceId, "Platform", "platform", role));
    }
}
