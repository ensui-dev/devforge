package com.devforge.workspace.application;

import com.devforge.workspace.contract.WorkspaceRole;
import com.devforge.workspace.domain.Workspace;

import java.time.Instant;
import java.util.UUID;

/**
 * @param callerRole the requesting user's role, so the client can hide actions it
 *                   is not permitted to perform without a second round trip
 */
public record WorkspaceResponse(
        UUID id,
        String name,
        String description,
        String slug,
        WorkspaceRole callerRole,
        Instant createdAt,
        Instant updatedAt
) {

    public static WorkspaceResponse from(Workspace workspace, WorkspaceRole callerRole) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getDescription(),
                workspace.getSlug(),
                callerRole,
                workspace.getCreatedAt(),
                workspace.getUpdatedAt()
        );
    }
}
