package com.devforge.instance.application;

import com.devforge.instance.contract.InstancePolicy;
import com.devforge.instance.contract.RegistrationMode;
import com.devforge.instance.domain.InstanceSettings;
import com.devforge.instance.domain.InstanceSettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Answers policy questions for the modules that must honour them.
 *
 * <p>Depends only on this module's own repository. That matters: the identity
 * module consults this while this module's <em>setup</em> service calls identity to
 * create the first admin. Keeping policy and setup as separate beans means those
 * two paths never form a cycle.
 */
@Service
@Transactional(readOnly = true)
public class InstancePolicyService implements InstancePolicy {

    private final InstanceSettingsRepository repository;

    public InstancePolicyService(InstanceSettingsRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean registrationAllowedFor(String email) {
        return settings()
                // An unconfigured instance accepts no registrations: the setup
                // wizard creates the only account until it has run.
                .filter(InstanceSettings::isConfigured)
                .map(current -> current.permitsRegistrationOf(email))
                .orElse(false);
    }

    @Override
    public String registrationRefusalReason() {
        Optional<InstanceSettings> current = settings().filter(InstanceSettings::isConfigured);
        if (current.isEmpty()) {
            return "This instance has not been set up yet.";
        }
        InstanceSettings settings = current.get();
        if (settings.getRegistrationMode() == RegistrationMode.CLOSED) {
            return "This instance is not accepting new accounts. Ask an administrator to create one.";
        }
        return "Accounts on this instance are limited to: " + String.join(", ", settings.allowedDomains());
    }

    @Override
    public boolean publicDocumentationEnabled() {
        return settings().map(InstanceSettings::isPublicDocsEnabled).orElse(false);
    }

    @Override
    public String handbookPath() {
        return settings().map(InstanceSettings::getHandbookPath).orElse("");
    }

    @Override
    public boolean isConfigured() {
        return settings().map(InstanceSettings::isConfigured).orElse(false);
    }

    private Optional<InstanceSettings> settings() {
        return repository.findById(InstanceSettings.KEY);
    }
}
