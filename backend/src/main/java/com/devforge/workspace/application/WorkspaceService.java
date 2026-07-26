package com.devforge.workspace.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
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
    private final AuditTrail auditTrail;

    public WorkspaceService(
            WorkspaceRepository workspaceRepository,
            WorkspaceMemberRepository memberRepository,
            WorkspaceAccess workspaceAccess,
            AuditTrail auditTrail
    ) {
        this.workspaceRepository = workspaceRepository;
        this.memberRepository = memberRepository;
        this.workspaceAccess = workspaceAccess;
        this.auditTrail = auditTrail;
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
        // Slugs are unique per owner, so another team's "platform" is no obstacle.
        if (workspaceRepository.existsByOwnerUserIdAndSlug(creatorId, request.slug())) {
            throw new DuplicateResourceException(
                    "You already have a workspace with the slug: " + request.slug());
        }

        Workspace workspace = workspaceRepository.save(
                new Workspace(request.name(), request.description(), request.slug(), creatorId));
        memberRepository.save(new WorkspaceMember(workspace.getId(), creatorId, WorkspaceRole.OWNER));

        auditTrail.record(creatorId, AuditEntry
                .of(AuditAction.WORKSPACE_CREATED, AuditTargetType.WORKSPACE)
                .target(workspace.getId(), workspace.getName())
                .inWorkspace(workspace.getId())
                .with("slug", workspace.getSlug()));

        return WorkspaceResponse.from(workspace, WorkspaceRole.OWNER);
    }

    @Transactional
    public WorkspaceResponse update(UUID workspaceId, UpdateWorkspaceRequest request, UUID userId) {
        WorkspaceRef ref = workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.ADMIN);
        Workspace workspace = loadWorkspace(workspaceId);

        if (!workspace.getSlug().equals(request.slug())
                && workspaceRepository.existsByOwnerUserIdAndSlug(
                        workspace.getOwnerUserId(), request.slug())) {
            throw new DuplicateResourceException(
                    "This owner already has a workspace with the slug: " + request.slug());
        }

        String previousName = workspace.getName();
        String previousSlug = workspace.getSlug();
        String previousDescription = workspace.getDescription();

        workspace.describe(request.name(), request.description(), request.slug());

        auditTrail.record(userId, AuditEntry
                .of(AuditAction.WORKSPACE_UPDATED, AuditTargetType.WORKSPACE)
                .target(workspace.getId(), workspace.getName())
                .inWorkspace(workspaceId)
                .changed("name", previousName, workspace.getName())
                .changed("slug", previousSlug, workspace.getSlug())
                .changed("description", previousDescription, workspace.getDescription()));

        return WorkspaceResponse.from(workspace, ref.callerRole());
    }

    @Transactional
    public void delete(UUID workspaceId, UUID userId) {
        workspaceAccess.requireAccess(workspaceId, userId, WorkspaceRole.OWNER);
        Workspace workspace = loadWorkspace(workspaceId);

        // Scoped like every other workspace event. Audit rows carry no foreign key
        // to workspaces, so this one survives the deletion it describes — it is
        // simply no longer reachable through the workspace's own endpoint, which
        // now 404s. Operators still see it in the instance log.
        auditTrail.record(userId, AuditEntry
                .of(AuditAction.WORKSPACE_DELETED, AuditTargetType.WORKSPACE)
                .target(workspace.getId(), workspace.getName())
                .inWorkspace(workspaceId)
                .with("slug", workspace.getSlug()));

        // Documents, boards, and memberships cascade at the database level.
        workspaceRepository.deleteById(workspaceId);
    }

    private Workspace loadWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace", workspaceId));
    }
}
