package com.devforge.workspace.application;

import com.devforge.identity.contract.UserDirectory;
import com.devforge.identity.contract.UserRef;
import com.devforge.shared.exception.PermissionDeniedException;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceLookup;
import com.devforge.workspace.contract.WorkspaceRef;
import com.devforge.workspace.contract.WorkspaceRole;
import com.devforge.workspace.domain.Workspace;
import com.devforge.workspace.domain.WorkspaceMember;
import com.devforge.workspace.domain.WorkspaceMemberRepository;
import com.devforge.workspace.domain.WorkspaceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The single implementation of {@link WorkspaceAccess}.
 *
 * <p>Every workspace-scoped operation in the system funnels through
 * {@link #requireAccess}, which is what makes authorisation auditable: there is
 * one method to read to know how access is decided.
 */
@Service
@Transactional(readOnly = true)
public class WorkspaceAccessService implements WorkspaceAccess, WorkspaceLookup {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final UserDirectory userDirectory;

    public WorkspaceAccessService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            UserDirectory userDirectory
    ) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.userDirectory = userDirectory;
    }

    @Override
    public WorkspaceRef requireAccess(UUID workspaceId, UUID userId, WorkspaceRole minimumRole) {
        WorkspaceMember membership = memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                // Non-members get 404 rather than 403: a 403 would confirm the
                // workspace exists, letting an outsider enumerate other teams.
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));

        if (!membership.getRole().atLeast(minimumRole)) {
            throw new PermissionDeniedException(
                    "This action requires the %s role or higher".formatted(minimumRole));
        }

        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));

        return new WorkspaceRef(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                membership.getRole()
        );
    }

    @Override
    public java.util.Optional<PublishedWorkspace> findPublished(String handle, String slug) {
        return userDirectory.findByHandle(handle)
                .flatMap(owner -> workspaceRepository
                        .findByOwnerUserIdAndSlugAndPublishedAtIsNotNull(owner.id(), slug)
                        .map(workspace -> toPublished(workspace, owner.handle())));
    }

    @Override
    public java.util.List<PublishedWorkspace> findPublishedByOwner(String handle) {
        return userDirectory.findByHandle(handle)
                .map(owner -> workspaceRepository
                        .findAllByOwnerUserIdAndPublishedAtIsNotNullOrderByNameAsc(owner.id())
                        .stream()
                        .map(workspace -> toPublished(workspace, owner.handle()))
                        .toList())
                .orElseGet(java.util.List::of);
    }

    @Override
    public java.util.List<PublishedWorkspace> findAllPublished() {
        return withHandles(workspaceRepository.findAllByPublishedAtIsNotNullOrderByNameAsc());
    }

    @Override
    public java.util.List<PublishedWorkspace> findPublishedBySlug(String slug) {
        return withHandles(workspaceRepository.findAllBySlugAndPublishedAtIsNotNull(slug));
    }

    /** Resolves every owner handle in one call rather than one query per workspace. */
    private java.util.List<PublishedWorkspace> withHandles(java.util.List<Workspace> workspaces) {
        if (workspaces.isEmpty()) {
            return java.util.List.of();
        }
        java.util.Map<UUID, UserRef> owners = userDirectory.findAllByIds(
                workspaces.stream().map(Workspace::getOwnerUserId).distinct().toList());

        return workspaces.stream()
                .filter(workspace -> owners.containsKey(workspace.getOwnerUserId()))
                .map(workspace -> toPublished(
                        workspace, owners.get(workspace.getOwnerUserId()).handle()))
                .toList();
    }

    private static PublishedWorkspace toPublished(Workspace workspace, String ownerHandle) {
        return new PublishedWorkspace(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                ownerHandle,
                workspace.getDescription(),
                workspace.getPublishedAt()
        );
    }
}
