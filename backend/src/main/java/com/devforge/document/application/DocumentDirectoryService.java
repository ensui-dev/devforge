package com.devforge.document.application;

import com.devforge.document.contract.DocumentDirectory;
import com.devforge.document.contract.DocumentRef;
import com.devforge.document.domain.Document;
import com.devforge.document.domain.DocumentRepository;
import com.devforge.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The document module's implementation of its own published contract.
 *
 * <p>Note there is no authorisation here. Callers reach this only after their own
 * {@code WorkspaceAccess} check, and every method is workspace-scoped, so a
 * caller cannot widen its reach by passing a foreign document id.
 */
@Service
@Transactional(readOnly = true)
public class DocumentDirectoryService implements DocumentDirectory {

    private final DocumentRepository documentRepository;

    public DocumentDirectoryService(DocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public Optional<DocumentRef> find(UUID workspaceId, UUID documentId) {
        return documentRepository.findByIdAndWorkspaceId(documentId, workspaceId)
                .map(DocumentDirectoryService::toRef);
    }

    @Override
    public Map<UUID, DocumentRef> findAllByIds(UUID workspaceId, Collection<UUID> documentIds) {
        if (documentIds.isEmpty()) {
            return Map.of();
        }
        return documentRepository.findByWorkspaceIdAndIdIn(workspaceId, documentIds).stream()
                .map(DocumentDirectoryService::toRef)
                .collect(Collectors.toMap(DocumentRef::id, Function.identity()));
    }

    @Override
    public DocumentRef require(UUID workspaceId, UUID documentId) {
        return find(workspaceId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document", documentId));
    }

    @Override
    public VisibilityCounts countByVisibility(UUID workspaceId) {
        return new VisibilityCounts(
                documentRepository.countByWorkspaceIdAndInternalFalse(workspaceId),
                documentRepository.countByWorkspaceIdAndInternalTrue(workspaceId)
        );
    }

    static DocumentRef toRef(Document document) {
        return new DocumentRef(
                document.getId(),
                document.getWorkspaceId(),
                document.getTitle(),
                document.getSlug(),
                document.getDocumentType()
        );
    }
}
