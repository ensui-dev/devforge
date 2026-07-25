package com.devforge.task.application;

import com.devforge.task.domain.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateTaskRequest(
        @NotBlank @Size(max = 500) String title,
        @Size(max = 10000) String description,
        @NotNull UUID columnId,
        TaskPriority priority,
        /** Must be a member of this workspace, or {@code null} for unassigned. */
        UUID assigneeId,
        /** Documents to cite from this task; each must be in the same workspace. */
        List<UUID> linkedDocumentIds
) {

    /** Normalises in the constructor so the accessor and {@code equals} agree. */
    public CreateTaskRequest {
        linkedDocumentIds = linkedDocumentIds == null ? List.of() : List.copyOf(linkedDocumentIds);
    }
}
