package com.devforge.task.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateColumnRequest(
        @NotBlank @Size(max = 100) String name,
        /** Optional work-in-progress cap; omit for unlimited. */
        @Min(1) Integer wipLimit
) {
}
