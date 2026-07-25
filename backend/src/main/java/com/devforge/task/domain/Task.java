package com.devforge.task.domain;

import com.devforge.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

/**
 * A unit of delivery work, living in one column of one board.
 *
 * <p>Its own aggregate: board, column, and assignee are referenced by id. Moving a
 * task therefore touches only the affected column's tasks, and the {@code task}
 * module needs nothing from {@code identity} persistence to record an assignee.
 */
@Entity
@Table(name = "tasks")
public class Task extends BaseEntity {

    @Column(name = "board_id", nullable = false, updatable = false)
    private UUID boardId;

    @Column(name = "column_id", nullable = false)
    private UUID columnId;

    @Column(name = "assignee_id")
    private UUID assigneeId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskPriority priority = TaskPriority.MEDIUM;

    protected Task() {
    }

    public Task(
            UUID boardId,
            UUID columnId,
            String title,
            String description,
            int position,
            TaskPriority priority,
            UUID assigneeId
    ) {
        this.boardId = boardId;
        this.columnId = columnId;
        this.title = title;
        this.description = description;
        this.position = position;
        this.priority = priority == null ? TaskPriority.MEDIUM : priority;
        this.assigneeId = assigneeId;
    }

    public UUID getBoardId() {
        return boardId;
    }

    public UUID getColumnId() {
        return columnId;
    }

    public UUID getAssigneeId() {
        return assigneeId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getPosition() {
        return position;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    /** Applies an edit to the task's own fields. Position and column are moved separately. */
    public void revise(String title, String description, TaskPriority priority, UUID assigneeId) {
        this.title = title;
        this.description = description;
        this.priority = priority == null ? TaskPriority.MEDIUM : priority;
        this.assigneeId = assigneeId;
    }

    void moveTo(UUID columnId, int position) {
        this.columnId = columnId;
        this.position = position;
    }

    void reposition(int position) {
        this.position = position;
    }

    public boolean isOn(UUID boardId) {
        return this.boardId.equals(boardId);
    }
}
