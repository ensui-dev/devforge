package com.devforge.workspace.application;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Membership rules protect against two failure modes: a team locking itself out,
 * and an admin quietly escalating to owner.
 */
@ExtendWith(MockitoExtension.class)
class MembershipServiceTest {

    @Mock
    private WorkspaceMemberRepository memberRepository;

    @Mock
    private WorkspaceAccess workspaceAccess;

    @Mock
    private UserDirectory userDirectory;

    @Mock
    private AuditTrail auditTrail;

    @InjectMocks
    private MembershipService membershipService;

    private UUID workspaceId;
    private UUID actorId;
    private UUID targetId;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        targetId = UUID.randomUUID();
    }

    @Test
    void listsMembersWithTheirDisplayNames() {
        givenAccess(WorkspaceRole.VIEWER);
        WorkspaceMember member = new WorkspaceMember(workspaceId, targetId, WorkspaceRole.MEMBER);
        when(memberRepository.findByWorkspaceIdOrderByRoleAscCreatedAtAsc(workspaceId))
                .thenReturn(List.of(member));
        when(userDirectory.findAllByIds(List.of(targetId)))
                .thenReturn(Map.of(targetId, new UserRef(targetId, "dev@example.com", "Dev", "handle")));

        List<MemberResponse> members = membershipService.findMembers(workspaceId, actorId);

        assertThat(members).hasSize(1);
        assertThat(members.getFirst().displayName()).isEqualTo("Dev");
        assertThat(members.getFirst().email()).isEqualTo("dev@example.com");
    }

    @Test
    void stillListsAMembershipWhoseAccountVanished() {
        givenAccess(WorkspaceRole.VIEWER);
        when(memberRepository.findByWorkspaceIdOrderByRoleAscCreatedAtAsc(workspaceId))
                .thenReturn(List.of(new WorkspaceMember(workspaceId, targetId, WorkspaceRole.MEMBER)));
        when(userDirectory.findAllByIds(any())).thenReturn(Map.of());

        assertThat(membershipService.findMembers(workspaceId, actorId).getFirst().displayName())
                .isEqualTo("Unknown user");
    }

    @Test
    void addsAMemberByEmail() {
        givenAccess(WorkspaceRole.ADMIN);
        when(userDirectory.findByEmail("new@example.com"))
                .thenReturn(Optional.of(new UserRef(targetId, "new@example.com", "New Dev", "handle")));
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetId)).thenReturn(false);
        when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(call -> call.getArgument(0));

        MemberResponse response = membershipService.addMember(
                workspaceId, new AddMemberRequest("new@example.com", WorkspaceRole.MEMBER), actorId);

        assertThat(response.role()).isEqualTo(WorkspaceRole.MEMBER);
        assertThat(response.userId()).isEqualTo(targetId);
    }

    @Test
    void rejectsAddingAnUnknownEmail() {
        givenAccess(WorkspaceRole.ADMIN);
        when(userDirectory.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.addMember(
                workspaceId, new AddMemberRequest("ghost@example.com", WorkspaceRole.MEMBER), actorId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsAddingSomeoneTwice() {
        givenAccess(WorkspaceRole.ADMIN);
        when(userDirectory.findByEmail("dup@example.com"))
                .thenReturn(Optional.of(new UserRef(targetId, "dup@example.com", "Dup", "handle")));
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetId)).thenReturn(true);

        assertThatThrownBy(() -> membershipService.addMember(
                workspaceId, new AddMemberRequest("dup@example.com", WorkspaceRole.MEMBER), actorId))
                .isInstanceOf(DuplicateResourceException.class);
    }

    /** An admin must not be able to mint an owner — that would be escalation. */
    @Test
    void adminCannotGrantOwner() {
        givenAccess(WorkspaceRole.ADMIN);

        assertThatThrownBy(() -> membershipService.addMember(
                workspaceId, new AddMemberRequest("new@example.com", WorkspaceRole.OWNER), actorId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("cannot grant the OWNER role");

        verify(memberRepository, never()).save(any());
    }

    @Test
    void ownerCanGrantOwner() {
        givenAccess(WorkspaceRole.OWNER);
        when(userDirectory.findByEmail("co@example.com"))
                .thenReturn(Optional.of(new UserRef(targetId, "co@example.com", "Co Owner", "handle")));
        when(memberRepository.existsByWorkspaceIdAndUserId(workspaceId, targetId)).thenReturn(false);
        when(memberRepository.save(any(WorkspaceMember.class))).thenAnswer(call -> call.getArgument(0));

        assertThat(membershipService.addMember(
                workspaceId, new AddMemberRequest("co@example.com", WorkspaceRole.OWNER), actorId).role())
                .isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    void adminCannotDemoteAnOwner() {
        givenAccess(WorkspaceRole.ADMIN);
        givenTargetMembership(WorkspaceRole.OWNER);

        assertThatThrownBy(() -> membershipService.changeRole(
                workspaceId, targetId, new UpdateMemberRoleRequest(WorkspaceRole.VIEWER), actorId))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("holds the OWNER role");
    }

    @Test
    void changesRoleWhenPermitted() {
        givenAccess(WorkspaceRole.OWNER);
        WorkspaceMember member = givenTargetMembership(WorkspaceRole.VIEWER);
        when(userDirectory.findById(targetId))
                .thenReturn(Optional.of(new UserRef(targetId, "dev@example.com", "Dev", "handle")));

        MemberResponse response = membershipService.changeRole(
                workspaceId, targetId, new UpdateMemberRoleRequest(WorkspaceRole.MEMBER), actorId);

        assertThat(response.role()).isEqualTo(WorkspaceRole.MEMBER);
        assertThat(member.getRole()).isEqualTo(WorkspaceRole.MEMBER);
    }

    /** The lock-out guard: the final owner cannot step down. */
    @Test
    void refusesToDemoteTheLastOwner() {
        givenAccess(WorkspaceRole.OWNER);
        givenTargetMembership(WorkspaceRole.OWNER);
        when(memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER)).thenReturn(1);

        assertThatThrownBy(() -> membershipService.changeRole(
                workspaceId, targetId, new UpdateMemberRoleRequest(WorkspaceRole.ADMIN), actorId))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("at least one owner");
    }

    @Test
    void allowsDemotingAnOwnerWhenAnotherRemains() {
        givenAccess(WorkspaceRole.OWNER);
        WorkspaceMember member = givenTargetMembership(WorkspaceRole.OWNER);
        when(memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER)).thenReturn(2);
        when(userDirectory.findById(targetId)).thenReturn(Optional.empty());

        membershipService.changeRole(
                workspaceId, targetId, new UpdateMemberRoleRequest(WorkspaceRole.ADMIN), actorId);

        assertThat(member.getRole()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    void refusesToRemoveTheLastOwner() {
        givenAccess(WorkspaceRole.OWNER);
        givenTargetMembership(WorkspaceRole.OWNER);
        when(memberRepository.countByWorkspaceIdAndRole(workspaceId, WorkspaceRole.OWNER)).thenReturn(1);

        assertThatThrownBy(() -> membershipService.removeMember(workspaceId, targetId, actorId))
                .isInstanceOf(DomainValidationException.class);

        verify(memberRepository, never()).delete(any());
    }

    @Test
    void removesAMemberWhenPermitted() {
        givenAccess(WorkspaceRole.ADMIN);
        WorkspaceMember member = givenTargetMembership(WorkspaceRole.MEMBER);

        membershipService.removeMember(workspaceId, targetId, actorId);

        verify(memberRepository).delete(member);
    }

    /** Leaving is self-service: it needs only VIEWER, not ADMIN. */
    @Test
    void aViewerMayLeaveOnTheirOwn() {
        when(workspaceAccess.requireAccess(workspaceId, actorId, WorkspaceRole.VIEWER))
                .thenReturn(new WorkspaceRef(workspaceId, "Platform", "platform", WorkspaceRole.VIEWER));
        WorkspaceMember member = new WorkspaceMember(workspaceId, actorId, WorkspaceRole.VIEWER);
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, actorId)).thenReturn(Optional.of(member));

        membershipService.removeMember(workspaceId, actorId, actorId);

        verify(memberRepository).delete(member);
        verify(workspaceAccess, never()).requireAccess(workspaceId, actorId, WorkspaceRole.ADMIN);
    }

    @Test
    void reportsNotFoundForANonMemberTarget() {
        givenAccess(WorkspaceRole.ADMIN);
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> membershipService.removeMember(workspaceId, targetId, actorId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void givenAccess(WorkspaceRole role) {
        when(workspaceAccess.requireAccess(any(), any(), any()))
                .thenReturn(new WorkspaceRef(workspaceId, "Platform", "platform", role));
    }

    private WorkspaceMember givenTargetMembership(WorkspaceRole role) {
        WorkspaceMember member = new WorkspaceMember(workspaceId, targetId, role);
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, targetId)).thenReturn(Optional.of(member));
        return member;
    }
}
