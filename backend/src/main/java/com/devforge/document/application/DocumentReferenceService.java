package com.devforge.document.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.AuthoringOrigin;
import com.devforge.document.contract.DocumentRef;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentReference;
import com.devforge.document.domain.DocumentReferenceRepository;
import com.devforge.document.domain.DocumentRepository;
import com.devforge.document.domain.DocumentRevision;
import com.devforge.document.domain.DocumentRevisionRepository;
import com.devforge.document.domain.LastChange;
import com.devforge.shared.exception.DomainValidationException;
import com.devforge.shared.exception.DuplicateResourceException;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final DocumentRevisionRepository revisionRepository;
    private final DocumentHistoryService history;
    private final WorkspaceAccess workspaceAccess;
    private final AuditTrail auditTrail;
    private final DocumentChangeAnnouncer announcer;

    public DocumentReferenceService(
            DocumentRepository documentRepository,
            DocumentReferenceRepository referenceRepository,
            DocumentRevisionRepository revisionRepository,
            DocumentHistoryService history,
            WorkspaceAccess workspaceAccess,
            AuditTrail auditTrail,
            DocumentChangeAnnouncer announcer
    ) {
        this.documentRepository = documentRepository;
        this.referenceRepository = referenceRepository;
        this.revisionRepository = revisionRepository;
        this.history = history;
        this.workspaceAccess = workspaceAccess;
        this.auditTrail = auditTrail;
        this.announcer = announcer;
    }

    /**
     * All links touching the document — outgoing links and backlinks together,
     * each saying whether the two ends have fallen out of step.
     *
     * <p>Backlinks are the feature that makes the knowledge base navigable in the
     * direction that matters when changing something: "what already depends on
     * this page?" Knowing <em>when</em> each end last changed turns that into an
     * answer rather than a list to check by hand.
     *
     * <p>Four queries whatever the size of the graph: the edges, the far ends,
     * their titles, and when everything involved last changed.
     */
    public List<DocumentReferenceResponse> findReferences(UUID workspaceId, UUID documentId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
        requireDocument(workspaceId, documentId);

        List<DocumentReference> references = referenceRepository.findAllTouching(documentId);
        if (references.isEmpty()) {
            return List.of();
        }

        List<UUID> farEnds = references.stream()
                .map(reference -> reference.otherEnd(documentId))
                .distinct()
                .toList();

        // Resolve every far end in one query rather than one per edge.
        Map<UUID, DocumentRef> related = documentRepository
                .findByWorkspaceIdAndIdIn(workspaceId, farEnds)
                .stream()
                .map(DocumentDirectoryService::toRef)
                .collect(java.util.stream.Collectors.toMap(DocumentRef::id, ref -> ref));

        // This document and every neighbour, so each edge can be compared without
        // a query of its own.
        Map<UUID, Instant> changed = LastChange.byDocument(revisionRepository.lastChangedAt(
                java.util.stream.Stream.concat(farEnds.stream(), java.util.stream.Stream.of(documentId))
                        .toList()));
        Instant viewedChangedAt = changed.get(documentId);

        return references.stream()
                .map(reference -> {
                    UUID farEnd = reference.otherEnd(documentId);
                    return DocumentReferenceResponse.of(
                            reference,
                            documentId,
                            related.get(farEnd),
                            viewedChangedAt,
                            changed.get(farEnd));
                })
                .toList();
    }

    /**
     * What a linked page has changed since this one last kept up with it.
     *
     * <p>The comparison the marker on the panel promises. "Since" is the moment the
     * page doing the depending was last revised, so the diff is exactly what
     * arrived after somebody last looked — not the linked page's whole history,
     * which is a different question with its own screen.
     */
    public ReferenceChangesResponse findChanges(
            UUID workspaceId,
            UUID documentId,
            UUID referenceId,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
        requireDocument(workspaceId, documentId);

        DocumentReference reference = referenceRepository.findById(referenceId)
                .filter(edge -> edge.touches(documentId))
                .orElseThrow(() -> new ResourceNotFoundException("Document reference", referenceId));

        // Scoped to the workspace, so an edge cannot be used to read across one.
        Document related = requireDocument(workspaceId, reference.otherEnd(documentId));

        Instant since = revisionRepository.findFirstByDocumentIdOrderByRevisionDesc(documentId)
                .map(DocumentRevision::getCreatedAt)
                .orElseThrow(() -> new ResourceNotFoundException("Revision", documentId));

        DocumentRevision now = revisionRepository
                .findFirstByDocumentIdOrderByRevisionDesc(related.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Revision", related.getId()));

        // The revision that was current then. Absent means the linked page did not
        // exist yet, which is worth saying rather than rendering as one enormous
        // addition.
        Optional<DocumentRevision> then = revisionRepository
                .findFirstByDocumentIdAndCreatedAtLessThanEqualOrderByRevisionDesc(
                        related.getId(), since);

        return new ReferenceChangesResponse(
                related.getTitle(),
                related.getSlug(),
                since,
                then.map(DocumentRevision::getRevision).orElse(null),
                then.map(history::bodyOf).orElse(null),
                now.getRevision(),
                history.bodyOf(now),
                now.getCreatedAt());
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
        Document source = requireDocument(workspaceId, sourceDocumentId);
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
        // A link is part of what the source page says, so anything mirroring that
        // page needs to hear about it.
        announcer.written(source, AuthoringOrigin.DIRECT, null, userId);

        return DocumentReferenceResponse.of(
                reference,
                sourceDocumentId,
                DocumentDirectoryService.toRef(target));
    }

    @Transactional
    public void deleteReference(UUID workspaceId, UUID documentId, UUID referenceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        Document source = requireDocument(workspaceId, documentId);

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
        // After the delete, so the announcement describes the links that remain.
        announcer.written(source, AuthoringOrigin.DIRECT, null, userId);
    }

    private Document requireDocument(UUID workspaceId, UUID documentId) {
        return documentRepository.findByIdAndWorkspaceId(documentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }
}
