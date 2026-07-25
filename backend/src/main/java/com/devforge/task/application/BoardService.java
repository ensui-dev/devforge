package com.devforge.task.application;

import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.task.domain.Board;
import com.devforge.task.domain.BoardRepository;
import com.devforge.task.domain.TaskRepository;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Boards and their column structure.
 *
 * <p>Column mutations return the whole board because reordering or removing a
 * column renumbers its siblings — returning just the changed column would leave
 * the client holding stale positions.
 */
@Service
@Transactional(readOnly = true)
public class BoardService {

    private final BoardRepository boardRepository;
    private final TaskRepository taskRepository;
    private final WorkspaceAccess workspaceAccess;
    private final BoardAssembler boardAssembler;

    public BoardService(
            BoardRepository boardRepository,
            TaskRepository taskRepository,
            WorkspaceAccess workspaceAccess,
            BoardAssembler boardAssembler
    ) {
        this.boardRepository = boardRepository;
        this.taskRepository = taskRepository;
        this.workspaceAccess = workspaceAccess;
        this.boardAssembler = boardAssembler;
    }

    public List<BoardSummaryResponse> findByWorkspace(UUID workspaceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);

        List<Board> boards = boardRepository.findByWorkspaceIdOrderByNameAsc(workspaceId);
        if (boards.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> taskCounts = countTasksFor(boards.stream().map(Board::getId).toList());
        return boards.stream()
                .map(board -> BoardSummaryResponse.of(board, taskCounts.getOrDefault(board.getId(), 0L)))
                .toList();
    }

    public BoardResponse findById(UUID workspaceId, UUID boardId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
        return boardAssembler.assembleBoard(loadBoardWithColumns(workspaceId, boardId));
    }

    @Transactional
    public BoardResponse create(UUID workspaceId, CreateBoardRequest request, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        // Columns cascade from the aggregate, so this is a single save.
        Board board = boardRepository.save(Board.withDefaultColumns(workspaceId, request.name()));
        return boardAssembler.assembleBoard(board);
    }

    @Transactional
    public BoardResponse rename(UUID workspaceId, UUID boardId, UpdateBoardRequest request, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        Board board = loadBoardWithColumns(workspaceId, boardId);
        board.rename(request.name());
        return boardAssembler.assembleBoard(board);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID boardId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.ADMIN);
        boardRepository.delete(loadBoard(workspaceId, boardId));
    }

    @Transactional
    public BoardResponse addColumn(
            UUID workspaceId,
            UUID boardId,
            CreateColumnRequest request,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        Board board = loadBoardWithColumns(workspaceId, boardId);
        board.addColumn(request.name(), request.wipLimit());
        return boardAssembler.assembleBoard(board);
    }

    @Transactional
    public BoardResponse updateColumn(
            UUID workspaceId,
            UUID boardId,
            UUID columnId,
            UpdateColumnRequest request,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        Board board = loadBoardWithColumns(workspaceId, boardId);
        board.requireColumn(columnId).rename(request.name(), request.wipLimit());
        return boardAssembler.assembleBoard(board);
    }

    @Transactional
    public BoardResponse moveColumn(
            UUID workspaceId,
            UUID boardId,
            UUID columnId,
            MoveColumnRequest request,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        Board board = loadBoardWithColumns(workspaceId, boardId);
        board.moveColumn(columnId, request.position());
        return boardAssembler.assembleBoard(board);
    }

    @Transactional
    public BoardResponse deleteColumn(UUID workspaceId, UUID boardId, UUID columnId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        Board board = loadBoardWithColumns(workspaceId, boardId);
        // The column's tasks are removed with it by the database cascade.
        board.removeColumn(columnId);
        return boardAssembler.assembleBoard(board);
    }

    private Map<UUID, Long> countTasksFor(List<UUID> boardIds) {
        Map<UUID, Long> counts = new HashMap<>();
        for (Object[] row : taskRepository.countByBoardIds(boardIds)) {
            counts.put((UUID) row[0], (Long) row[1]);
        }
        return counts;
    }

    private Board loadBoard(UUID workspaceId, UUID boardId) {
        return boardRepository.findByIdAndWorkspaceId(boardId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Board", boardId));
    }

    private Board loadBoardWithColumns(UUID workspaceId, UUID boardId) {
        return boardRepository.findWithColumnsByIdAndWorkspaceId(boardId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Board", boardId));
    }
}
