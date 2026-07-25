package com.devforge.support;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Empties every application table between tests.
 *
 * <p>Integration tests share one application context and one container, so
 * without this the rows one test class writes are visible to the next. That is
 * what made the original {@code WorkspaceControllerIntegrationTest} fragile: it
 * asserted on {@code $[0]} of an unordered list that other classes had also
 * written to.
 *
 * <p>Table names are read from the catalogue rather than hard-coded, so adding a
 * migration cannot leave a stale table uncleaned.
 */
@Component
public class DatabaseCleaner {

    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void clean() {
        @SuppressWarnings("unchecked")
        List<String> tables = entityManager.createNativeQuery("""
                        SELECT tablename FROM pg_tables
                        WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                        """)
                .getResultList();

        if (tables.isEmpty()) {
            return;
        }

        // One statement so foreign keys never block an intermediate state.
        entityManager
                .createNativeQuery("TRUNCATE TABLE " + String.join(", ", tables) + " CASCADE")
                .executeUpdate();
    }
}
