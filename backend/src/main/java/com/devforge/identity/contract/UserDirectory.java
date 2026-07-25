package com.devforge.identity.contract;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Published interface for looking up users from other modules.
 *
 * <p>This is the <em>only</em> identity type other modules are allowed to depend
 * on. Membership needs to resolve invitees by email; boards need display names
 * for assignees. Both go through here rather than reaching into the identity
 * module's repositories.
 */
public interface UserDirectory {

    Optional<UserRef> findById(UUID userId);

    Optional<UserRef> findByEmail(String email);

    /**
     * Resolves many users at once, so callers rendering a list of assignees or
     * members do not issue a query per row.
     *
     * @return references keyed by id; ids with no matching user are absent
     */
    Map<UUID, UserRef> findAllByIds(Collection<UUID> userIds);

    List<UserRef> search(String query);

    /**
     * @throws com.devforge.shared.exception.ResourceNotFoundException if absent
     */
    UserRef require(UUID userId);
}
