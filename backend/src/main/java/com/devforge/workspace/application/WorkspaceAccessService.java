package com.devforge.workspace.application;

import com.devforge.shared.exception.PermissionDeniedException;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.workspace.contract.WorkspaceAccess;
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
public class WorkspaceAccessService implements WorkspaceAccess {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;

    public WorkspaceAccessService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository
    ) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
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
}
