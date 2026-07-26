package com.devforge.document.domain;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DocumentContentRepository extends JpaRepository<DocumentContent, UUID> {

    Optional<DocumentContent> findByDocumentIdAndContentHash(UUID documentId, String contentHash);

    boolean existsByDocumentIdAndContentHash(UUID documentId, String contentHash);

    /** How many distinct bodies this document's history holds, for tests and diagnostics. */
    long countByDocumentId(UUID documentId);
}
