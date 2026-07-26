package com.devforge.document.application;

import com.devforge.document.contract.DocumentRef;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentReference;
import com.devforge.document.domain.DocumentReferenceRepository;
import com.devforge.document.domain.DocumentRepository;
import com.devforge.instance.contract.InstancePolicy;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.workspace.contract.WorkspaceLookup;
import com.devforge.workspace.contract.WorkspaceLookup.PublishedWorkspace;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only view of workspaces that have published their documentation.
 *
 * <p>This is the only path in the system that returns workspace content without an
 * authenticated caller, so containment is structural rather than procedural:
 *
 * <ul>
 *   <li>{@link WorkspaceLookup#findPublished} cannot return an unpublished
 *       workspace, so a private one is not filtered out later — it is never
 *       loaded.</li>
 *   <li>Every document read goes through a repository method that filters
 *       {@code internal = false} in SQL, so a page held back cannot be returned by
 *       a caller forgetting to filter.</li>
 *   <li>References are resolved against the same public-only lookup, so a public
 *       page never reveals the title of an internal one it links to.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class PublicDocsService {

    private final DocumentRepository documentRepository;
    private final DocumentReferenceRepository referenceRepository;
    private final WorkspaceLookup workspaceLookup;
    private final InstancePolicy instancePolicy;

    public PublicDocsService(
            DocumentRepository documentRepository,
            DocumentReferenceRepository referenceRepository,
            WorkspaceLookup workspaceLookup,
            InstancePolicy instancePolicy
    ) {
        this.documentRepository = documentRepository;
        this.referenceRepository = referenceRepository;
        this.workspaceLookup = workspaceLookup;
        this.instancePolicy = instancePolicy;
    }

    /** Every published workspace, for the documentation directory. */
    public List<PublishedWorkspaceResponse> directory() {
        if (!instancePolicy.publicDocumentationEnabled()) {
            return List.of();
        }
        return workspaceLookup.findAllPublished().stream()
                .map(workspace -> PublishedWorkspaceResponse.of(
                        workspace,
                        documentRepository.countByWorkspaceIdAndInternalFalse(workspace.id())))
                .filter(entry -> entry.pageCount() > 0)
                .toList();
    }

    /** Everything one owner has published, for their namespace page. */
    public List<PublishedWorkspaceResponse> byOwner(String handle) {
        if (!instancePolicy.publicDocumentationEnabled()) {
            return List.of();
        }
        return workspaceLookup.findPublishedByOwner(handle).stream()
                .map(workspace -> PublishedWorkspaceResponse.of(
                        workspace,
                        documentRepository.countByWorkspaceIdAndInternalFalse(workspace.id())))
                .filter(entry -> entry.pageCount() > 0)
                .toList();
    }

    /**
     * Resolves a bare slug to its canonical namespaced path.
     *
     * <p>Public addresses used to be {@code /docs/{slug}}. Those links would
     * otherwise rot, so this resolves one — but only when a single published
     * workspace matches, since the slug is no longer unique on its own.
     *
     * @return the canonical path, or empty when nothing or too much matches
     */
    public java.util.Optional<String> canonicalPathForSlug(String slug) {
        if (!instancePolicy.publicDocumentationEnabled()) {
            return java.util.Optional.empty();
        }
        List<PublishedWorkspace> matches = workspaceLookup.findPublishedBySlug(slug);
        return matches.size() == 1
                ? java.util.Optional.of(matches.getFirst().publicPath())
                : java.util.Optional.empty();
    }

    /** One published workspace's table of contents. */
    public PublicHandbookResponse tableOfContents(String handle, String workspaceSlug) {
        PublishedWorkspace workspace = requirePublished(handle, workspaceSlug);
        return PublicHandbookResponse.of(
                workspace,
                documentRepository.findAllByWorkspaceIdAndInternalFalseOrderByTitleAsc(workspace.id()));
    }

    public PublicDocumentResponse findDocument(
            String handle,
            String workspaceSlug,
            String documentSlug
    ) {
        PublishedWorkspace workspace = requirePublished(handle, workspaceSlug);

        Document document = documentRepository
                .findByWorkspaceIdAndSlugAndInternalFalse(workspace.id(), documentSlug)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentSlug));

        List<DocumentReference> references = referenceRepository.findAllTouching(document.getId());

        // Resolved through the public-only lookup, so an edge pointing at an
        // internal page resolves to nothing and is dropped below.
        Map<UUID, DocumentRef> related = documentRepository
                .findByWorkspaceIdAndInternalFalseAndIdIn(
                        workspace.id(),
                        references.stream().map(r -> r.otherEnd(document.getId())).distinct().toList())
                .stream()
                .map(DocumentDirectoryService::toRef)
                .collect(Collectors.toMap(DocumentRef::id, ref -> ref));

        List<DocumentReferenceResponse> edges = references.stream()
                .filter(reference -> related.containsKey(reference.otherEnd(document.getId())))
                .map(reference -> DocumentReferenceResponse.of(
                        reference, document.getId(), related.get(reference.otherEnd(document.getId()))))
                .toList();

        return PublicDocumentResponse.of(document, edges);
    }

    private PublishedWorkspace requirePublished(String handle, String slug) {
        if (!instancePolicy.publicDocumentationEnabled()) {
            // Indistinguishable from "no such documentation", so switching the
            // feature off does not advertise what used to be there.
            throw new ResourceNotFoundException("Published workspace", handle + "/" + slug);
        }
        return workspaceLookup.findPublished(handle, slug)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Published workspace", handle + "/" + slug));
    }
}
