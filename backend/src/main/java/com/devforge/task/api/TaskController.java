package com.devforge.task.api;

import com.devforge.shared.security.CurrentUser;
import com.devforge.task.application.CreateTaskRequest;
import com.devforge.task.application.LinkDocumentRequest;
import com.devforge.task.application.MoveTaskRequest;
import com.devforge.task.application.TaskResponse;
import com.devforge.task.application.TaskService;
import com.devforge.task.application.UpdateTaskRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/boards/{boardId}/tasks")
@Tag(name = "Tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task at the end of a column (MEMBER)")
    public TaskResponse create(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @Valid @RequestBody CreateTaskRequest request,
            @CurrentUser UUID userId
    ) {
        return taskService.create(workspaceId, boardId, request, userId);
    }

    @PutMapping("/{taskId}")
    @Operation(summary = "Edit a task's title, description, priority, or assignee (MEMBER)")
    public TaskResponse update(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @PathVariable UUID taskId,
            @Valid @RequestBody UpdateTaskRequest request,
            @CurrentUser UUID userId
    ) {
        return taskService.update(workspaceId, boardId, taskId, request, userId);
    }

    @PatchMapping("/{taskId}/position")
    @Operation(summary = "Move a task within or between columns (MEMBER)")
    public TaskResponse move(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @PathVariable UUID taskId,
            @Valid @RequestBody MoveTaskRequest request,
            @CurrentUser UUID userId
    ) {
        return taskService.move(workspaceId, boardId, taskId, request, userId);
    }

    @DeleteMapping("/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task, closing the gap in its column (MEMBER)")
    public void delete(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @PathVariable UUID taskId,
            @CurrentUser UUID userId
    ) {
        taskService.delete(workspaceId, boardId, taskId, userId);
    }

    @PostMapping("/{taskId}/documents")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cite a document from this task (MEMBER)")
    public TaskResponse linkDocument(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @PathVariable UUID taskId,
            @Valid @RequestBody LinkDocumentRequest request,
            @CurrentUser UUID userId
    ) {
        return taskService.linkDocument(workspaceId, boardId, taskId, request, userId);
    }

    @DeleteMapping("/{taskId}/documents/{documentId}")
    @Operation(summary = "Remove a document citation from this task (MEMBER)")
    public TaskResponse unlinkDocument(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @PathVariable UUID taskId,
            @PathVariable UUID documentId,
            @CurrentUser UUID userId
    ) {
        return taskService.unlinkDocument(workspaceId, boardId, taskId, documentId, userId);
    }
}
