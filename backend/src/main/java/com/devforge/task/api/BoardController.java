package com.devforge.task.api;

import com.devforge.shared.security.CurrentUser;
import com.devforge.task.application.BoardResponse;
import com.devforge.task.application.BoardService;
import com.devforge.task.application.BoardSummaryResponse;
import com.devforge.task.application.CreateBoardRequest;
import com.devforge.task.application.CreateColumnRequest;
import com.devforge.task.application.MoveColumnRequest;
import com.devforge.task.application.UpdateBoardRequest;
import com.devforge.task.application.UpdateColumnRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/workspaces/{workspaceId}/boards")
@Tag(name = "Boards")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    @GetMapping
    @Operation(summary = "List the workspace's boards with column and task counts")
    public List<BoardSummaryResponse> list(@PathVariable UUID workspaceId, @CurrentUser UUID userId) {
        return boardService.findByWorkspace(workspaceId, userId);
    }

    @GetMapping("/{boardId}")
    @Operation(summary = "Get a board with its columns and tasks")
    public BoardResponse get(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @CurrentUser UUID userId
    ) {
        return boardService.findById(workspaceId, boardId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a board seeded with the default columns (MEMBER)")
    public BoardResponse create(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody CreateBoardRequest request,
            @CurrentUser UUID userId
    ) {
        return boardService.create(workspaceId, request, userId);
    }

    @PutMapping("/{boardId}")
    @Operation(summary = "Rename a board (MEMBER)")
    public BoardResponse rename(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @Valid @RequestBody UpdateBoardRequest request,
            @CurrentUser UUID userId
    ) {
        return boardService.rename(workspaceId, boardId, request, userId);
    }

    @DeleteMapping("/{boardId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a board and all its tasks (ADMIN)")
    public void delete(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @CurrentUser UUID userId
    ) {
        boardService.delete(workspaceId, boardId, userId);
    }

    @PostMapping("/{boardId}/columns")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Append a column (MEMBER)")
    public BoardResponse addColumn(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @Valid @RequestBody CreateColumnRequest request,
            @CurrentUser UUID userId
    ) {
        return boardService.addColumn(workspaceId, boardId, request, userId);
    }

    @PutMapping("/{boardId}/columns/{columnId}")
    @Operation(summary = "Rename a column or change its WIP limit (MEMBER)")
    public BoardResponse updateColumn(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @PathVariable UUID columnId,
            @Valid @RequestBody UpdateColumnRequest request,
            @CurrentUser UUID userId
    ) {
        return boardService.updateColumn(workspaceId, boardId, columnId, request, userId);
    }

    @PatchMapping("/{boardId}/columns/{columnId}/position")
    @Operation(summary = "Reorder a column, renumbering the rest (MEMBER)")
    public BoardResponse moveColumn(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @PathVariable UUID columnId,
            @Valid @RequestBody MoveColumnRequest request,
            @CurrentUser UUID userId
    ) {
        return boardService.moveColumn(workspaceId, boardId, columnId, request, userId);
    }

    @DeleteMapping("/{boardId}/columns/{columnId}")
    @Operation(summary = "Delete a column and the tasks in it (MEMBER)")
    public BoardResponse deleteColumn(
            @PathVariable UUID workspaceId,
            @PathVariable UUID boardId,
            @PathVariable UUID columnId,
            @CurrentUser UUID userId
    ) {
        return boardService.deleteColumn(workspaceId, boardId, columnId, userId);
    }
}
