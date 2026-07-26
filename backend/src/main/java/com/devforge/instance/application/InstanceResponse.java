package com.devforge.instance.application;

import com.devforge.instance.contract.RegistrationMode;
import com.devforge.instance.domain.InstanceSettings;

import java.util.List;

/**
 * What the client needs before anyone signs in: how to brand itself, whether the
 * instance has been set up, and whether registration is open.
 *
 * <p>Deliberately excludes anything an unauthenticated visitor has no business
 * knowing. Allowed domains are included because the registration form has to
 * explain a refusal before it happens.
 */
public record InstanceResponse(
        boolean configured,
        String name,
        String tagline,
        String logoMark,
        String logoImage,
        String accentColor,
        RegistrationMode registrationMode,
        List<String> allowedEmailDomains,
        boolean publicDocsEnabled,
        String handbookPath
) {

    public static InstanceResponse from(InstanceSettings settings) {
        return new InstanceResponse(
                settings.isConfigured(),
                settings.getName(),
                settings.getTagline(),
                settings.getLogoMark(),
                settings.getLogoImage(),
                settings.getAccentColor(),
                settings.getRegistrationMode(),
                settings.allowedDomains(),
                settings.isPublicDocsEnabled(),
                settings.getHandbookPath()
        );
    }

    /** Shown while an instance is still unconfigured, before any name exists. */
    public static InstanceResponse unconfigured() {
        return new InstanceResponse(
                false, "DevForge", null, "⌁", null, null,
                RegistrationMode.CLOSED, List.of(), false, null);
    }
}
