package com.devforge.workspace.application;

import com.devforge.workspace.contract.WorkspaceRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Members are added by email address, so the inviter needs no internal user id. */
public record AddMemberRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotNull WorkspaceRole role
) {
}
