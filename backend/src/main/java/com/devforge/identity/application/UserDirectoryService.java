package com.devforge.identity.application;

import com.devforge.identity.contract.UserDirectory;
import com.devforge.identity.contract.UserRef;
import com.devforge.identity.domain.User;
import com.devforge.identity.domain.UserRepository;
import com.devforge.shared.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * The identity module's implementation of its own published contract.
 */
@Service
@Transactional(readOnly = true)
public class UserDirectoryService implements UserDirectory {

    private final UserRepository userRepository;

    public UserDirectoryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserRef> findById(UUID userId) {
        return userRepository.findById(userId).map(UserDirectoryService::toRef);
    }

    @Override
    public Optional<UserRef> findByEmail(String email) {
        return userRepository.findByEmail(User.normalizeEmail(email)).map(UserDirectoryService::toRef);
    }

    @Override
    public Map<UUID, UserRef> findAllByIds(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllByIdIn(userIds).stream()
                .map(UserDirectoryService::toRef)
                .collect(java.util.stream.Collectors.toMap(UserRef::id, Function.identity()));
    }

    @Override
    public List<UserRef> search(String query) {
        String fragment = query == null ? "" : query.trim();
        if (fragment.isEmpty()) {
            return List.of();
        }
        return userRepository
                .findTop20ByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByDisplayNameAsc(
                        fragment, fragment)
                .stream()
                .map(UserDirectoryService::toRef)
                .toList();
    }

    @Override
    public UserRef require(UUID userId) {
        return findById(userId).orElseThrow(() -> new ResourceNotFoundException("User", userId));
    }

    private static UserRef toRef(User user) {
        return new UserRef(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
