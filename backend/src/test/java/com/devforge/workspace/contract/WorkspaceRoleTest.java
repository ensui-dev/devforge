package com.devforge.workspace.contract;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WorkspaceRoleTest {

    @Test
    void ownerOutranksEveryone() {
        assertThat(WorkspaceRole.OWNER.atLeast(WorkspaceRole.OWNER)).isTrue();
        assertThat(WorkspaceRole.OWNER.atLeast(WorkspaceRole.ADMIN)).isTrue();
        assertThat(WorkspaceRole.OWNER.atLeast(WorkspaceRole.MEMBER)).isTrue();
        assertThat(WorkspaceRole.OWNER.atLeast(WorkspaceRole.VIEWER)).isTrue();
    }

    @Test
    void viewerHoldsOnlyItsOwnAuthority() {
        assertThat(WorkspaceRole.VIEWER.atLeast(WorkspaceRole.VIEWER)).isTrue();
        assertThat(WorkspaceRole.VIEWER.atLeast(WorkspaceRole.MEMBER)).isFalse();
        assertThat(WorkspaceRole.VIEWER.atLeast(WorkspaceRole.ADMIN)).isFalse();
        assertThat(WorkspaceRole.VIEWER.atLeast(WorkspaceRole.OWNER)).isFalse();
    }

    @Test
    void memberCanWriteButNotAdminister() {
        assertThat(WorkspaceRole.MEMBER.atLeast(WorkspaceRole.MEMBER)).isTrue();
        assertThat(WorkspaceRole.MEMBER.atLeast(WorkspaceRole.ADMIN)).isFalse();
    }

    @Test
    void adminCanAdministerButNotOwn() {
        assertThat(WorkspaceRole.ADMIN.atLeast(WorkspaceRole.MEMBER)).isTrue();
        assertThat(WorkspaceRole.ADMIN.atLeast(WorkspaceRole.ADMIN)).isTrue();
        assertThat(WorkspaceRole.ADMIN.atLeast(WorkspaceRole.OWNER)).isFalse();
    }
}
