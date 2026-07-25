package com.devforge.task.domain;

import com.devforge.shared.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * Attaches a task to a document — the join that keeps delivery work pointed at
 * source knowledge instead of restating it in a task description.
 *
 * <p>Modelled as an entity rather than a {@code @ManyToMany} so the link itself
 * can carry data (when it was made, and later who made it) and so the {@code task}
 * module never maps the {@code Document} type.
 */
@Entity
@Table(
        name = "task_document_links",
        uniqueConstraints = @UniqueConstraint(columnNames = {"task_id", "document_id"})
)
public class TaskDocumentLink extends BaseEntity {

    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID taskId;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    protected TaskDocumentLink() {
    }

    public TaskDocumentLink(UUID taskId, UUID documentId) {
        this.taskId = taskId;
        this.documentId = documentId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public UUID getDocumentId() {
        return documentId;
    }
}
