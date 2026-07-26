package com.devforge.instance.application;

import com.devforge.instance.contract.RegistrationMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The configuration an operator chooses, whether at first run or later.
 *
 * @param logoImage   optional data URI, kept small enough to live in a row rather
 *                    than requiring object storage
 * @param accentColor six-digit hex; the interface derives its shades from it
 */
public record InstanceSettingsRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 200) String tagline,
        @Size(max = 8) String logoMark,
        @Size(max = 96_000, message = "must be smaller than about 64KB") String logoImage,
        @Pattern(regexp = "^$|^#[0-9a-fA-F]{6}$", message = "must be a hex colour such as #0e6b73")
        String accentColor,
        @NotNull RegistrationMode registrationMode,
        @Size(max = 1000) String allowedEmailDomains,
        @NotNull Boolean publicDocsEnabled,
        @Size(max = 210) String handbookPath,
        @Size(max = 200) String publicBaseUrl
) {
}
