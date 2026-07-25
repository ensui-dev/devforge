package com.devforge.task.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * @param columnId destination column; may be the task's current column for a
 *                 reorder within the lane
 * @param position zero-based target index; values past the end place it last
 */
public record MoveTaskRequest(
        @NotNull UUID columnId,
        @NotNull @Min(0) Integer position
) {
}
