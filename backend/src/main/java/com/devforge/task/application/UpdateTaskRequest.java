package com.devforge.task.application;

import com.devforge.task.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Edits a task's own fields. Column and position are changed through the move
 * endpoint instead, so an ordinary edit can never silently reorder the board.
 */
public record UpdateTaskRequest(
        @NotBlank @Size(max = 500) String title,
        @Size(max = 10000) String description,
        TaskPriority priority,
        UUID assigneeId
) {
}
