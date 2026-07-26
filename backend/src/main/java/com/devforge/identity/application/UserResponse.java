package com.devforge.identity.application;

import com.devforge.identity.contract.UserRef;

import java.util.UUID;

/**
 * What one user may see about another, which is only enough to pick them out of a
 * list. The signed-in user's own account is {@link CurrentUserResponse}.
 */
public record UserResponse(UUID id, String email, String displayName, String handle) {

    public static UserResponse from(UserRef user) {
        return new UserResponse(user.id(), user.email(), user.displayName(), user.handle());
    }
}
