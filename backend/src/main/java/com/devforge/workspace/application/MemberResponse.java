package com.devforge.workspace.application;

import com.devforge.identity.contract.UserRef;
import com.devforge.workspace.contract.WorkspaceRole;
import com.devforge.workspace.domain.WorkspaceMember;

import java.time.Instant;
import java.util.UUID;

public record MemberResponse(
        UUID userId,
        String email,
        String displayName,
        WorkspaceRole role,
        Instant joinedAt
) {

    /**
     * @param user may be {@code null} if the account was removed concurrently;
     *             the membership row is still reported so the UI can show and
     *             clean up the orphan rather than silently dropping it
     */
    public static MemberResponse of(WorkspaceMember member, UserRef user) {
        return new MemberResponse(
                member.getUserId(),
                user == null ? null : user.email(),
                user == null ? "Unknown user" : user.displayName(),
                member.getRole(),
                member.getCreatedAt()
        );
    }
}
