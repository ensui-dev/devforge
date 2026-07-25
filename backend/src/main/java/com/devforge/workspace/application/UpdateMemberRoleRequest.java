package com.devforge.workspace.application;

import com.devforge.workspace.contract.WorkspaceRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(@NotNull WorkspaceRole role) {
}
