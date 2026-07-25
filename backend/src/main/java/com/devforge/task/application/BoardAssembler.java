package com.devforge.task.application;

import com.devforge.document.contract.DocumentDirectory;
import com.devforge.document.contract.DocumentRef;
import com.devforge.identity.contract.UserDirectory;
import com.devforge.identity.contract.UserRef;
import com.devforge.task.domain.Board;
import com.devforge.task.domain.BoardColumn;
import com.devforge.task.domain.Task;
import com.devforge.task.domain.TaskDocumentLink;
import com.devforge.task.domain.TaskDocumentLinkRepository;
import com.devforge.task.domain.TaskRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Composes board and task responses from several sources.
 *
 * <p>This exists because a full kanban view needs data from three modules — the
 * board's own tasks, assignee names from {@code identity}, and cited document
 * titles from {@code document} — and gathering it inside a service method would
 * bury the query pattern. Concentrating it here makes the cost explicit and
 * constant: <strong>four queries for a whole board</strong> (tasks, links,
 * assignees, documents), regardless of how many tasks or columns it has.
 *
 * <p>Both cross-module reads go through published contracts, so this class holds
 * no reference to another module's entities or repositories.
 */
@Component
public class BoardAssembler {

    private final TaskRepository taskRepository;
    private final TaskDocumentLinkRepository linkRepository;
    private final UserDirectory userDirectory;
    private final DocumentDirectory documentDirectory;

    public BoardAssembler(
            TaskRepository taskRepository,
            TaskDocumentLinkRepository linkRepository,
            UserDirectory userDirectory,
            DocumentDirectory documentDirectory
    ) {
        this.taskRepository = taskRepository;
        this.linkRepository = linkRepository;
        this.userDirectory = userDirectory;
        this.documentDirectory = documentDirectory;
    }

    /** Builds the full board view: columns in order, each with its tasks in order. */
    public BoardResponse assembleBoard(Board board) {
        List<Task> tasks = taskRepository.findByBoardIdOrderByColumnIdAscPositionAsc(board.getId());
        Enrichment enrichment = enrich(board.getWorkspaceId(), tasks);

        // Preserve the query's position ordering by inserting in encounter order.
        Map<UUID, List<TaskResponse>> tasksByColumn = new LinkedHashMap<>();
        for (Task task : tasks) {
            tasksByColumn
                    .computeIfAbsent(task.getColumnId(), key -> new ArrayList<>())
                    .add(enrichment.toResponse(task));
        }

        List<BoardColumnResponse> columns = board.getColumns().stream()
                .map(column -> BoardColumnResponse.of(
                        column,
                        tasksByColumn.getOrDefault(column.getId(), List.of())))
                .toList();

        return new BoardResponse(
                board.getId(),
                board.getWorkspaceId(),
                board.getName(),
                columns,
                board.getCreatedAt(),
                board.getUpdatedAt()
        );
    }

    /** Builds one task response, for endpoints that return a single task. */
    public TaskResponse assembleTask(UUID workspaceId, Task task) {
        return enrich(workspaceId, List.of(task)).toResponse(task);
    }

    private Enrichment enrich(UUID workspaceId, List<Task> tasks) {
        if (tasks.isEmpty()) {
            return new Enrichment(Map.of(), Map.of(), Map.of());
        }

        List<UUID> taskIds = tasks.stream().map(Task::getId).toList();
        List<TaskDocumentLink> links = linkRepository.findByTaskIdIn(taskIds);

        Map<UUID, List<UUID>> documentIdsByTask = new LinkedHashMap<>();
        for (TaskDocumentLink link : links) {
            documentIdsByTask
                    .computeIfAbsent(link.getTaskId(), key -> new ArrayList<>())
                    .add(link.getDocumentId());
        }

        Map<UUID, UserRef> assignees = userDirectory.findAllByIds(
                tasks.stream()
                        .map(Task::getAssigneeId)
                        .filter(java.util.Objects::nonNull)
                        .distinct()
                        .toList());

        Map<UUID, DocumentRef> documents = documentDirectory.findAllByIds(
                workspaceId,
                links.stream().map(TaskDocumentLink::getDocumentId).distinct().toList());

        return new Enrichment(documentIdsByTask, assignees, documents);
    }

    /**
     * Pre-resolved lookups shared across every task in one response.
     *
     * @param documentIdsByTask which documents each task cites
     * @param assignees         user references keyed by id
     * @param documents         document references keyed by id
     */
    private record Enrichment(
            Map<UUID, List<UUID>> documentIdsByTask,
            Map<UUID, UserRef> assignees,
            Map<UUID, DocumentRef> documents
    ) {

        TaskResponse toResponse(Task task) {
            List<LinkedDocumentResponse> linkedDocuments = documentIdsByTask
                    .getOrDefault(task.getId(), List.of())
                    .stream()
                    .map(documents::get)
                    // A link can outlive its document only in the window before the
                    // cascade completes; skip rather than emit a null entry.
                    .filter(java.util.Objects::nonNull)
                    .map(LinkedDocumentResponse::from)
                    .toList();

            TaskAssigneeResponse assignee = task.getAssigneeId() == null
                    ? null
                    : TaskAssigneeResponse.from(assignees.get(task.getAssigneeId()));

            return TaskResponse.of(task, assignee, linkedDocuments);
        }
    }
}
