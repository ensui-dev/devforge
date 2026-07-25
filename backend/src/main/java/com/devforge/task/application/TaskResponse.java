package com.devforge.task.application;

import com.devforge.task.domain.Task;
import com.devforge.task.domain.TaskPriority;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        UUID boardId,
        UUID columnId,
        String title,
        String description,
        int position,
        TaskPriority priority,
        TaskAssigneeResponse assignee,
        List<LinkedDocumentResponse> linkedDocuments,
        Instant createdAt,
        Instant updatedAt
) {

    public static TaskResponse of(
            Task task,
            TaskAssigneeResponse assignee,
            List<LinkedDocumentResponse> linkedDocuments
    ) {
        return new TaskResponse(
                task.getId(),
                task.getBoardId(),
                task.getColumnId(),
                task.getTitle(),
                task.getDescription(),
                task.getPosition(),
                task.getPriority(),
                assignee,
                linkedDocuments,
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
