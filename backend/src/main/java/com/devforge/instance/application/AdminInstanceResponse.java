package com.devforge.instance.application;

import com.devforge.instance.domain.InstanceSettings;

import java.time.Instant;

/**
 * The full settings, for the operator who administers the instance.
 *
 * <p>Separate from {@link InstanceResponse} so that adding an operational setting
 * later cannot accidentally publish it to unauthenticated visitors.
 */
public record AdminInstanceResponse(
        InstanceResponse instance,
        String allowedEmailDomains,
        String publicBaseUrl,
        Instant setupCompletedAt
) {

    public static AdminInstanceResponse from(InstanceSettings settings) {
        return new AdminInstanceResponse(
                InstanceResponse.from(settings),
                settings.getAllowedEmailDomains(),
                settings.getPublicBaseUrl(),
                settings.getSetupCompletedAt()
        );
    }
}
