package com.devforge.task.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.DocumentDirectory;
import com.devforge.shared.exception.DomainValidationException;
import com.devforge.shared.exception.DuplicateResourceException;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.task.domain.Board;
import com.devforge.task.domain.BoardColumn;
import com.devforge.task.domain.BoardRepository;
import com.devforge.task.domain.Task;
import com.devforge.task.domain.TaskDocumentLink;
import com.devforge.task.domain.TaskDocumentLinkRepository;
import com.devforge.task.domain.TaskOrdering;
import com.devforge.task.domain.TaskRepository;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Task lifecycle, placement, and document links.
 *
 * <p>Ordering arithmetic lives in {@link TaskOrdering}; this class handles
 * authorisation, loading, and the cross-module validation that an assignee is
 * actually on the team and a cited document actually belongs to the workspace.
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final BoardRepository boardRepository;
    private final TaskDocumentLinkRepository linkRepository;
    private final WorkspaceAccess workspaceAccess;
    private final DocumentDirectory documentDirectory;
    private final BoardAssembler boardAssembler;
    private final AuditTrail auditTrail;

    public TaskService(
            TaskRepository taskRepository,
            BoardRepository boardRepository,
            TaskDocumentLinkRepository linkRepository,
            WorkspaceAccess workspaceAccess,
            DocumentDirectory documentDirectory,
            BoardAssembler boardAssembler,
            AuditTrail auditTrail
    ) {
        this.auditTrail = auditTrail;
        this.taskRepository = taskRepository;
        this.boardRepository = boardRepository;
        this.linkRepository = linkRepository;
        this.workspaceAccess = workspaceAccess;
        this.documentDirectory = documentDirectory;
        this.boardAssembler = boardAssembler;
    }

    @Transactional
    public TaskResponse create(UUID workspaceId, UUID boardId, CreateTaskRequest request, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);

        Board board = loadBoardWithColumns(workspaceId, boardId);
        BoardColumn column = board.requireColumn(request.columnId());

        int existingCount = taskRepository.countByColumnId(column.getId());
        column.requireCapacityFor(existingCount + 1);

        requireAssigneeIsMember(workspaceId, request.assigneeId());

        Task task = taskRepository.save(new Task(
                boardId,
                column.getId(),
                request.title(),
                request.description(),
                TaskOrdering.appendPosition(existingCount),
                request.priority(),
                request.assigneeId()
        ));

        for (UUID documentId : request.linkedDocumentIds()) {
            // Verifies the document is in this workspace before linking.
            documentDirectory.require(workspaceId, documentId);
            linkRepository.save(new TaskDocumentLink(task.getId(), documentId));
        }

        auditTrail.record(userId, AuditEntry
                .of(AuditAction.TASK_CREATED, AuditTargetType.TASK)
                .target(task.getId(), task.getTitle())
                .inWorkspace(workspaceId)
                .with("column", column.getName())
                .with("priority", task.getPriority())
                .with("linkedDocuments", request.linkedDocumentIds().size()));

        return boardAssembler.assembleTask(workspaceId, task);
    }

    @Transactional
    public TaskResponse update(
            UUID workspaceId,
            UUID boardId,
            UUID taskId,
            UpdateTaskRequest request,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        requireBoardInWorkspace(workspaceId, boardId);

        Task task = loadTask(boardId, taskId);
        requireAssigneeIsMember(workspaceId, request.assigneeId());

        String previousTitle = task.getTitle();
        var previousPriority = task.getPriority();
        UUID previousAssignee = task.getAssigneeId();

        task.revise(request.title(), request.description(), request.priority(), request.assigneeId());

        auditTrail.record(userId, AuditEntry
                .of(AuditAction.TASK_UPDATED, AuditTargetType.TASK)
                .target(task.getId(), task.getTitle())
                .inWorkspace(workspaceId)
                .changed("title", previousTitle, task.getTitle())
                .changed("priority", previousPriority, task.getPriority())
                .changed("assignee", previousAssignee, task.getAssigneeId()));

        return boardAssembler.assembleTask(workspaceId, task);
    }

    /**
     * Moves a task within or between columns, renumbering the affected columns so
     * positions stay contiguous.
     */
    @Transactional
    public TaskResponse move(
            UUID workspaceId,
            UUID boardId,
            UUID taskId,
            MoveTaskRequest request,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);

        Board board = loadBoardWithColumns(workspaceId, boardId);
        BoardColumn targetColumn = board.requireColumn(request.columnId());
        Task task = loadTask(boardId, taskId);

        UUID sourceColumnId = task.getColumnId();

        if (sourceColumnId.equals(targetColumn.getId())) {
            TaskOrdering.repositionWithinColumn(
                    taskRepository.findByColumnIdOrderByPositionAsc(sourceColumnId),
                    task,
                    request.position());
        } else {
            List<Task> targetTasks = taskRepository.findByColumnIdOrderByPositionAsc(targetColumn.getId());
            targetColumn.requireCapacityFor(targetTasks.size() + 1);

            TaskOrdering.moveAcrossColumns(
                    taskRepository.findByColumnIdOrderByPositionAsc(sourceColumnId),
                    targetTasks,
                    task,
                    targetColumn.getId(),
                    request.position());
        }

        // A move between columns is the event people actually look for in a
        // board's history; a reorder within one column is noise by comparison,
        // so the two are distinguishable by the presence of `from`.
        auditTrail.record(userId, AuditEntry
                .of(AuditAction.TASK_MOVED, AuditTargetType.TASK)
                .target(task.getId(), task.getTitle())
                .inWorkspace(workspaceId)
                .changed("column",
                        board.requireColumn(sourceColumnId).getName(),
                        targetColumn.getName())
                .with("position", request.position()));

        return boardAssembler.assembleTask(workspaceId, task);
    }

    @Transactional
    public void delete(UUID workspaceId, UUID boardId, UUID taskId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        requireBoardInWorkspace(workspaceId, boardId);

        Task task = loadTask(boardId, taskId);
        UUID columnId = task.getColumnId();

        auditTrail.record(userId, AuditEntry
                .of(AuditAction.TASK_DELETED, AuditTargetType.TASK)
                .target(task.getId(), task.getTitle())
                .inWorkspace(workspaceId));

        taskRepository.delete(task);
        // Flush the delete before renumbering so the compaction sees the column
        // without this task.
        taskRepository.flush();

        TaskOrdering.compact(taskRepository.findByColumnIdOrderByPositionAsc(columnId));
    }

    @Transactional
    public TaskResponse linkDocument(
            UUID workspaceId,
            UUID boardId,
            UUID taskId,
            LinkDocumentRequest request,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        requireBoardInWorkspace(workspaceId, boardId);

        Task task = loadTask(boardId, taskId);
        documentDirectory.require(workspaceId, request.documentId());

        if (linkRepository.existsByTaskIdAndDocumentId(taskId, request.documentId())) {
            throw new DuplicateResourceException("This document is already linked to the task");
        }

        linkRepository.save(new TaskDocumentLink(taskId, request.documentId()));

        auditTrail.record(userId, AuditEntry
                .of(AuditAction.TASK_DOCUMENT_LINKED, AuditTargetType.TASK)
                .target(task.getId(), task.getTitle())
                .inWorkspace(workspaceId)
                .with("documentId", request.documentId()));

        return boardAssembler.assembleTask(workspaceId, task);
    }

    @Transactional
    public TaskResponse unlinkDocument(
            UUID workspaceId,
            UUID boardId,
            UUID taskId,
            UUID documentId,
            UUID userId
    ) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);
        requireBoardInWorkspace(workspaceId, boardId);

        Task task = loadTask(boardId, taskId);
        TaskDocumentLink link = linkRepository.findByTaskIdAndDocumentId(taskId, documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Task document link", documentId));

        auditTrail.record(userId, AuditEntry
                .of(AuditAction.TASK_DOCUMENT_UNLINKED, AuditTargetType.TASK)
                .target(task.getId(), task.getTitle())
                .inWorkspace(workspaceId)
                .with("documentId", documentId));

        linkRepository.delete(link);
        linkRepository.flush();

        return boardAssembler.assembleTask(workspaceId, task);
    }

    /**
     * A task may only be assigned to someone who can actually open the workspace.
     *
     * <p>Checked through {@link WorkspaceAccess} rather than by querying members
     * directly, so the rule stays consistent with how access is decided
     * everywhere else.
     */
    private void requireAssigneeIsMember(UUID workspaceId, UUID assigneeId) {
        if (assigneeId == null) {
            return;
        }
        try {
            workspaceAccess.requireAccess(workspaceId, assigneeId, WorkspaceRole.VIEWER);
        } catch (ResourceNotFoundException ex) {
            throw new DomainValidationException("The assignee is not a member of this workspace");
        }
    }

    private void requireBoardInWorkspace(UUID workspaceId, UUID boardId) {
        if (!boardRepository.findByIdAndWorkspaceId(boardId, workspaceId).isPresent()) {
            throw new ResourceNotFoundException("Board", boardId);
        }
    }

    private Board loadBoardWithColumns(UUID workspaceId, UUID boardId) {
        return boardRepository.findWithColumnsByIdAndWorkspaceId(boardId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Board", boardId));
    }

    private Task loadTask(UUID boardId, UUID taskId) {
        return taskRepository.findByIdAndBoardId(taskId, boardId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
    }
}
