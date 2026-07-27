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

    /** Resolves many slugs at once, so importing a page's links is one query. */
    List<Document> findByWorkspaceIdAndSlugIn(UUID workspaceId, java.util.Collection<String> slugs);

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
     * Ranked search over title and body.
     *
     * <p>Four ways to match, because one is not enough for a search box.
     *
     * <ul>
     *   <li>The stemmed {@code search_vector}, which is what lets "authenticate"
     *       find "authentication".</li>
     *   <li>The unstemmed {@code search_simple}, matched as a prefix, so a word
     *       finds its page before it has finished being typed. Prefix matching
     *       against the stemmed vector would not do it: "deployment" is stored as
     *       {@code deploy}, which does not start with {@code deploym}.</li>
     *   <li>A substring match on the title, which is the only one of the three that
     *       can find a fragment inside a word — full text is word-based, so "lag"
     *       will not match "consumer-lag" as a token however it is stemmed.</li>
     *   <li>Trigram similarity on the title, which is what tolerates a typo:
     *       "authentcation" still finds the authentication page.</li>
     * </ul>
     *
     * <p>Ranked in that order of confidence. A full-text hit outranks a substring
     * hit, which outranks something that merely looks similar, and titles beat
     * bodies because the vector weights them so.
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
                      AND (d.search_vector @@ to_tsquery('english', :tsQuery)
                           OR d.search_simple @@ to_tsquery('simple', :tsQuery)
                           OR d.title ILIKE :likePattern
                           OR similarity(d.title, :typed) > 0.25)
                    ORDER BY ts_rank(d.search_vector, to_tsquery('english', :tsQuery)) DESC,
                             ts_rank(d.search_simple, to_tsquery('simple', :tsQuery)) DESC,
                             (d.title ILIKE :likePattern) DESC,
                             similarity(d.title, :typed) DESC,
                             d.title ASC
                    """,
            countQuery = """
                    SELECT count(*)
                    FROM documents d
                    WHERE d.workspace_id = :workspaceId
                      AND (d.search_vector @@ to_tsquery('english', :tsQuery)
                           OR d.search_simple @@ to_tsquery('simple', :tsQuery)
                           OR d.title ILIKE :likePattern
                           OR similarity(d.title, :typed) > 0.25)
                    """,
            nativeQuery = true
    )
    Page<Document> search(
            @Param("workspaceId") UUID workspaceId,
            @Param("tsQuery") String tsQuery,
            @Param("likePattern") String likePattern,
            @Param("typed") String typed,
            Pageable pageable
    );
}
