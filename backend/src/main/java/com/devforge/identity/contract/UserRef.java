package com.devforge.identity.contract;

import java.util.UUID;

/**
 * The identity module's public view of a user.
 *
 * <p>Other modules receive this record instead of the {@code User} entity, so no
 * module outside {@code identity} can navigate or mutate identity state — and the
 * password hash can never leak into another module's response.
 */
public record UserRef(UUID id, String email, String displayName) {
}
