package com.devforge.identity.application;

import com.devforge.identity.contract.UserRef;
import com.devforge.identity.domain.User;
import com.devforge.identity.domain.UserRepository;
import com.devforge.shared.exception.DuplicateResourceException;
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

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenIssuer accessTokenIssuer
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenIssuer = accessTokenIssuer;
    }

    @Transactional
    public AuthenticationResponse register(RegisterRequest request) {
        String email = User.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new DuplicateResourceException("An account already exists for " + email);
        }

        User user = userRepository.save(new User(
                email,
                request.displayName().trim(),
                passwordEncoder.encode(request.password())
        ));

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

    public UserResponse currentUser(UUID userId) {
        return UserResponse.from(userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId)));
    }

    private AuthenticationResponse authenticationFor(User user) {
        UserRef ref = new UserRef(user.getId(), user.getEmail(), user.getDisplayName());
        return AuthenticationResponse.of(accessTokenIssuer.issue(ref), UserResponse.from(ref));
    }
}
