package com.devforge.workspace.application;

import com.devforge.shared.exception.DuplicateResourceException;
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

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class WorkspaceService {

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceAccess workspaceAccess;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            WorkspaceAccess workspaceAccess
    ) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.workspaceAccess = workspaceAccess;
    }

    /**
     * Lists only the workspaces the caller belongs to, each with their role.
     *
     * <p>Driven from the membership rows so the roles arrive with the same query
     * that determines visibility: two queries total regardless of how many
     * workspaces the user belongs to.
     */
    public List<WorkspaceResponse> findAllForUser(UUID userId) {
        Map<UUID, WorkspaceRole> rolesByWorkspace = memberRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(WorkspaceMember::getWorkspaceId, WorkspaceMember::getRole));

        if (rolesByWorkspace.isEmpty()) {
            return List.of();
        }

        return workspaceRepository.findAllById(rolesByWorkspace.keySet()).stream()
                .sorted(Comparator.comparing(Workspace::getName, String.CASE_INSENSITIVE_ORDER))
                .map(workspace -> WorkspaceResponse.from(workspace, rolesByWorkspace.get(workspace.getId())))
                .toList();
    }

    public WorkspaceResponse findById(UUID workspaceId, UUID userId) {
        WorkspaceRef ref = workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER);
        return WorkspaceResponse.from(loadWorkspace(workspaceId), ref.callerRole());
    }

    /** The creator is enrolled as {@code OWNER} in the same transaction. */
    @Transactional
    public WorkspaceResponse create(CreateWorkspaceRequest request, UUID creatorId) {
        if (workspaceRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("Workspace slug already exists: " + request.slug());
        }

        Workspace workspace = workspaceRepository.save(
                new Workspace(request.name(), request.description(), request.slug()));
        memberRepository.save(new WorkspaceMember(workspace.getId(), creatorId, WorkspaceRole.OWNER));

        return WorkspaceResponse.from(workspace, WorkspaceRole.OWNER);
    }

    @Transactional
    public WorkspaceResponse update(UUID workspaceId, UpdateWorkspaceRequest request, UUID userId) {
        WorkspaceRef ref = workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.ADMIN);
        Workspace workspace = loadWorkspace(workspaceId);

        if (!workspace.getSlug().equals(request.slug()) && workspaceRepository.existsBySlug(request.slug())) {
            throw new DuplicateResourceException("Workspace slug already exists: " + request.slug());
        }

        workspace.describe(request.name(), request.description(), request.slug());
        return WorkspaceResponse.from(workspace, ref.callerRole());
    }

    @Transactional
    public void delete(UUID workspaceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.OWNER);
        // Documents, boards, and memberships cascade at the database level.
        workspaceRepository.deleteById(workspaceId);
    }

    private Workspace loadWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }
}
