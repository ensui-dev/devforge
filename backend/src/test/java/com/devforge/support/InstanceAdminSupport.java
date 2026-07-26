package com.devforge.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Grants instance administration in tests.
 *
 * <p>Written directly because the application deliberately offers no endpoint for
 * it: an instance admin is created by running setup, or promoted by an operator
 * with database access. Tests need the same door.
 */
@Component
public class InstanceAdminSupport {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void promote(UUID userId) {
        entityManager.createNativeQuery(
                        "UPDATE users SET instance_admin = TRUE WHERE id = :id")
                .setParameter("id", userId)
                .executeUpdate();
    }
}
