package com.devforge.document.domain;

import com.devforge.document.contract.ReferenceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentReferenceRepository extends JpaRepository<DocumentReference, UUID> {

    /** Every edge touching the document, in either direction. */
    @Query("""
            SELECT r FROM DocumentReference r
            WHERE r.sourceDocumentId = :documentId OR r.targetDocumentId = :documentId
            ORDER BY r.createdAt ASC
            """)
    List<DocumentReference> findAllTouching(@Param("documentId") UUID documentId);

    List<DocumentReference> findBySourceDocumentId(UUID sourceDocumentId);

    boolean existsBySourceDocumentIdAndTargetDocumentIdAndReferenceType(
            UUID sourceDocumentId,
            UUID targetDocumentId,
            ReferenceType referenceType
    );

    Optional<DocumentReference> findByIdAndSourceDocumentId(UUID id, UUID sourceDocumentId);
}
