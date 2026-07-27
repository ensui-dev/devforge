package com.devforge.document.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.AuthoringOrigin;
import com.devforge.document.contract.DocumentAuthoring;
import com.devforge.document.contract.DocumentDraft;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentRepository;
import com.devforge.document.domain.RevisionReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The document module's implementation of its own authoring contract.
 *
 * <p>Lives here, inside the module, so that everything a write guarantees is
 * applied once: a revision per change, content stored once per distinct body, and
 * no revision at all when nothing moved. A caller outside the module gets those
 * guarantees without being able to reach the entities.
 *
 * <p>Applies no access control, as its contract documents — the caller has already
 * decided the change is permitted, which matters because a webhook has no
 * signed-in user to check.
 */
@Service
@Transactional
public class DocumentAuthoringService implements DocumentAuthoring {

    private final DocumentRepository documentRepository;
    private final DocumentHistoryService history;
    private final AuditTrail auditTrail;

    public DocumentAuthoringService(
            DocumentRepository documentRepository,
            DocumentHistoryService history,
            AuditTrail auditTrail
    ) {
        this.documentRepository = documentRepository;
        this.history = history;
        this.auditTrail = auditTrail;
    }

    @Override
    public AuthoringResult upsert(
            UUID workspaceId,
            DocumentDraft draft,
            UUID actorId,
            AuthoringOrigin origin
    ) {
        Optional<Document> existing =
                documentRepository.findByWorkspaceIdAndSlug(workspaceId, draft.slug());

        if (existing.isEmpty()) {
            Document created = documentRepository.save(new Document(
                    workspaceId,
                    draft.title(),
                    draft.slug(),
                    draft.content(),
                    draft.documentType(),
                    draft.internal()));

            history.snapshot(created, createReason(origin), null, actorId);
            record(actorId, AuditAction.DOCUMENT_CREATED, created, workspaceId, origin, null);
            return AuthoringResult.CREATED;
        }

        Document document = existing.get();
        String previousTitle = document.getTitle();

        document.revise(
                draft.title(),
                draft.slug(),
                draft.content(),
                draft.documentType(),
                draft.internal());

        // The same rule the editor obeys: a save that changes nothing writes
        // nothing. It matters more here — a sync re-applies every file every time,
        // so without this one push would append a revision to every page.
        if (!history.differsFromLatest(document)) {
            return AuthoringResult.UNCHANGED;
        }

        history.snapshot(document, updateReason(origin), null, actorId);
        record(actorId, AuditAction.DOCUMENT_UPDATED, document, workspaceId, origin, previousTitle);
        return AuthoringResult.UPDATED;
    }

    @Override
    public boolean archiveBySlug(
            UUID workspaceId,
            String slug,
            UUID actorId,
            AuthoringOrigin origin
    ) {
        Optional<Document> found = documentRepository.findByWorkspaceIdAndSlug(workspaceId, slug);
        if (found.isEmpty()) {
            return false;
        }

        Document document = found.get();
        if (document.isInternal()) {
            // Already withdrawn; re-archiving would add a revision saying nothing.
            return false;
        }

        document.revise(
                document.getTitle(),
                document.getSlug(),
                document.getContent(),
                document.getDocumentType(),
                true);

        history.snapshot(document, updateReason(origin), null, actorId);
        auditTrail.record(actorId, AuditEntry
                .of(AuditAction.DOCUMENT_UPDATED, AuditTargetType.DOCUMENT)
                .target(document.getId(), document.getTitle())
                .inWorkspace(workspaceId)
                .with("origin", origin)
                .with("archived", true)
                .changed("internal", false, true));
        return true;
    }

    @Override
    public boolean deleteBySlug(
            UUID workspaceId,
            String slug,
            UUID actorId,
            AuthoringOrigin origin
    ) {
        Optional<Document> found = documentRepository.findByWorkspaceIdAndSlug(workspaceId, slug);
        if (found.isEmpty()) {
            return false;
        }

        Document document = found.get();
        // Recorded before the delete, while there is still a title to record.
        auditTrail.record(actorId, AuditEntry
                .of(AuditAction.DOCUMENT_DELETED, AuditTargetType.DOCUMENT)
                .target(document.getId(), document.getTitle())
                .inWorkspace(workspaceId)
                .with("origin", origin)
                .with("slug", slug));

        documentRepository.delete(document);
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> slugsIn(UUID workspaceId) {
        return documentRepository.findSlugsByWorkspaceId(workspaceId);
    }

    /**
     * A synced revision says SYNCED whether it created the page or changed it —
     * being revision 1 already conveys that it was a creation, whereas where it
     * came from is not recoverable from anything else.
     */
    private static RevisionReason createReason(AuthoringOrigin origin) {
        return origin == AuthoringOrigin.SYNC ? RevisionReason.SYNCED : RevisionReason.CREATED;
    }

    private static RevisionReason updateReason(AuthoringOrigin origin) {
        return origin == AuthoringOrigin.SYNC ? RevisionReason.SYNCED : RevisionReason.UPDATED;
    }

    private void record(
            UUID actorId,
            AuditAction action,
            Document document,
            UUID workspaceId,
            AuthoringOrigin origin,
            String previousTitle
    ) {
        auditTrail.record(actorId, AuditEntry
                .of(action, AuditTargetType.DOCUMENT)
                .target(document.getId(), document.getTitle())
                .inWorkspace(workspaceId)
                .with("origin", origin)
                .with("slug", document.getSlug())
                .changed("title", previousTitle, document.getTitle()));
    }
}
