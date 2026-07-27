package com.devforge.document.domain;

import com.devforge.document.contract.DocumentType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {

    Page<Document> findByWorkspaceIdOrderByTitleAsc(UUID workspaceId, Pageable pageable);

    Page<Document> findByWorkspaceIdAndDocumentTypeOrderByTitleAsc(
            UUID workspaceId,
            DocumentType documentType,
            Pageable pageable
    );

    Optional<Document> findByWorkspaceIdAndSlug(UUID workspaceId, String slug);

    /**
     * Slugs only.
     *
     * <p>Used to work out what an external source no longer contains. Loading whole
     * documents to compare names would pull every body in the workspace into memory
     * to answer a question about strings.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT d.slug FROM Document d WHERE d.workspaceId = :workspaceId")
    List<String> findSlugsByWorkspaceId(UUID workspaceId);

    Optional<Document> findByIdAndWorkspaceId(UUID id, UUID workspaceId);

    boolean existsByWorkspaceIdAndSlug(UUID workspaceId, String slug);

    List<Document> findByWorkspaceIdAndIdIn(UUID workspaceId, Collection<UUID> ids);

    /** Unpaged listing, used only for a published workspace's navigation. */
    List<Document> findAllByWorkspaceIdOrderByTitleAsc(UUID workspaceId);

    /*
     * The public reads. Every one filters on `internal = false` in the query rather
     * than in Java, so a page held back cannot be exposed by a caller forgetting to
     * filter a result set.
     */

    List<Document> findAllByWorkspaceIdAndInternalFalseOrderByTitleAsc(UUID workspaceId);

    Optional<Document> findByWorkspaceIdAndSlugAndInternalFalse(UUID workspaceId, String slug);

    List<Document> findByWorkspaceIdAndInternalFalseAndIdIn(UUID workspaceId, Collection<UUID> ids);

    int countByWorkspaceIdAndInternalFalse(UUID workspaceId);

    int countByWorkspaceIdAndInternalTrue(UUID workspaceId);

    /**
     * Ranked full-text search over title and body.
     *
     * <p>Uses the stored {@code search_vector} column and its GIN index, so cost
     * grows with the number of <em>matches</em> rather than the number of
     * documents. {@code websearch_to_tsquery} accepts what users actually type
     * ({@code "quoted phrase" -excluded or alternative}) without throwing on
     * malformed input, which a raw {@code to_tsquery} would.
     *
     * <p>Columns are listed explicitly rather than {@code SELECT *} so the
     * unmapped {@code tsvector} column is never returned.
     */
    @Query(
            // Every mapped column must appear here, or Hibernate cannot build the
            // entity. `SELECT *` is not an option because the generated tsvector
            // column is deliberately unmapped — so adding a field to Document means
            // adding it to this list too.
            value = """
                    SELECT d.id, d.workspace_id, d.title, d.slug, d.content, d.document_type,
                           d.internal, d.created_at, d.updated_at, d.version
                    FROM documents d
                    WHERE d.workspace_id = :workspaceId
                      AND d.search_vector @@ websearch_to_tsquery('english', :query)
                    ORDER BY ts_rank(d.search_vector, websearch_to_tsquery('english', :query)) DESC,
                             d.title ASC
                    """,
            countQuery = """
                    SELECT count(*)
                    FROM documents d
                    WHERE d.workspace_id = :workspaceId
                      AND d.search_vector @@ websearch_to_tsquery('english', :query)
                    """,
            nativeQuery = true
    )
    Page<Document> search(
            @Param("workspaceId") UUID workspaceId,
            @Param("query") String query,
            Pageable pageable
    );
}
