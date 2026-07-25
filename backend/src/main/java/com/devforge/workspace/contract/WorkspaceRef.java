package com.devforge.workspace.contract;

import java.util.UUID;

/**
 * The workspace module's public view of a workspace, plus the role held by the
 * user whose access was just checked.
 */
public record WorkspaceRef(UUID id, String name, String slug, WorkspaceRole callerRole) {
}
