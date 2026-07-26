package com.devforge.identity.application;

import com.devforge.identity.contract.UserRef;
import com.devforge.identity.domain.User;
import com.devforge.identity.domain.UserRepository;
import com.devforge.instance.contract.InstancePolicy;
import com.devforge.shared.exception.PermissionDeniedException;
import com.devforge.shared.exception.ResourceNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenIssuer accessTokenIssuer;
    private final AccountProvisioningService accountProvisioning;
    private final InstancePolicy instancePolicy;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokenIssuer,
            AccountProvisioningService accountProvisioning,
            InstancePolicy instancePolicy
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
        this.accountProvisioning = accountProvisioning;
        this.instancePolicy = instancePolicy;
    }

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());

        // Whether this instance accepts the registration at all is the operator's
        // decision, not identity's.
        if (!instancePolicy.registrationAllowedFor(email)) {
            throw new PermissionDeniedException(instancePolicy.registrationRefusalReason());
        }

        User user = accountProvisioning.create(
                email, request.displayName(), request.password(), false);
        return authenticationFor(user);
    }

    public AuthenticationResponse login(LoginRequest request) {
        String email = User.normalizeEmail(request.email());
        User user = userRepository.findByEmail(email)
                // Same error whether the address is unknown or the password is
                // wrong, so the response cannot be used to enumerate accounts.
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        return authenticationFor(user);
    }

    public CurrentUserResponse currentUser(UUID userId) {
        return CurrentUserResponse.from(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId)));
    }

    private AuthenticationResponse authenticationFor(User user) {
        UserRef ref = new UserRef(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getHandle());
        return AuthenticationResponse.of(accessTokenIssuer.issue(ref), CurrentUserResponse.from(user));
    }
}
