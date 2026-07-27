package com.devforge.document.application;

import com.devforge.document.contract.AuthoringOrigin;
import com.devforge.document.contract.DeclaredReference;
import com.devforge.document.contract.DocumentChanged;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentReference;
import com.devforge.document.domain.DocumentReferenceRepository;
import com.devforge.document.domain.DocumentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Turns a document write into the event other modules hear about it by.
 *
 * <p>Assembling the event is a real job — a listener needs the links as target
 * slugs, which means a second query and a resolution the callers should not each
 * repeat — so it lives here rather than at four call sites.
 *
 * <p>Package-private: announcing is the document module's own business, and nothing
 * outside it should be able to claim a document changed.
 */
@Component
class DocumentChangeAnnouncer {

    private final DocumentRepository documentRepository;
    private final DocumentReferenceRepository referenceRepository;
    private final ApplicationEventPublisher events;

    DocumentChangeAnnouncer(
            DocumentRepository documentRepository,
            DocumentReferenceRepository referenceRepository,
            ApplicationEventPublisher events
    ) {
        this.documentRepository = documentRepository;
        this.referenceRepository = referenceRepository;
        this.events = events;
    }

    /**
     * @param previousSlug the slug this document had before, or {@code null} if new
     */
    void written(Document document, AuthoringOrigin origin, String previousSlug, UUID actorId) {
        events.publishEvent(new DocumentChanged(
                document.getWorkspaceId(),
                document.getId(),
                DocumentChanged.Change.WRITTEN,
                document.getSlug(),
                previousSlug,
                document.getTitle(),
                document.getContent(),
                document.getDocumentType(),
                document.isInternal(),
                outgoingLinks(document),
                actorId,
                origin));
    }

    void removed(Document document, AuthoringOrigin origin, UUID actorId) {
        events.publishEvent(new DocumentChanged(
                document.getWorkspaceId(),
                document.getId(),
                DocumentChanged.Change.REMOVED,
                document.getSlug(),
                null,
                document.getTitle(),
                "",
                document.getDocumentType(),
                document.isInternal(),
                List.of(),
                actorId,
                origin));
    }

    /**
     * This document's outgoing links, named by the slug of what they point at.
     *
     * <p>Slugs rather than ids because that is the vocabulary a document has outside
     * DevForge — the same reason {@link DeclaredReference} uses them coming the other
     * way. Backlinks are excluded: they belong to the pages that declared them, and a
     * listener writing this document out must not claim to own them.
     */
    private List<DeclaredReference> outgoingLinks(Document document) {
        List<DocumentReference> references =
                referenceRepository.findBySourceDocumentId(document.getId());
        if (references.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> slugById = documentRepository.findByWorkspaceIdAndIdIn(
                        document.getWorkspaceId(),
                        references.stream().map(DocumentReference::getTargetDocumentId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(Document::getId, Document::getSlug));

        return references.stream()
                .filter(reference -> slugById.containsKey(reference.getTargetDocumentId()))
                .map(reference -> new DeclaredReference(
                        reference.getReferenceType(),
                        slugById.get(reference.getTargetDocumentId())))
                .toList();
    }
}
