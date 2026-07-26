package com.devforge.workspace.application;

import com.devforge.audit.contract.AuditEntry;
import com.devforge.audit.contract.AuditAction;
import com.devforge.audit.contract.AuditTrail;
import com.devforge.shared.exception.DuplicateResourceException;
import com.devforge.workspace.contract.WorkspaceAccess;
import com.devforge.workspace.contract.WorkspaceRef;
import com.devforge.workspace.contract.WorkspaceRole;
import com.devforge.workspace.domain.Workspace;
import com.devforge.workspace.domain.WorkspaceMember;
import com.devforge.workspace.domain.WorkspaceMemberRepository;
import com.devforge.workspace.domain.WorkspaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkspaceServiceTest {

    @Mock
    private WorkspaceRepository workspaceRepository;

    @Mock
    private WorkspaceMemberRepository memberRepository;

    @Mock
    private WorkspaceAccess workspaceAccess;

    @Mock
    private AuditTrail auditTrail;

    @InjectMocks
    private WorkspaceService workspaceService;

    private UUID userId;
    private Workspace workspace;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        workspace = new Workspace("Platform", "Core services", "platform", userId);
    }

    @Test
    void listsOnlyWorkspacesTheUserBelongsTo() {
        when(memberRepository.findByUserId(userId)).thenReturn(List.of(
                new WorkspaceMember(workspace.getId(), userId, WorkspaceRole.ADMIN)));
        when(workspaceRepository.findAllById(any())).thenReturn(List.of(workspace));

        List<WorkspaceResponse> responses = workspaceService.findAllForUser(userId);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().name()).isEqualTo("Platform");
        assertThat(responses.getFirst().callerRole()).isEqualTo(WorkspaceRole.ADMIN);
    }

    @Test
    void returnsNothingAndQueriesNoWorkspacesWhenUserHasNoMemberships() {
        when(memberRepository.findByUserId(userId)).thenReturn(List.of());

        assertThat(workspaceService.findAllForUser(userId)).isEmpty();

        verify(workspaceRepository, never()).findAllById(any());
    }

    @Test
    void sortsWorkspacesByNameCaseInsensitively() {
        Workspace zebra = new Workspace("zebra", null, "zebra", userId);
        Workspace apple = new Workspace("Apple", null, "apple", userId);
        when(memberRepository.findByUserId(userId)).thenReturn(List.of(
                new WorkspaceMember(zebra.getId(), userId, WorkspaceRole.MEMBER),
                new WorkspaceMember(apple.getId(), userId, WorkspaceRole.MEMBER)));
        when(workspaceRepository.findAllById(any())).thenReturn(List.of(zebra, apple));

        assertThat(workspaceService.findAllForUser(userId))
                .extracting(WorkspaceResponse::name)
                .containsExactly("Apple", "zebra");
    }

    @Test
    void enrolsTheCreatorAsOwner() {
        when(workspaceRepository.existsByOwnerUserIdAndSlug(userId, "platform")).thenReturn(false);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(call -> call.getArgument(0));

        WorkspaceResponse response = workspaceService.create(
                new CreateWorkspaceRequest("Platform", "Core services", "platform"), userId);

        assertThat(response.callerRole()).isEqualTo(WorkspaceRole.OWNER);

        ArgumentCaptor<WorkspaceMember> captor = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(memberRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(userId);
        assertThat(captor.getValue().getRole()).isEqualTo(WorkspaceRole.OWNER);
    }

    @Test
    void rejectsADuplicateSlugOnCreate() {
        when(workspaceRepository.existsByOwnerUserIdAndSlug(userId, "platform")).thenReturn(true);

        assertThatThrownBy(() -> workspaceService.create(
                new CreateWorkspaceRequest("Platform", null, "platform"), userId))
                .isInstanceOf(DuplicateResourceException.class);

        verify(memberRepository, never()).save(any());
    }

    @Test
    void updateRequiresAdminAndAppliesChanges() {
        givenAccess(WorkspaceRole.ADMIN);
        when(workspaceRepository.findById(workspace.getId())).thenReturn(Optional.of(workspace));

        WorkspaceResponse response = workspaceService.update(
                workspace.getId(),
                new UpdateWorkspaceRequest("Renamed", "New description", "renamed"),
                userId);

        assertThat(response.name()).isEqualTo("Renamed");
        assertThat(response.slug()).isEqualTo("renamed");
        verify(workspaceAccess).requireAccess(workspace.getId(), userId, WorkspaceRole.ADMIN);
    }

    @Test
    void allowsKeepingTheSameSlugOnUpdate() {
        givenAccess(WorkspaceRole.OWNER);
        when(workspaceRepository.findById(workspace.getId())).thenReturn(Optional.of(workspace));

        WorkspaceResponse response = workspaceService.update(
                workspace.getId(),
                new UpdateWorkspaceRequest("Platform Renamed", null, "platform"),
                userId);

        assertThat(response.slug()).isEqualTo("platform");
        verify(workspaceRepository, never()).existsByOwnerUserIdAndSlug(any(), any());
    }

    @Test
    void rejectsTakingAnotherWorkspacesSlug() {
        givenAccess(WorkspaceRole.ADMIN);
        when(workspaceRepository.findById(workspace.getId())).thenReturn(Optional.of(workspace));
        when(workspaceRepository.existsByOwnerUserIdAndSlug(userId, "taken")).thenReturn(true);

        assertThatThrownBy(() -> workspaceService.update(
                workspace.getId(),
                new UpdateWorkspaceRequest("Platform", null, "taken"),
                userId))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    void deleteRequiresOwnership() {
        givenAccess(WorkspaceRole.OWNER);
        // Deleting now loads the workspace first, to record what it was called:
        // an audit entry naming only a UUID would defeat the point of keeping one.
        when(workspaceRepository.findById(workspace.getId())).thenReturn(Optional.of(workspace));

        workspaceService.delete(workspace.getId(), userId);

        verify(workspaceAccess).requireAccess(workspace.getId(), userId, WorkspaceRole.OWNER);
        verify(workspaceRepository).deleteById(workspace.getId());
    }

    /**
     * The deletion entry must outlive the workspace. Audit rows carry no foreign
     * key to workspaces precisely so that deleting one cannot delete the evidence
     * that it happened.
     */
    @Test
    void recordsWhoDeletedTheWorkspaceAndWhatItWasCalled() {
        givenAccess(WorkspaceRole.OWNER);
        when(workspaceRepository.findById(workspace.getId())).thenReturn(Optional.of(workspace));

        workspaceService.delete(workspace.getId(), userId);

        ArgumentCaptor<AuditEntry> entry = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditTrail).record(org.mockito.ArgumentMatchers.eq(userId), entry.capture());
        assertThat(entry.getValue().action()).isEqualTo(AuditAction.WORKSPACE_DELETED);
        assertThat(entry.getValue().targetLabel()).isEqualTo(workspace.getName());
        assertThat(entry.getValue().workspaceId()).isEqualTo(workspace.getId());
    }

    private void givenAccess(WorkspaceRole role) {
        when(workspaceAccess.requireAccess(any(), any(), any()))
                .thenReturn(new WorkspaceRef(workspace.getId(), workspace.getName(), workspace.getSlug(), role));
    }
}
