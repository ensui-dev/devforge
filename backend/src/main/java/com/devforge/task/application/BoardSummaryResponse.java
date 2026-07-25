package com.devforge.task.application;

import com.devforge.task.domain.Board;

import java.time.Instant;
import java.util.UUID;

/**
 * Board listing projection.
 *
 * <p>Carries counts instead of nested columns and tasks, so listing boards costs
 * a bounded amount of data no matter how much work they hold.
 */
public record BoardSummaryResponse(
        UUID id,
        UUID workspaceId,
        String name,
        int columnCount,
        long taskCount,
        Instant createdAt,
        Instant updatedAt
) {

    public static BoardSummaryResponse of(Board board, long taskCount) {
        return new BoardSummaryResponse(
                board.getId(),
                board.getWorkspaceId(),
                board.getName(),
                board.getColumns().size(),
                taskCount,
                board.getCreatedAt(),
                board.getUpdatedAt()
        );
    }
}
