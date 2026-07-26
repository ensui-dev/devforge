package com.devforge.document.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentRevisionRepository extends JpaRepository<DocumentRevision, UUID> {

    Page<DocumentRevision> findByDocumentIdOrderByRevisionDesc(UUID documentId, Pageable pageable);

    Optional<DocumentRevision> findByDocumentIdAndRevision(UUID documentId, int revision);

    /**
     * The highest revision number so far.
     *
     * <p>Derived from the rows rather than kept as a counter on the document: a
     * counter would be a second thing to keep in step, and the unique constraint
     * on {@code (document_id, revision)} already makes a stale one fail loudly.
     */
    Optional<DocumentRevision> findFirstByDocumentIdOrderByRevisionDesc(UUID documentId);

    long countByDocumentId(UUID documentId);
}
