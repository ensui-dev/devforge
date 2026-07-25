package com.devforge.document.application;

import com.devforge.document.contract.DocumentType;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentRepository;
import com.devforge.shared.application.PageResponse;
import com.devforge.shared.exception.DuplicateResourceException;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Documentation pages within a workspace.
 *
 * <p>Depends on {@link WorkspaceAccess} — an interface published by the workspace
 * module — rather than on {@code WorkspaceService}. Nothing here can touch
 * workspace persistence, and the dependency is a single narrow method that is
 * trivial to stub in tests.
 */
@Service
@Transactional(readOnly = true)
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final WorkspaceAccess workspaceAccess;

    public DocumentService(DocumentRepository documentRepository, WorkspaceAccess workspaceAccess) {
        this.documentRepository = documentRepository;
        this.workspaceAccess = workspaceAccess;
    }

    public PageResponse<DocumentSummaryResponse> findByWorkspace(
            UUID workspaceId,
            UUID userId,
            DocumentType documentType,
            Pageable pageable
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);

        Page<Document> page = documentType == null
                ? documentRepository.findByWorkspaceIdOrderByTitleAsc(workspaceId, pageable)
                : documentRepository.findByWorkspaceIdAndDocumentTypeOrderByTitleAsc(
                        workspaceId, documentType, pageable);

        return PageResponse.of(page, DocumentSummaryResponse::from);
    }

    /** Ranked full-text search across the workspace's documentation. */
    public PageResponse<DocumentSummaryResponse> search(
            UUID workspaceId,
            UUID userId,
            String query,
            Pageable pageable
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
        return PageResponse.of(
                documentRepository.search(workspaceId, query, pageable),
                DocumentSummaryResponse::from);
    }

    public DocumentResponse findById(UUID workspaceId, UUID documentId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
        return DocumentResponse.from(loadDocument(workspaceId, documentId));
    }

    public DocumentResponse findBySlug(UUID workspaceId, String slug, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
        return DocumentResponse.from(documentRepository.findByWorkspaceIdAndSlug(workspaceId, slug)
                .orElseThrow(() -> new ResourceNotFoundException("Document", slug)));
    }

    @Transactional
    public DocumentResponse create(UUID workspaceId, CreateDocumentRequest request, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);

        if (documentRepository.existsByWorkspaceIdAndSlug(workspaceId, request.slug())) {
            throw new DuplicateResourceException("Document slug already exists: " + request.slug());
        }

        Document document = documentRepository.save(new Document(
                workspaceId,
                request.title(),
                request.slug(),
                request.content(),
                request.documentType()
        ));
        return DocumentResponse.from(document);
    }

    @Transactional
    public DocumentResponse update(
            UUID workspaceId,
            UUID documentId,
            UpdateDocumentRequest request,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        Document document = loadDocument(workspaceId, documentId);

        if (!document.getSlug().equals(request.slug())
                && documentRepository.existsByWorkspaceIdAndSlug(workspaceId, request.slug())) {
            throw new DuplicateResourceException("Document slug already exists: " + request.slug());
        }

        document.revise(request.title(), request.slug(), request.content(), request.documentType());
        return DocumentResponse.from(document);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID documentId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        // References to and from this document, and any task links, cascade.
        documentRepository.delete(loadDocument(workspaceId, documentId));
    }

    private Document loadDocument(UUID workspaceId, UUID documentId) {
        return documentRepository.findByIdAndWorkspaceId(documentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }
}
