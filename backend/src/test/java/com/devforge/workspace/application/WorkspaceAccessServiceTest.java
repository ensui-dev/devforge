package com.devforge.workspace.application;

import com.devforge.shared.exception.PermissionDeniedException;
import com.devforge.shared.exception.ResourceNotFoundException;
import com.devforge.workspace.contract.WorkspaceRef;
import com.devforge.workspace.contract.WorkspaceRole;
import com.devforge.workspace.domain.Workspace;
import com.devforge.workspace.domain.WorkspaceMember;
import com.devforge.workspace.domain.WorkspaceMemberRepository;
import com.devforge.workspace.domain.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Every workspace-scoped request funnels through this class, so its behaviour is
 * the system's authorisation contract.
 */
@ExtendWith(MockitoExtension.class)
class WorkspaceAccessServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository memberRepository;

    @Mock
    private com.devforge.identity.contract.UserDirectory userDirectory;

    @InjectMocks
    private WorkspaceAccessService workspaceAccessService;

    private UUID workspaceId;
    private UUID userId;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        workspaceId = UUID.randomUUID();
        userId = UUID.randomUUID();
        workspace = new Workspace("Platform", "Core services", "platform", userId);
    }

    @Test
    void grantsAccessAndReportsTheCallersRole() {
        givenMembership(WorkspaceRole.ADMIN);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        WorkspaceRef ref = workspaceAccessService.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER);

        assertThat(ref.callerRole()).isEqualTo(WorkspaceRole.ADMIN);
        assertThat(ref.slug()).isEqualTo("platform");
    }

    @Test
    void allowsAccessWhenTheRoleExactlyMeetsTheRequirement() {
        givenMembership(WorkspaceRole.MEMBER);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(workspace));

        assertThat(workspaceAccessService.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER)).isNotNull();
    }

    @Test
    void deniesAccessWhenTheRoleIsTooLow() {
        givenMembership(WorkspaceRole.VIEWER);

        assertThatThrownBy(() ->
                workspaceAccessService.requireAccess(workspaceId, userId, WorkspaceRole.MEMBER))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("MEMBER");
    }

    /**
     * The important security property: a non-member cannot tell an existing
     * workspace from a nonexistent one.
     */
    @Test
    void reportsNotFoundRatherThanForbiddenForNonMembers() {
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                workspaceAccessService.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void reportsNotFoundWhenMembershipOutlivesTheWorkspace() {
        givenMembership(WorkspaceRole.OWNER);
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                workspaceAccessService.requireAccess(workspaceId, userId, WorkspaceRole.VIEWER))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private void givenMembership(WorkspaceRole role) {
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, userId))
                .thenReturn(Optional.of(new WorkspaceMember(workspaceId, userId, role)));
    }
}
