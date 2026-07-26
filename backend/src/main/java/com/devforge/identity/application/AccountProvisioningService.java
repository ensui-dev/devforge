package com.devforge.identity.application;

import com.devforge.identity.contract.AccountProvisioning;
import com.devforge.identity.contract.UserRef;
import com.devforge.identity.domain.User;
import com.devforge.identity.domain.UserRepository;
import com.devforge.shared.exception.DomainValidationException;
import com.devforge.shared.exception.DuplicateResourceException;
import com.devforge.shared.exception.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account creation, shared by self-registration and by the operator flows that
 * create accounts on someone's behalf.
 *
 * <p>Depends only on identity's own persistence, so it cannot form a bean cycle
 * with the instance module that calls it.
 */
@Service
@Transactional
public class AccountProvisioningService implements AccountProvisioning {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AccountProvisioningService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserRef createAccount(
            String email,
            String displayName,
            String password,
            boolean instanceAdmin
    ) {
        return toRef(create(email, displayName, password, instanceAdmin));
    }

    @Override
    public UserRef setInstanceAdmin(java.util.UUID userId, boolean instanceAdmin) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!instanceAdmin && user.isInstanceAdmin()
                && userRepository.countByInstanceAdminTrue() <= 1) {
            throw new DomainValidationException(
                    "This is the only instance administrator. Appoint another one first.");
        }

        if (instanceAdmin) {
            user.grantInstanceAdmin();
        } else {
            user.revokeInstanceAdmin();
        }
        return toRef(userRepository.save(user));
    }

    /** Returns the entity, for callers inside this module that need it. */
    User create(String email, String displayName, String password, boolean instanceAdmin) {
        String normalized = User.normalizeEmail(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new DuplicateResourceException("An account already exists for " + normalized);
        }

        User user = new User(
                normalized,
                displayName.trim(),
                allocateHandle(User.suggestHandle(normalized)),
                passwordEncoder.encode(password)
        );
        if (instanceAdmin) {
            user.grantInstanceAdmin();
        }
        return userRepository.save(user);
    }

    /**
     * Finds a free handle, suffixing on collision.
     *
     * <p>Bounded rather than looping forever: after a hundred collisions on one
     * base something is wrong, and failing loudly beats spinning.
     */
    private String allocateHandle(String base) {
        if (!userRepository.existsByHandle(base)) {
            return base;
        }
        for (int suffix = 2; suffix < 100; suffix++) {
            String candidate = base + "-" + suffix;
            if (!userRepository.existsByHandle(candidate)) {
                return candidate;
            }
        }
        throw new DuplicateResourceException(
                "Could not allocate a handle for this account. Choose a different email.");
    }

    static UserRef toRef(User user) {
        return new UserRef(user.getId(), user.getEmail(), user.getDisplayName(), user.getHandle());
    }
}
