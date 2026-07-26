package com.devforge.workspace.application;

import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditTargetType;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.identity.contract.UserDirectory;
import com.devforge.identity.contract.UserRef;
import com.devforge.shared.exception.DomainValidationException;
import com.devforge.shared.exception.DuplicateResourceException;
import com.devforge.shared.exception.PermissionDeniedException;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRef;
import com.devforge.workspace.contract.WorkspaceRole;
import com.devforge.workspace.domain.WorkspaceMember;
import com.devforge.workspace.domain.WorkspaceMemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Team management for one workspace.
 *
 * <p>Two invariants shape the rules here:
 * <ul>
 *   <li>A workspace always retains at least one {@code OWNER}, so a team can
 *       never lock itself out of its own data.</li>
 *   <li>An admin cannot grant authority they do not hold, nor act on someone
 *       ranked above them — otherwise {@code ADMIN} would be a silent
 *       privilege-escalation path to {@code OWNER}.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class MembershipService {

    private final WorkspaceMemberRepository memberRepository;
    private final WorkspaceAccess workspaceAccess;
    private final UserDirectory userDirectory;
    private final AuditTrail auditTrail;

    public MembershipService(
            WorkspaceMemberRepository memberRepository,
            WorkspaceAccess workspaceAccess,
            UserDirectory userDirectory,
            AuditTrail auditTrail
    ) {
        this.memberRepository = memberRepository;
        this.workspaceAccess = workspaceAccess;
        this.userDirectory = userDirectory;
        this.auditTrail = auditTrail;
    }

    public List<MemberResponse> findMembers(UUID workspaceId, UUID actorId) {
        workspaceAccess.requireAccess(workspaceId, actorId, WorkspaceRole.VIEWER);

        List<WorkspaceMember> members = memberRepository.findByWorkspaceIdOrderByRoleAscCreatedAtAsc(workspaceId);
        // Resolve every display name in one call rather than per member.
        Map<UUID, UserRef> users = userDirectory.findAllByIds(
                members.stream().map(WorkspaceMember::getUserId).toList());

        return members.stream()
                .map(member -> MemberResponse.of(member, users.get(member.getUserId())))
                .toList();
    }

    @Transactional
    public MemberResponse addMember(UUID workspaceId, AddMemberRequest request, UUID actorId) {
        WorkspaceRef workspace = workspaceAccess.requireAccess(workspaceId, actorId, WorkspaceRole.ADMIN);
        requireCanGrant(workspace.callerRole(), request.role());

        UserRef invitee = userDirectory.findByEmail(request.email())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.email()));

        if (memberRepository.existsByWorkspaceIdAndUserId(workspaceId, invitee.id())) {
            throw new DuplicateResourceException(
                    "%s is already a member of this workspace".formatted(invitee.email()));
        }

        WorkspaceMember member = memberRepository.save(
                new WorkspaceMember(workspaceId, invitee.id(), request.role()));

        auditTrail.record(actorId, AuditEntry
                .of(AuditAction.MEMBER_ADDED, AuditTargetType.MEMBER)
                .target(invitee.id(), invitee.email())
                .inWorkspace(workspaceId)
                .with("role", request.role()));

        return MemberResponse.of(member, invitee);
    }

    @Transactional
    public MemberResponse changeRole(
            UUID workspaceId,
            UUID targetUserId,
            UpdateMemberRoleRequest request,
            UUID actorId
    ) {
        WorkspaceRef workspace = workspaceAccess.requireAccess(workspaceId, actorId, WorkspaceRole.ADMIN);
        WorkspaceMember member = loadMember(workspaceId, targetUserId);

        requireCanActOn(workspace.callerRole(), member.getRole(), actorId, targetUserId);
        requireCanGrant(workspace.callerRole(), request.role());

        if (member.getRole() == WorkspaceRole.OWNER && request.role() != WorkspaceRole.OWNER) {
            requireAnotherOwnerRemains(workspaceId);
        }

        WorkspaceRole previousRole = member.getRole();
        member.changeRole(request.role());

        UserRef target = userDirectory.findById(targetUserId).orElse(null);
        auditTrail.record(actorId, AuditEntry
                .of(AuditAction.MEMBER_ROLE_CHANGED, AuditTargetType.MEMBER)
                .target(targetUserId, target == null ? null : target.email())
                .inWorkspace(workspaceId)
                .changed("role", previousRole, request.role()));

        return MemberResponse.of(member, target);
    }

    @Transactional
    public void removeMember(UUID workspaceId, UUID targetUserId, UUID actorId) {
        boolean leavingVoluntarily = actorId.equals(targetUserId);

        // Anyone may leave a workspace; removing someone else needs ADMIN.
        WorkspaceRef workspace = workspaceAccess.requireAccess(
                workspaceId,
                actorId,
                leavingVoluntarily ? WorkspaceRole.VIEWER : WorkspaceRole.ADMIN);

        WorkspaceMember member = loadMember(workspaceId, targetUserId);

        if (!leavingVoluntarily) {
            requireCanActOn(workspace.callerRole(), member.getRole(), actorId, targetUserId);
        }
        if (member.getRole() == WorkspaceRole.OWNER) {
            requireAnotherOwnerRemains(workspaceId);
        }

        UserRef target = userDirectory.findById(targetUserId).orElse(null);
        auditTrail.record(actorId, AuditEntry
                .of(AuditAction.MEMBER_REMOVED, AuditTargetType.MEMBER)
                .target(targetUserId, target == null ? null : target.email())
                .inWorkspace(workspaceId)
                .with("role", member.getRole())
                // Leaving and being removed read very differently in a log.
                .with("voluntary", leavingVoluntarily));

        memberRepository.delete(member);
    }

    private WorkspaceMember loadMember(UUID workspaceId, UUID userId) {
        return memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace member", userId));
    }

    /** Nobody may hand out a role above their own. */
    private static void requireCanGrant(WorkspaceRole actorRole, WorkspaceRole grantedRole) {
        if (!actorRole.atLeast(grantedRole)) {
            throw new PermissionDeniedException(
                    "You cannot grant the %s role because you hold %s".formatted(grantedRole, actorRole));
        }
    }

    /** Nobody may re-role or remove a member ranked above them. */
    private static void requireCanActOn(
            WorkspaceRole actorRole,
            WorkspaceRole targetRole,
            UUID actorId,
            UUID targetUserId
    ) {
        if (!actorId.equals(targetUserId) && !actorRole.atLeast(targetRole)) {
            throw new PermissionDeniedException(
                    "You cannot modify a member who holds the %s role".formatted(targetRole));
        }
    }

    private void requireAnotherOwnerRemains(UUID workspaceId) {
        if (memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER) <= 1) {
            throw new DomainValidationException(
                    "A workspace must keep at least one owner. Promote another member first.");
        }
    }
}
