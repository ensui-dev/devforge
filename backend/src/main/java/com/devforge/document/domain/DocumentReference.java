package com.devforge.document.domain;

import com.devforge.document.contract.ReferenceType;
import com.devforge.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * A typed, directed edge between two documents in the same workspace.
 *
 * <p>Endpoints are held as ids rather than associations. Both documents live in
 * this module, so an association would be legal, but ids keep reference queries
 * from dragging two full document bodies into memory to answer "what links here".
 */
@Entity
@Table(
        name = "document_references",
        uniqueConstraints = @UniqueConstraint(columnNames = {
                "source_document_id", "target_document_id", "reference_type"
        })
)
public class DocumentReference extends BaseEntity {

    @Column(name = "source_document_id", nullable = false, updatable = false)
    private UUID sourceDocumentId;

    @Column(name = "target_document_id", nullable = false, updatable = false)
    private UUID targetDocumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reference_type", nullable = false, length = 50)
    private ReferenceType referenceType;

    protected DocumentReference() {
    }

    public DocumentReference(UUID sourceDocumentId, UUID targetDocumentId, ReferenceType referenceType) {
        this.sourceDocumentId = sourceDocumentId;
        this.targetDocumentId = targetDocumentId;
        this.referenceType = referenceType;
    }

    public UUID getSourceDocumentId() {
        return sourceDocumentId;
    }

    public UUID getTargetDocumentId() {
        return targetDocumentId;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    /** @return the document at the other end of this edge from {@code documentId} */
    public UUID otherEnd(UUID documentId) {
        return sourceDocumentId.equals(documentId) ? targetDocumentId : sourceDocumentId;
    }

    public boolean isOutgoingFrom(UUID documentId) {
        return sourceDocumentId.equals(documentId);
    }

    /**
     * Whether this edge has the document at either end.
     *
     * <p>Checked before answering a question about an edge that arrived as an id:
     * without it, any edge id would read the pair of documents it joins, from any
     * document the caller could open.
     */
    public boolean touches(UUID documentId) {
        return sourceDocumentId.equals(documentId) || targetDocumentId.equals(documentId);
    }
}
