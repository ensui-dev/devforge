package com.devforge.task.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** @param position zero-based target index; values past the end move the column last */
public record MoveColumnRequest(@NotNull @Min(0) Integer position) {
}
