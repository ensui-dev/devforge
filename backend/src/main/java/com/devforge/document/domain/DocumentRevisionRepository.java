package com.devforge.document.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    /**
     * The revision that was current at a moment in the past.
     *
     * <p>What a document said when something else last looked at it, which is the
     * "before" half of showing what has changed since.
     */
    Optional<DocumentRevision> findFirstByDocumentIdAndCreatedAtLessThanEqualOrderByRevisionDesc(
            UUID documentId, Instant at);

    /**
     * When each of these documents last changed, in one query.
     *
     * <p>A page's connections panel asks about every neighbour at once; asking per
     * neighbour would be a query per edge on a screen that already renders a graph.
     */
    @Query("""
            SELECT new com.devforge.document.domain.LastChange(r.documentId, MAX(r.createdAt))
            FROM DocumentRevision r
            WHERE r.documentId IN :documentIds
            GROUP BY r.documentId
            """)
    List<LastChange> lastChangedAt(@Param("documentIds") Collection<UUID> documentIds);
}
