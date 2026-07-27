package com.devforge.document.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.AuthoringOrigin;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentContent;
import com.devforge.document.domain.DocumentContentRepository;
import com.devforge.document.domain.DocumentRepository;
import com.devforge.document.domain.DocumentRevision;
import com.devforge.document.domain.DocumentRevisionRepository;
import com.devforge.document.domain.RevisionReason;
import com.devforge.identity.contract.UserDirectory;
import com.devforge.shared.application.PageResponse;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRole;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * A document's history: what it said, when, and who changed it.
 *
 * <p>The rule that shapes everything here is that history is append-only.
 * Restoring revision 3 does not delete revisions 4 and 5 — it writes revision 6
 * with revision 3's content and records where it came from. You can always see
 * that the restore happened, and undo it the same way.
 *
 * <p>{@link #snapshot} is called by {@link DocumentService} on every write. It
 * lives here rather than there so the rule "every change leaves a revision" has
 * one implementation instead of three call sites that could each forget.
 */
@Service
@Transactional(readOnly = true)
public class DocumentHistoryService {

    private final DocumentRepository documentRepository;
    private final DocumentRevisionRepository revisionRepository;
    private final DocumentContentRepository contentRepository;
    private final WorkspaceAccess workspaceAccess;
    private final UserDirectory userDirectory;
    private final AuditTrail auditTrail;
    private final DocumentChangeAnnouncer announcer;

    public DocumentHistoryService(
            DocumentRepository documentRepository,
            DocumentRevisionRepository revisionRepository,
            DocumentContentRepository contentRepository,
            WorkspaceAccess workspaceAccess,
            UserDirectory userDirectory,
            AuditTrail auditTrail,
            DocumentChangeAnnouncer announcer
    ) {
        this.documentRepository = documentRepository;
        this.revisionRepository = revisionRepository;
        this.contentRepository = contentRepository;
        this.workspaceAccess = workspaceAccess;
        this.userDirectory = userDirectory;
        this.auditTrail = auditTrail;
        this.announcer = announcer;
    }

    /**
     * Appends a revision recording the document's current state.
     *
     * <p>Called after the change has been applied, so the snapshot is what the
     * document now says — revision 1 is what it was created as, and the newest
     * revision always matches the live document.
     */
    @Transactional
    public void snapshot(Document document, RevisionReason reason, Integer restoredFrom, UUID authorId) {
        // Stored once per distinct body. A restore, and a hand-made revert, both
        // produce content that already exists — this is where that is noticed.
        if (!contentRepository.existsByDocumentIdAndContentHash(
                document.getId(), DocumentContent.hash(document.getContent()))) {
            contentRepository.save(new DocumentContent(document.getId(), document.getContent()));
        }

        Optional<DocumentRevision> latest =
                revisionRepository.findFirstByDocumentIdOrderByRevisionDesc(document.getId());
        int next = latest.map(revision -> revision.getRevision() + 1).orElse(1);

        revisionRepository.save(new DocumentRevision(
                document.getId(), next, document, reason, restoredFrom, authorId, labelFor(authorId)));

        // Every change to a document passes through here, which is what makes this
        // the one place that can announce one. The previous revision is already
        // loaded, and it holds the only record of what the slug used to be — a
        // listener mirroring documents onto paths needs that to tell a rename from
        // a new page.
        announcer.written(document, originOf(reason), latest.map(DocumentRevision::getSlug).orElse(null), authorId);
    }

    /**
     * A synced revision is the only kind that did not come from a person here.
     *
     * <p>Derived rather than passed in, because the two are the same fact: a
     * revision says SYNCED exactly when an external source of truth wrote it.
     * Threading a second argument through every caller would let them disagree.
     */
    private static AuthoringOrigin originOf(RevisionReason reason) {
        return reason == RevisionReason.SYNCED ? AuthoringOrigin.SYNC : AuthoringOrigin.DIRECT;
    }

    /**
     * Whether the document differs from its newest revision.
     *
     * <p>Git refuses an empty commit, and for the same reason: saving a document
     * without changing it should not add a revision that says nothing. Hitting save
     * twice would otherwise fill history with identical entries.
     */
    public boolean differsFromLatest(Document document) {
        return revisionRepository.findFirstByDocumentIdOrderByRevisionDesc(document.getId())
                .map(latest -> !latest.getTitle().equals(document.getTitle())
                        || !latest.getSlug().equals(document.getSlug())
                        || latest.getDocumentType() != document.getDocumentType()
                        || latest.isInternal() != document.isInternal()
                        || !latest.getContentHash().equals(DocumentContent.hash(document.getContent())))
                .orElse(true);
    }

    public PageResponse<DocumentRevisionResponse> history(
            UUID workspaceId,
            UUID documentId,
            UUID userId,
            Pageable pageable
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
        requireDocument(workspaceId, documentId);

        return PageResponse.of(
                revisionRepository.findByDocumentIdOrderByRevisionDesc(documentId, pageable),
                DocumentRevisionResponse::summary);
    }

    /** One revision in full, for viewing or diffing against the live document. */
    public DocumentRevisionResponse revision(
            UUID workspaceId,
            UUID documentId,
            int revision,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
        requireDocument(workspaceId, documentId);

        DocumentRevision found = revisionRepository.findByDocumentIdAndRevision(documentId, revision)
                .orElseThrow(() -> new ResourceNotFoundException("Revision", revision));

        return DocumentRevisionResponse.withBody(found, bodyOf(found));
    }

    /**
     * Puts an earlier revision's content back, as a new revision.
     *
     * @return the document as it now stands
     */
    @Transactional
    public DocumentResponse restore(
            UUID workspaceId,
            UUID documentId,
            int revision,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        Document document = requireDocument(workspaceId, documentId);

        DocumentRevision target = revisionRepository
                .findByDocumentIdAndRevision(documentId, revision)
                .orElseThrow(() -> new ResourceNotFoundException("Revision", revision));

        // The slug is restored too, so a restored page is genuinely what it was.
        // If something else has since taken that slug, keep the current one rather
        // than failing the restore — the content is what the user asked for.
        String slug = document.getSlug().equals(target.getSlug())
                || !documentRepository.existsByWorkspaceIdAndSlug(workspaceId, target.getSlug())
                ? target.getSlug()
                : document.getSlug();

        document.revise(
                target.getTitle(),
                slug,
                bodyOf(target),
                target.getDocumentType(),
                target.isInternal());

        snapshot(document, RevisionReason.RESTORED, revision, userId);

        auditTrail.record(userId, AuditEntry
                .of(AuditAction.DOCUMENT_RESTORED, AuditTargetType.DOCUMENT)
                .target(document.getId(), document.getTitle())
                .inWorkspace(workspaceId)
                .with("restoredFrom", revision));

        return DocumentResponse.from(document);
    }

    /**
     * The body a revision points at.
     *
     * <p>Package-private rather than private: showing what a linked page changed
     * needs the same lookup, and duplicating it there would be a second place for
     * the composite key to be got wrong.
     *
     * <p>The composite foreign key guarantees it exists, so an absent row means the
     * schema has been tampered with rather than that the caller asked for something
     * reasonable.
     */
    String bodyOf(DocumentRevision revision) {
        return contentRepository
                .findByDocumentIdAndContentHash(revision.getDocumentId(), revision.getContentHash())
                .map(DocumentContent::getBody)
                .orElseThrow(() -> new IllegalStateException(
                        "Revision %d of document %s references missing content %s"
                                .formatted(revision.getRevision(), revision.getDocumentId(),
                                        revision.getContentHash())));
    }

    private Document requireDocument(UUID workspaceId, UUID documentId) {
        return documentRepository.findByIdAndWorkspaceId(documentId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }

    private String labelFor(UUID authorId) {
        if (authorId == null) {
            return null;
        }
        return userDirectory.findById(authorId)
                .map(user -> "%s <%s>".formatted(user.displayName(), user.email()))
                .orElse(null);
    }
}
