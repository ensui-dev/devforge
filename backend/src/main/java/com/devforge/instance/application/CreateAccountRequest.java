package com.devforge.instance.application;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * An account created by the operator rather than by the person who will use it.
 *
 * <p>This is what makes {@code CLOSED} registration a usable setting instead of a
 * locked door: on a private instance the operator adds colleagues here.
 */
public record CreateAccountRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 120) String displayName,
        @NotBlank @Size(min = 8, max = 200, message = "must be at least 8 characters")
        String password,
        boolean instanceAdmin
) {
}
