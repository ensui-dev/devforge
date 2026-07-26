package com.devforge.instance.application;

import com.devforge.identity.contract.UserRef;

import java.util.UUID;

/**
 * An account as the instance operator sees it.
 *
 * <p>The instance module cannot return identity's own response type — that would
 * couple the two modules' application layers — so it publishes its own shape built
 * from the {@link UserRef} contract.
 */
public record InstanceUserResponse(
        UUID id,
        String email,
        String displayName,
        String handle,
        boolean instanceAdmin
) {

    public static InstanceUserResponse from(UserRef user, boolean instanceAdmin) {
        return new InstanceUserResponse(
                user.id(), user.email(), user.displayName(), user.handle(), instanceAdmin);
    }
}
