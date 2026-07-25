package com.devforge.workspace.contract;

import java.util.UUID;

/**
 * Published interface every workspace-scoped module uses to authorise a request.
 *
 * <p>This is the seam that replaced the old service-to-service call chain
 * ({@code DocumentService -> WorkspaceService}, which handed back a {@code
 * Workspace} entity). The {@code document} and {@code task} modules now depend
 * only on this interface and the records in this package, so they cannot reach
 * workspace persistence and cannot be broken by changes to it.
 *
 * <p>Centralising the check here also means authorisation cannot be forgotten in
 * one module and enforced in another: resolving the workspace <em>is</em> the
 * permission check.
 */
public interface WorkspaceAccess {

    /**
     * Authorises {@code userId} against {@code workspaceId} and returns the
     * workspace.
     *
     * @throws com.devforge.shared.exception.ResourceNotFoundException if the
     *         workspace does not exist <em>or</em> the user is not a member —
     *         the two are deliberately indistinguishable so that membership of
     *         other teams' workspaces cannot be probed
     * @throws com.devforge.shared.exception.PermissionDeniedException if the user
     *         is a member but holds a role below {@code minimumRole}
     */
    WorkspaceRef requireAccess(UUID workspaceId, UUID userId, WorkspaceRole minimumRole);
}
