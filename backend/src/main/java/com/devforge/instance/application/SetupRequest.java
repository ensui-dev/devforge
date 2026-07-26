package com.devforge.instance.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * First run: the instance's configuration and the account that will administer it.
 *
 * @param admin becomes the first instance admin, and can never be created this way
 *              again — the endpoint closes once setup completes
 */
public record SetupRequest(
        @NotNull @Valid InstanceSettingsRequest instance,
        @NotNull @Valid AdminAccount admin
) {

    public record AdminAccount(
            @NotBlank @Email @Size(max = 320) String email,
            @NotBlank @Size(max = 120) String displayName,
            @NotBlank @Size(min = 8, max = 200, message = "must be at least 8 characters")
            String password
    ) {
    }
}
