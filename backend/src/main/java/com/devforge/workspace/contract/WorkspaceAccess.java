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

    /**
     * Resolves a workspace by the address a git remote uses, and authorises it in
     * the same step.
     *
     * <p>Deliberately not two calls. Looking up first and checking afterwards would
     * hand back the identity of a workspace the caller may not see, and the whole
     * point of {@code requireAccess} is that resolving <em>is</em> the check.
     *
     * <p>{@link WorkspaceLookup} cannot serve this: it only ever returns published
     * workspaces, by design, and a private workspace is exactly the case a git
     * remote needs.
     *
     * @return empty when no such workspace exists, when the caller is not a member,
     *         or when their role is below {@code minimumRole} — indistinguishable,
     *         so a git URL cannot be used to discover which workspaces exist
     */
    java.util.Optional<WorkspaceRef> findForCaller(
            String ownerHandle,
            String slug,
            UUID userId,
            WorkspaceRole minimumRole);
}
