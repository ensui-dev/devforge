package com.devforge.workspace.domain;

import com.devforge.shared.domain.BaseEntity;
import com.devforge.workspace.contract.WorkspaceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.UUID;

/**
 * Enrolment of one user in one workspace.
 *
 * <p>{@code userId} is a plain identifier rather than an association to the
 * {@code User} entity: membership belongs to the workspace module, identity does
 * not, and referencing across that boundary by id keeps the two independently
 * loadable while the database foreign key still guarantees integrity.
 */
@Entity
@Table(
        name = "workspace_members",
        uniqueConstraints = @UniqueConstraint(columnNames = {"workspace_id", "user_id"})
)
public class WorkspaceMember extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceRole role;

    protected WorkspaceMember() {
    }

    public WorkspaceMember(UUID workspaceId, UUID userId, WorkspaceRole role) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public UUID getUserId() {
        return userId;
    }

    public WorkspaceRole getRole() {
        return role;
    }

    public void changeRole(WorkspaceRole role) {
        this.role = role;
    }
}
