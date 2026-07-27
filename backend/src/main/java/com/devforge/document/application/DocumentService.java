package com.devforge.document.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.AuthoringOrigin;
import com.devforge.document.contract.DocumentType;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentRepository;
import com.devforge.document.domain.RevisionReason;
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
    private final DocumentHistoryService history;
    private final AuditTrail auditTrail;
    private final DocumentChangeAnnouncer announcer;

    public DocumentService(
            DocumentRepository documentRepository,
            WorkspaceAccess workspaceAccess,
            DocumentHistoryService history,
            AuditTrail auditTrail,
            DocumentChangeAnnouncer announcer
    ) {
        this.documentRepository = documentRepository;
        this.workspaceAccess = workspaceAccess;
        this.history = history;
        this.auditTrail = auditTrail;
        this.announcer = announcer;
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
                request.documentType(),
                request.isInternal()
        ));

        // Revision 1 is what the document was created as, so history is never
        // missing its own beginning.
        history.snapshot(document, RevisionReason.CREATED, null, userId);
        auditTrail.record(userId, AuditEntry
                .of(AuditAction.DOCUMENT_CREATED, AuditTargetType.DOCUMENT)
                .target(document.getId(), document.getTitle())
                .inWorkspace(workspaceId)
                .with("documentType", document.getDocumentType())
                .with("internal", document.isInternal()));

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

        // Captured before the edit so the log can say what actually changed
        // rather than listing every field on every save.
        String previousTitle = document.getTitle();
        String previousSlug = document.getSlug();
        DocumentType previousType = document.getDocumentType();
        boolean previousInternal = document.isInternal();
        int previousLength = document.getContent().length();

        document.revise(
                request.title(),
                request.slug(),
                request.content(),
                request.documentType(),
                request.isInternal());

        // Git refuses an empty commit; so does this. Saving a document without
        // changing it must not append a revision that says nothing, or an audit
        // entry with no changed fields — hitting save twice would otherwise fill
        // both with noise.
        if (!history.differsFromLatest(document)) {
            return DocumentResponse.from(document);
        }

        history.snapshot(document, RevisionReason.UPDATED, null, userId);
        auditTrail.record(userId, AuditEntry
                .of(AuditAction.DOCUMENT_UPDATED, AuditTargetType.DOCUMENT)
                .target(document.getId(), document.getTitle())
                .inWorkspace(workspaceId)
                .changed("title", previousTitle, document.getTitle())
                .changed("slug", previousSlug, document.getSlug())
                .changed("documentType", previousType, document.getDocumentType())
                .changed("internal", previousInternal, document.isInternal())
                .changed("contentLength", previousLength, document.getContent().length()));

        return DocumentResponse.from(document);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID documentId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        Document document = loadDocument(workspaceId, documentId);

        // Recorded before the delete, while the title still exists to record.
        auditTrail.record(userId, AuditEntry
                .of(AuditAction.DOCUMENT_DELETED, AuditTargetType.DOCUMENT)
                .target(document.getId(), document.getTitle())
                .inWorkspace(workspaceId)
                .with("slug", document.getSlug()));

        // Announced before the delete, for the same reason: afterwards there is
        // nothing left to describe.
        announcer.removed(document, AuthoringOrigin.DIRECT, userId);

        // References to and from this document, its revisions, and any task links
        // all cascade. The audit entry is what survives.
        documentRepository.delete(document);
    }

    private Document loadDocument(UUID workspaceId, UUID documentId) {
        return documentRepository.findByIdAndWorkspaceId(documentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }
}
