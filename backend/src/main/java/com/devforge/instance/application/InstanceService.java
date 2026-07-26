package com.devforge.instance.application;

import com.devforge.identity.contract.AccountProvisioning;
import com.devforge.identity.contract.UserDirectory;
import com.devforge.identity.contract.UserRef;
import com.devforge.instance.domain.InstanceSettings;
import com.devforge.instance.domain.InstanceSettingsRepository;
import com.devforge.shared.exception.DomainValidationException;
import com.devforge.shared.exception.PermissionDeniedException;
import com.devforge.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * First-run setup, and the settings screen that follows it.
 *
 * <p>The security property that matters here: {@link #setUp} works only while the
 * instance is unconfigured. Once it completes it refuses forever, so a deployment
 * left briefly exposed cannot be claimed by whoever reaches it second — and an
 * attacker cannot use it to mint an administrator on a running instance.
 */
@Service
@Transactional(readOnly = true)
public class InstanceService {

    private final InstanceSettingsRepository repository;
    private final AccountProvisioning accountProvisioning;
    private final UserDirectory userDirectory;

    public InstanceService(
            InstanceSettingsRepository repository,
            AccountProvisioning accountProvisioning,
            UserDirectory userDirectory
    ) {
        this.repository = repository;
        this.accountProvisioning = accountProvisioning;
        this.userDirectory = userDirectory;
    }

    /** What an unauthenticated visitor is told: branding, and whether to show setup. */
    public InstanceResponse describe() {
        return repository.findById(InstanceSettings.KEY)
                .filter(InstanceSettings::isConfigured)
                .map(InstanceResponse::from)
                .orElseGet(InstanceResponse::unconfigured);
    }

    public AdminInstanceResponse describeForAdmin(UUID userId) {
        requireInstanceAdmin(userId);
        return AdminInstanceResponse.from(current());
    }

    /**
     * Configures the instance and creates its first administrator.
     *
     * @throws DomainValidationException once setup has already completed
     */
    @Transactional
    public SetupResponse setUp(SetupRequest request) {
        InstanceSettings settings = current();
        if (settings.isConfigured()) {
            throw new DomainValidationException(
                    "This instance has already been set up. Sign in instead.");
        }

        apply(settings, request.instance());

        UserRef admin = accountProvisioning.createAccount(
                request.admin().email(),
                request.admin().displayName(),
                request.admin().password(),
                true);

        // Recorded last, so a failure anywhere above leaves setup still open.
        settings.completeSetup();

        return new SetupResponse(InstanceResponse.from(settings), admin.email());
    }

    @Transactional
    public AdminInstanceResponse update(InstanceSettingsRequest request, UUID userId) {
        requireInstanceAdmin(userId);
        InstanceSettings settings = current();
        apply(settings, request);
        return AdminInstanceResponse.from(settings);
    }

    /**
     * Creates an account without consulting the registration mode.
     *
     * <p>An operator running a {@code CLOSED} instance has no other way to add
     * people, so this path deliberately bypasses the policy that governs
     * self-registration.
     */
    @Transactional
    public InstanceUserResponse createAccount(CreateAccountRequest request, UUID actorId) {
        requireInstanceAdmin(actorId);
        UserRef created = accountProvisioning.createAccount(
                request.email(), request.displayName(), request.password(), request.instanceAdmin());
        return InstanceUserResponse.from(created, request.instanceAdmin());
    }

    /** The operators of this instance, so the screen can show who else holds the keys. */
    public List<InstanceUserResponse> administrators(UUID actorId) {
        requireInstanceAdmin(actorId);
        return userDirectory.instanceAdmins().stream()
                .map(user -> InstanceUserResponse.from(user, true))
                .toList();
    }

    @Transactional
    public InstanceUserResponse setInstanceAdmin(UUID userId, boolean instanceAdmin, UUID actorId) {
        requireInstanceAdmin(actorId);
        return InstanceUserResponse.from(
                accountProvisioning.setInstanceAdmin(userId, instanceAdmin), instanceAdmin);
    }

    private void apply(InstanceSettings settings, InstanceSettingsRequest request) {
        if (request.registrationMode() == com.devforge.instance.contract.RegistrationMode.RESTRICTED
                && (request.allowedEmailDomains() == null
                    || request.allowedEmailDomains().isBlank())) {
            throw new DomainValidationException(
                    "List at least one email domain, or choose a different registration mode.");
        }

        settings.configure(
                request.name().trim(),
                request.tagline() == null || request.tagline().isBlank()
                        ? null : request.tagline().trim(),
                request.logoMark(),
                request.logoImage() == null || request.logoImage().isBlank()
                        ? null : request.logoImage(),
                request.accentColor(),
                request.registrationMode(),
                request.allowedEmailDomains(),
                Boolean.TRUE.equals(request.publicDocsEnabled()),
                request.handbookPath(),
                request.publicBaseUrl());
    }

    private void requireInstanceAdmin(UUID userId) {
        if (!userDirectory.isInstanceAdmin(userId)) {
            throw new PermissionDeniedException(
                    "Only an instance administrator can change these settings.");
        }
    }

    private InstanceSettings current() {
        return repository.findById(InstanceSettings.KEY)
                .orElseThrow(() -> new ResourceNotFoundException("Instance settings", "singleton"));
    }
}
