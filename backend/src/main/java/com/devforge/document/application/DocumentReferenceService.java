package com.devforge.document.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.DocumentRef;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentReference;
import com.devforge.document.domain.DocumentReferenceRepository;
import com.devforge.document.domain.DocumentRepository;
import com.devforge.shared.exception.DomainValidationException;
import com.devforge.shared.exception.DuplicateResourceException;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The document graph: creating, listing, and removing typed links.
 *
 * <p>Split out from {@link DocumentService} because it is a distinct
 * responsibility with its own rules (both endpoints must exist in the same
 * workspace, no self-links, no duplicate typed edges) and its own repository.
 * Keeping it separate stops {@code DocumentService} from becoming the module's
 * catch-all.
 */
@Service
@Transactional(readOnly = true)
public class DocumentReferenceService {

    private final DocumentRepository documentRepository;
    private final DocumentReferenceRepository referenceRepository;
    private final WorkspaceAccess workspaceAccess;
    private final AuditTrail auditTrail;

    public DocumentReferenceService(
            DocumentRepository documentRepository,
            DocumentReferenceRepository referenceRepository,
            WorkspaceAccess workspaceAccess,
            AuditTrail auditTrail
    ) {
        this.documentRepository = documentRepository;
        this.referenceRepository = referenceRepository;
        this.workspaceAccess = workspaceAccess;
        this.auditTrail = auditTrail;
    }

    /**
     * All links touching the document — outgoing links and backlinks together.
     *
     * <p>Backlinks are the feature that makes the knowledge base navigable in the
     * direction that matters when changing something: "what already depends on
     * this page?"
     */
    public List<DocumentReferenceResponse> findReferences(UUID workspaceId, UUID documentId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
        requireDocument(workspaceId, documentId);

        List<DocumentReference> references = referenceRepository.findAllTouching(documentId);
        if (references.isEmpty()) {
            return List.of();
        }

        // Resolve every far end in one query rather than one per edge.
        Map<UUID, DocumentRef> related = documentRepository.findByWorkspaceIdAndIdIn(
                        workspaceId,
                        references.stream().map(reference -> reference.otherEnd(documentId)).distinct().toList())
                .stream()
                .map(DocumentDirectoryService::toRef)
                .collect(java.util.stream.Collectors.toMap(DocumentRef::id, ref -> ref));

        return references.stream()
                .map(reference -> DocumentReferenceResponse.of(
                        reference,
                        documentId,
                        related.get(reference.otherEnd(documentId))))
                .toList();
    }

    @Transactional
    public DocumentReferenceResponse createReference(
            UUID workspaceId,
            UUID sourceDocumentId,
            CreateDocumentReferenceRequest request,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);

        if (sourceDocumentId.equals(request.targetDocumentId())) {
            throw new DomainValidationException("A document cannot reference itself");
        }

        // Loading both ends scoped to the workspace is what prevents linking
        // across team boundaries.
        requireDocument(workspaceId, sourceDocumentId);
        Document target = requireDocument(workspaceId, request.targetDocumentId());

        if (referenceRepository.existsBySourceDocumentIdAndTargetDocumentIdAndReferenceType(
                sourceDocumentId, request.targetDocumentId(), request.referenceType())) {
            throw new DuplicateResourceException(
                    "A %s reference to this document already exists".formatted(request.referenceType()));
        }

        DocumentReference reference = referenceRepository.save(new DocumentReference(
                sourceDocumentId,
                request.targetDocumentId(),
                request.referenceType()
        ));

        auditTrail.record(userId, AuditEntry
                .of(AuditAction.DOCUMENT_LINKED, AuditTargetType.DOCUMENT)
                .target(sourceDocumentId, target.getTitle())
                .inWorkspace(workspaceId)
                .with("referenceType", request.referenceType())
                .with("targetDocumentId", request.targetDocumentId()));

        return DocumentReferenceResponse.of(
                reference,
                sourceDocumentId,
                DocumentDirectoryService.toRef(target));
    }

    @Transactional
    public void deleteReference(UUID workspaceId, UUID documentId, UUID referenceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        requireDocument(workspaceId, documentId);

        // Scoping by source id means a link can only be removed from the page that
        // declared it, so a backlink cannot be deleted out from under its owner.
        DocumentReference reference = referenceRepository.findByIdAndSourceDocumentId(referenceId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document reference", referenceId));

        auditTrail.record(userId, AuditEntry
                .of(AuditAction.DOCUMENT_UNLINKED, AuditTargetType.DOCUMENT)
                .target(documentId, null)
                .inWorkspace(workspaceId)
                .with("referenceType", reference.getReferenceType())
                .with("targetDocumentId", reference.getTargetDocumentId()));

        referenceRepository.delete(reference);
    }

    private Document requireDocument(UUID workspaceId, UUID documentId) {
        return documentRepository.findByIdAndWorkspaceId(documentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }
}
