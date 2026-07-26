package com.devforge.identity.application;

import com.devforge.identity.domain.User;

import java.util.UUID;

/**
 * The signed-in user's own account.
 *
 * <p>Separate from {@link UserResponse}, which is what the member picker shows
 * about <em>other</em> people. Keeping them apart means a field added here — such
 * as {@code instanceAdmin} — cannot leak into a directory search that anyone can
 * call.
 */
public record CurrentUserResponse(
        UUID id,
        String email,
        String displayName,
        String handle,
        boolean instanceAdmin
) {

    public static CurrentUserResponse from(User user) {
        return new CurrentUserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getHandle(),
                user.isInstanceAdmin());
    }
}
