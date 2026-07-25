package com.devforge.workspace.api;

import com.devforge.shared.security.CurrentUser;
import com.devforge.workspace.application.AddMemberRequest;
import com.devforge.workspace.application.MemberResponse;
import com.devforge.workspace.application.MembershipService;
import com.devforge.workspace.application.UpdateMemberRoleRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/workspaces/{workspaceId}/members")
@Tag(name = "Workspace members")
public class WorkspaceMemberController {

    private final MembershipService membershipService;

    public WorkspaceMemberController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping
    @Operation(summary = "List the workspace team")
    public List<MemberResponse> list(@PathVariable UUID workspaceId, @CurrentUser UUID userId) {
        return membershipService.findMembers(workspaceId, userId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add an existing user to the workspace by email (ADMIN)")
    public MemberResponse add(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody AddMemberRequest request,
            @CurrentUser UUID userId
    ) {
        return membershipService.addMember(workspaceId, request, userId);
    }

    @PutMapping("/{memberUserId}")
    @Operation(summary = "Change a member's role (ADMIN)")
    public MemberResponse changeRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberUserId,
            @Valid @RequestBody UpdateMemberRoleRequest request,
            @CurrentUser UUID userId
    ) {
        return membershipService.changeRole(workspaceId, memberUserId, request, userId);
    }

    @DeleteMapping("/{memberUserId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove a member, or leave the workspace yourself")
    public void remove(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberUserId,
            @CurrentUser UUID userId
    ) {
        membershipService.removeMember(workspaceId, memberUserId, userId);
    }
}
