package com.devforge.task.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateBoardRequest(@NotBlank @Size(max = 255) String name) {
}
