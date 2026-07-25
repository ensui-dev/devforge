package com.devforge.task.application;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateColumnRequest(
        @NotBlank @Size(max = 100) String name,
        @Min(1) Integer wipLimit
) {
}
