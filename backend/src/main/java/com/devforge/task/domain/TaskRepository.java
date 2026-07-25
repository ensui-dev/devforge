package com.devforge.task.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {

    /** Every task on a board, ordered for grouping into columns. */
    List<Task> findByBoardIdOrderByColumnIdAscPositionAsc(UUID boardId);

    List<Task> findByColumnIdOrderByPositionAsc(UUID columnId);

    Optional<Task> findByIdAndBoardId(UUID id, UUID boardId);

    int countByColumnId(UUID columnId);

    /**
     * Task counts per board, for board listings.
     *
     * @return rows of {@code [boardId, count]}
     */
    @Query("SELECT t.boardId, COUNT(t) FROM Task t WHERE t.boardId IN :boardIds GROUP BY t.boardId")
    List<Object[]> countByBoardIds(@Param("boardIds") Collection<UUID> boardIds);
}
