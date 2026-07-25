package com.devforge.task.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BoardRepository extends JpaRepository<Board, UUID> {

    List<Board> findByWorkspaceIdOrderByNameAsc(UUID workspaceId);

    /**
     * Loads a board with its columns in one query.
     *
     * <p>An entity graph over the single {@code columns} collection is safe;
     * fetching a second, nested collection in the same query is what previously
     * failed with {@code MultipleBagFetchException}. Tasks are loaded separately
     * by {@code BoardAssembler}.
     */
    @EntityGraph(attributePaths = "columns")
    Optional<Board> findWithColumnsByIdAndWorkspaceId(UUID id, UUID workspaceId);

    Optional<Board> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
