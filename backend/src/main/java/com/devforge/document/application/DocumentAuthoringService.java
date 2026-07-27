package com.devforge.document.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.AuthoringOrigin;
import com.devforge.document.contract.DeclaredReference;
import com.devforge.document.contract.DocumentAuthoring;
import com.devforge.document.contract.DocumentDraft;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentReference;
import com.devforge.document.domain.DocumentReferenceRepository;
import com.devforge.document.domain.DocumentRepository;
import com.devforge.document.domain.RevisionReason;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final DocumentReferenceRepository referenceRepository;
    private final DocumentHistoryService history;
    private final AuditTrail auditTrail;
    private final DocumentChangeAnnouncer announcer;

    public DocumentAuthoringService(
            DocumentRepository documentRepository,
            DocumentReferenceRepository referenceRepository,
            DocumentHistoryService history,
            AuditTrail auditTrail,
            DocumentChangeAnnouncer announcer
    ) {
        this.documentRepository = documentRepository;
        this.referenceRepository = referenceRepository;
        this.history = history;
        this.auditTrail = auditTrail;
        this.announcer = announcer;
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
        announcer.removed(document, origin, actorId);

        documentRepository.delete(document);
        return true;
    }

    @Override
    public ReferenceOutcome replaceReferences(
            UUID workspaceId,
            String sourceSlug,
            List<DeclaredReference> declared,
            UUID actorId,
            AuthoringOrigin origin
    ) {
        Optional<Document> source = documentRepository.findByWorkspaceIdAndSlug(workspaceId, sourceSlug);
        if (source.isEmpty()) {
            return ReferenceOutcome.nothing();
        }
        UUID sourceId = source.get().getId();

        // Resolve every declared target in one query rather than one per link.
        List<String> targetSlugs = declared.stream().map(DeclaredReference::targetSlug).distinct().toList();
        Map<String, UUID> idBySlug = targetSlugs.isEmpty()
                ? Map.of()
                : documentRepository.findByWorkspaceIdAndSlugIn(workspaceId, targetSlugs).stream()
                        .collect(Collectors.toMap(Document::getSlug, Document::getId));

        List<String> unresolved = new ArrayList<>();
        Set<Wanted> wanted = new LinkedHashSet<>();
        for (DeclaredReference reference : declared) {
            UUID targetId = idBySlug.get(reference.targetSlug());
            if (targetId == null) {
                // A typo in a link is exactly what a reader would otherwise find out
                // about much later, so it is reported rather than dropped.
                unresolved.add(reference.targetSlug());
                continue;
            }
            if (targetId.equals(sourceId)) {
                unresolved.add(reference.targetSlug() + " (a document cannot reference itself)");
                continue;
            }
            wanted.add(new Wanted(targetId, reference.referenceType()));
        }

        List<DocumentReference> existing = referenceRepository.findBySourceDocumentId(sourceId);
        Set<Wanted> current = existing.stream()
                .map(reference -> new Wanted(reference.getTargetDocumentId(), reference.getReferenceType()))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int removed = 0;
        for (DocumentReference reference : existing) {
            Wanted asWanted = new Wanted(reference.getTargetDocumentId(), reference.getReferenceType());
            if (!wanted.contains(asWanted)) {
                referenceRepository.delete(reference);
                removed++;
            }
        }

        int added = 0;
        for (Wanted link : wanted) {
            if (!current.contains(link)) {
                referenceRepository.save(
                        new DocumentReference(sourceId, link.targetId(), link.referenceType()));
                added++;
            }
        }

        if (added > 0 || removed > 0) {
            auditTrail.record(actorId, AuditEntry
                    .of(AuditAction.DOCUMENT_LINKED, AuditTargetType.DOCUMENT)
                    .target(sourceId, source.get().getTitle())
                    .inWorkspace(workspaceId)
                    .with("origin", origin)
                    .with("added", added)
                    .with("removed", removed));
            // The page's links are part of what it says, so changing them changes
            // the document even though no revision was written — a file holding this
            // page declares its links in front matter.
            announcer.written(source.get(), origin, null, actorId);
        }

        return new ReferenceOutcome(added, removed, List.copyOf(unresolved));
    }

    /** A link as a set member, so reconciling is a set difference rather than a scan. */
    private record Wanted(UUID targetId, com.devforge.document.contract.ReferenceType referenceType) {
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
