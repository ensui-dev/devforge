package com.devforge.task.application;

import com.devforge.audit.contract.AuditTrail;
import com.devforge.document.contract.DocumentDirectory;
import com.devforge.document.contract.DocumentRef;
import com.devforge.document.contract.DocumentType;
import com.devforge.shared.exception.DomainValidationException;
import com.devforge.shared.exception.DuplicateResourceException;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.task.domain.Board;
import com.devforge.task.domain.BoardRepository;
import com.devforge.task.domain.Task;
import com.devforge.task.domain.TaskDocumentLink;
import com.devforge.task.domain.TaskDocumentLinkRepository;
import com.devforge.task.domain.TaskPriority;
import com.devforge.task.domain.TaskRepository;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRef;
import com.devforge.workspace.contract.WorkspaceRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskServiceTest {

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private TaskDocumentLinkRepository linkRepository;

    @Mock
    private WorkspaceAccess workspaceAccess;

    @Mock
    private DocumentDirectory documentDirectory;

    @Mock
    private BoardAssembler boardAssembler;

    @Mock
    private AuditTrail auditTrail;

    @InjectMocks
    private TaskService taskService;

    private UUID workspaceId;
    private UUID userId;
    private Board board;
    private UUID backlogId;
    private UUID inProgressId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        board = Board.withDefaultColumns(workspaceId, "Delivery");
        backlogId = board.getColumns().get(0).getId();
        inProgressId = board.getColumns().get(1).getId();

        givenAccess(WorkspaceRole.MEMBER);
        when(boardRepository.findWithColumnsByIdAndWorkspaceId(board.getId(), workspaceId))
                .thenReturn(Optional.of(board));
        when(boardRepository.findByIdAndWorkspaceId(board.getId(), workspaceId))
                .thenReturn(Optional.of(board));
        when(taskRepository.save(any(Task.class))).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void appendsANewTaskToTheEndOfItsColumn() {
        when(taskRepository.countByColumnId(backlogId)).thenReturn(2);

        taskService.create(workspaceId, board.getId(), request(backlogId, null, List.of()), userId);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getPosition()).isEqualTo(2);
        assertThat(captor.getValue().getColumnId()).isEqualTo(backlogId);
    }

    @Test
    void defaultsPriorityToMedium() {
        when(taskRepository.countByColumnId(backlogId)).thenReturn(0);

        taskService.create(
                workspaceId,
                board.getId(),
                new CreateTaskRequest("Task", null, backlogId, null, null, null),
                userId);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getPriority()).isEqualTo(TaskPriority.MEDIUM);
    }

    @Test
    void rejectsCreationInAnUnknownColumn() {
        assertThatThrownBy(() -> taskService.create(
                workspaceId, board.getId(), request(UUID.randomUUID(), null, List.of()), userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void enforcesTheWipLimitOnCreation() {
        board.addColumn("Capped", 1);
        UUID cappedId = board.getColumns().getLast().getId();
        when(taskRepository.countByColumnId(cappedId)).thenReturn(1);

        assertThatThrownBy(() -> taskService.create(
                workspaceId, board.getId(), request(cappedId, null, List.of()), userId))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("work-in-progress limit");
    }

    @Test
    void linksCitedDocumentsOnCreation() {
        UUID documentId = UUID.randomUUID();
        when(taskRepository.countByColumnId(backlogId)).thenReturn(0);
        when(documentDirectory.require(workspaceId, documentId))
                .thenReturn(new DocumentRef(documentId, workspaceId, "Spec", "spec", DocumentType.API));

        taskService.create(
                workspaceId, board.getId(), request(backlogId, null, List.of(documentId)), userId);

        verify(documentDirectory).require(workspaceId, documentId);
        verify(linkRepository).save(any(TaskDocumentLink.class));
    }

    /** A document from another workspace must not become linkable. */
    @Test
    void refusesToCiteADocumentOutsideTheWorkspace() {
        UUID foreignDocument = UUID.randomUUID();
        when(taskRepository.countByColumnId(backlogId)).thenReturn(0);
        when(documentDirectory.require(workspaceId, foreignDocument))
                .thenThrow(new ResourceNotFoundException("Document", foreignDocument));

        assertThatThrownBy(() -> taskService.create(
                workspaceId, board.getId(), request(backlogId, null, List.of(foreignDocument)), userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsAnAssigneeWhoIsNotAMember() {
        UUID outsider = UUID.randomUUID();
        when(taskRepository.countByColumnId(backlogId)).thenReturn(0);
        when(workspaceAccess.requireAccess(workspaceId, outsider, WorkspaceRole.VIEWER))
                .thenThrow(new ResourceNotFoundException("Workspace", workspaceId));

        assertThatThrownBy(() -> taskService.create(
                workspaceId, board.getId(), request(backlogId, outsider, List.of()), userId))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("not a member");
    }

    @Test
    void acceptsAnAssigneeWhoIsAMember() {
        UUID teammate = UUID.randomUUID();
        when(taskRepository.countByColumnId(backlogId)).thenReturn(0);
        when(workspaceAccess.requireAccess(workspaceId, teammate, WorkspaceRole.VIEWER))
                .thenReturn(new WorkspaceRef(workspaceId, "Platform", "platform", WorkspaceRole.VIEWER));

        taskService.create(workspaceId, board.getId(), request(backlogId, teammate, List.of()), userId);

        ArgumentCaptor<Task> captor = ArgumentCaptor.forClass(Task.class);
        verify(taskRepository).save(captor.capture());
        assertThat(captor.getValue().getAssigneeId()).isEqualTo(teammate);
    }

    @Test
    void updatesTaskFieldsWithoutTouchingPlacement() {
        Task task = task(backlogId, "Original", 3);
        when(taskRepository.findByIdAndBoardId(task.getId(), board.getId())).thenReturn(Optional.of(task));

        taskService.update(
                workspaceId,
                board.getId(),
                task.getId(),
                new UpdateTaskRequest("Renamed", "New description", TaskPriority.HIGH, null),
                userId);

        assertThat(task.getTitle()).isEqualTo("Renamed");
        assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(task.getPosition()).as("an edit must not reorder the board").isEqualTo(3);
        assertThat(task.getColumnId()).isEqualTo(backlogId);
    }

    @Test
    void reordersWithinAColumn() {
        List<Task> tasks = new ArrayList<>(List.of(
                task(backlogId, "A", 0), task(backlogId, "B", 1), task(backlogId, "C", 2)));
        Task moved = tasks.get(2);
        when(taskRepository.findByIdAndBoardId(moved.getId(), board.getId())).thenReturn(Optional.of(moved));
        when(taskRepository.findByColumnIdOrderByPositionAsc(backlogId)).thenReturn(tasks);

        taskService.move(workspaceId, board.getId(), moved.getId(),
                new MoveTaskRequest(backlogId, 0), userId);

        assertThat(moved.getPosition()).isZero();
        assertThat(tasks.get(0).getPosition()).isEqualTo(1);
        assertThat(tasks.get(1).getPosition()).isEqualTo(2);
    }

    @Test
    void movesBetweenColumnsAndClosesTheSourceGap() {
        Task moved = task(backlogId, "Moving", 1);
        List<Task> source = new ArrayList<>(List.of(task(backlogId, "Stays", 0), moved));
        List<Task> target = new ArrayList<>(List.of(task(inProgressId, "Existing", 0)));

        when(taskRepository.findByIdAndBoardId(moved.getId(), board.getId())).thenReturn(Optional.of(moved));
        when(taskRepository.findByColumnIdOrderByPositionAsc(backlogId)).thenReturn(source);
        when(taskRepository.findByColumnIdOrderByPositionAsc(inProgressId)).thenReturn(target);

        taskService.move(workspaceId, board.getId(), moved.getId(),
                new MoveTaskRequest(inProgressId, 0), userId);

        assertThat(moved.getColumnId()).isEqualTo(inProgressId);
        assertThat(moved.getPosition()).isZero();
        assertThat(source.getFirst().getPosition()).isZero();
        assertThat(target.getFirst().getPosition()).isEqualTo(1);
    }

    @Test
    void enforcesTheWipLimitOnAnIncomingMove() {
        board.addColumn("Capped", 1);
        UUID cappedId = board.getColumns().getLast().getId();
        Task moved = task(backlogId, "Moving", 0);

        when(taskRepository.findByIdAndBoardId(moved.getId(), board.getId())).thenReturn(Optional.of(moved));
        when(taskRepository.findByColumnIdOrderByPositionAsc(cappedId))
                .thenReturn(new ArrayList<>(List.of(task(cappedId, "Occupant", 0))));

        assertThatThrownBy(() -> taskService.move(workspaceId, board.getId(), moved.getId(),
                new MoveTaskRequest(cappedId, 0), userId))
                .isInstanceOf(DomainValidationException.class);

        assertThat(moved.getColumnId()).as("a rejected move must not mutate the task").isEqualTo(backlogId);
    }

    @Test
    void deletingATaskCompactsItsColumn() {
        Task deleted = task(backlogId, "Gone", 0);
        List<Task> remaining = new ArrayList<>(List.of(task(backlogId, "Kept", 5)));
        when(taskRepository.findByIdAndBoardId(deleted.getId(), board.getId()))
                .thenReturn(Optional.of(deleted));
        when(taskRepository.findByColumnIdOrderByPositionAsc(backlogId)).thenReturn(remaining);

        taskService.delete(workspaceId, board.getId(), deleted.getId(), userId);

        verify(taskRepository).delete(deleted);
        assertThat(remaining.getFirst().getPosition()).isZero();
    }

    @Test
    void rejectsADuplicateDocumentLink() {
        Task task = task(backlogId, "Task", 0);
        UUID documentId = UUID.randomUUID();
        when(taskRepository.findByIdAndBoardId(task.getId(), board.getId())).thenReturn(Optional.of(task));
        when(linkRepository.existsByTaskIdAndDocumentId(task.getId(), documentId)).thenReturn(true);

        assertThatThrownBy(() -> taskService.linkDocument(
                workspaceId, board.getId(), task.getId(),
                new LinkDocumentRequest(documentId), userId))
                .isInstanceOf(DuplicateResourceException.class);

        verify(linkRepository, never()).save(any());
    }

    @Test
    void unlinksAnExistingDocument() {
        Task task = task(backlogId, "Task", 0);
        UUID documentId = UUID.randomUUID();
        TaskDocumentLink link = new TaskDocumentLink(task.getId(), documentId);
        when(taskRepository.findByIdAndBoardId(task.getId(), board.getId())).thenReturn(Optional.of(task));
        when(linkRepository.findByTaskIdAndDocumentId(task.getId(), documentId)).thenReturn(Optional.of(link));

        taskService.unlinkDocument(workspaceId, board.getId(), task.getId(), documentId, userId);

        verify(linkRepository).delete(link);
    }

    @Test
    void reportsNotFoundForATaskOnAnotherBoard() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findByIdAndBoardId(taskId, board.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.update(
                workspaceId, board.getId(), taskId,
                new UpdateTaskRequest("Title", null, null, null), userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private CreateTaskRequest request(UUID columnId, UUID assigneeId, List<UUID> documentIds) {
        return new CreateTaskRequest(
                "Wire up API client", "Connect frontend to backend",
                columnId, TaskPriority.MEDIUM, assigneeId, documentIds);
    }

    private Task task(UUID columnId, String title, int position) {
        return new Task(board.getId(), columnId, title, null, position, TaskPriority.MEDIUM, null);
    }

    private void givenAccess(WorkspaceRole role) {
        when(workspaceAccess.requireAccess(workspaceId, userId, role))
                .thenReturn(new WorkspaceRef(workspaceId, "Platform", "platform", role));
    }
}
