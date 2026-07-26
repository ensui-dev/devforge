package com.devforge.task.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
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
    private final AuditTrail auditTrail;

    public BoardService(
            BoardRepository boardRepository,
            TaskRepository taskRepository,
            WorkspaceAccess workspaceAccess,
            BoardAssembler boardAssembler,
            AuditTrail auditTrail
    ) {
        this.boardRepository = boardRepository;
        this.taskRepository = taskRepository;
        this.workspaceAccess = workspaceAccess;
        this.boardAssembler = boardAssembler;
        this.auditTrail = auditTrail;
    }

    /** Board and column events all read the same way, so they share one shape. */
    private void audit(UUID userId, AuditAction action, AuditTargetType type,
                       UUID id, String label, UUID workspaceId) {
        auditTrail.record(userId, AuditEntry.of(action, type)
                .target(id, label)
                .inWorkspace(workspaceId));
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
        audit(userId, AuditAction.BOARD_CREATED, AuditTargetType.BOARD,
                board.getId(), board.getName(), workspaceId);
        return boardAssembler.assembleBoard(board);
    }

    @Transactional
    public BoardResponse rename(UUID workspaceId, UUID boardId, UpdateBoardRequest request, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        Board board = loadBoardWithColumns(workspaceId, boardId);
        String previousName = board.getName();
        board.rename(request.name());
        auditTrail.record(userId, AuditEntry
                .of(AuditAction.BOARD_UPDATED, AuditTargetType.BOARD)
                .target(board.getId(), board.getName())
                .inWorkspace(workspaceId)
                .changed("name", previousName, board.getName()));
        return boardAssembler.assembleBoard(board);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID boardId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.ADMIN);
        Board board = loadBoard(workspaceId, boardId);
        audit(userId, AuditAction.BOARD_DELETED, AuditTargetType.BOARD,
                board.getId(), board.getName(), workspaceId);
        boardRepository.delete(board);
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
        auditTrail.record(userId, AuditEntry
                .of(AuditAction.COLUMN_CREATED, AuditTargetType.COLUMN)
                .target(board.getId(), request.name())
                .inWorkspace(workspaceId)
                .with("board", board.getName())
                .with("wipLimit", request.wipLimit()));
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
        var column = board.requireColumn(columnId);
        String previousName = column.getName();
        Integer previousLimit = column.getWipLimit();
        column.rename(request.name(), request.wipLimit());
        auditTrail.record(userId, AuditEntry
                .of(AuditAction.COLUMN_UPDATED, AuditTargetType.COLUMN)
                .target(columnId, column.getName())
                .inWorkspace(workspaceId)
                .with("board", board.getName())
                .changed("name", previousName, column.getName())
                .changed("wipLimit", previousLimit, column.getWipLimit()));
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
        auditTrail.record(userId, AuditEntry
                .of(AuditAction.COLUMN_UPDATED, AuditTargetType.COLUMN)
                .target(columnId, board.requireColumn(columnId).getName())
                .inWorkspace(workspaceId)
                .with("board", board.getName())
                .with("position", request.position()));
        return boardAssembler.assembleBoard(board);
    }

    @Transactional
    public BoardResponse deleteColumn(UUID workspaceId, UUID boardId, UUID columnId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        Board board = loadBoardWithColumns(workspaceId, boardId);
        String removed = board.requireColumn(columnId).getName();
        // The column's tasks are removed with it by the database cascade, so the
        // log is the only record that they existed.
        board.removeColumn(columnId);
        audit(userId, AuditAction.COLUMN_DELETED, AuditTargetType.COLUMN,
                columnId, removed, workspaceId);
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
