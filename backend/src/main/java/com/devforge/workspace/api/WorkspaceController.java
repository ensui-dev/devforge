package com.devforge.workspace.api;

import com.devforge.shared.security.CurrentUser;
import com.devforge.workspace.application.CreateWorkspaceRequest;
import com.devforge.workspace.application.UpdateWorkspaceRequest;
import com.devforge.workspace.application.WorkspaceResponse;
import com.devforge.workspace.application.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces")
@Tag(name = "Workspaces")
public class WorkspaceController {

    private final WorkspaceService workspaceService;

    public WorkspaceController(WorkspaceService workspaceService) {
        this.workspaceService = workspaceService;
    }

    @GetMapping
    @Operation(summary = "List the workspaces the caller belongs to")
    public List<WorkspaceResponse> list(@CurrentUser UUID userId) {
        return workspaceService.findAllForUser(userId);
    }

    @GetMapping("/{workspaceId}")
    @Operation(summary = "Get one workspace")
    public WorkspaceResponse get(@PathVariable UUID workspaceId, @CurrentUser UUID userId) {
        return workspaceService.findById(workspaceId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a workspace, enrolling the caller as its owner")
    public WorkspaceResponse create(
            @Valid @RequestBody CreateWorkspaceRequest request,
            @CurrentUser UUID userId
    ) {
        return workspaceService.create(request, userId);
    }

    @PutMapping("/{workspaceId}")
    @Operation(summary = "Rename or re-slug a workspace (ADMIN)")
    public WorkspaceResponse update(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody UpdateWorkspaceRequest request,
            @CurrentUser UUID userId
    ) {
        return workspaceService.update(workspaceId, request, userId);
    }

    @DeleteMapping("/{workspaceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a workspace and everything in it (OWNER)")
    public void delete(@PathVariable UUID workspaceId, @CurrentUser UUID userId) {
        workspaceService.delete(workspaceId, userId);
    }
}
