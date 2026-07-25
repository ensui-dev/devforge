package com.devforge.task.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A board with its columns and their tasks — the full kanban view. */
public record BoardResponse(
        UUID id,
        UUID workspaceId,
        String name,
        List<BoardColumnResponse> columns,
        Instant createdAt,
        Instant updatedAt
) {
}
