package com.devforge.shared.domain;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Entity identity semantics. Worth pinning because collections of entities rely on
 * them, and the previous {@code BaseEntity} inherited reference equality while
 * assigning ids only at flush time.
 */
class BaseEntityTest {

    private static class Alpha extends BaseEntity {
    }

    private static class Beta extends BaseEntity {
    }

    @Test
    void assignsAnIdentifierBeforePersistence() {
        assertThat(new Alpha().getId()).isNotNull();
    }

    @Test
    void distinctInstancesHaveDistinctIdentifiers() {
        assertThat(new Alpha().getId()).isNotEqualTo(new Alpha().getId());
    }

    @Test
    void equalsItself() {
        Alpha entity = new Alpha();

        assertThat(entity).isEqualTo(entity);
    }

    @Test
    void differentInstancesAreNotEqual() {
        assertThat(new Alpha()).isNotEqualTo(new Alpha());
    }

    @Test
    void isNotEqualToADifferentEntityTypeSharingAnId() {
        Alpha alpha = new Alpha();
        Beta beta = new Beta();
        // Force the same identifier to prove type is part of equality.
        setId(beta, alpha.getId());

        assertThat(alpha).isNotEqualTo(beta);
    }

    @Test
    void twoInstancesOfTheSameRowAreEqual() {
        Alpha first = new Alpha();
        Alpha second = new Alpha();
        setId(second, first.getId());

        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }

    @Test
    void behavesCorrectlyInsideAHashSet() {
        Alpha first = new Alpha();
        Alpha duplicate = new Alpha();
        setId(duplicate, first.getId());

        Set<BaseEntity> entities = new HashSet<>();
        entities.add(first);
        entities.add(duplicate);

        assertThat(entities).hasSize(1);
    }

    @Test
    void isNotEqualToNullOrAnUnrelatedType() {
        Alpha entity = new Alpha();

        assertThat(entity).isNotEqualTo(null).isNotEqualTo("not an entity");
    }

    @Test
    void versionIsNullUntilPersistedSoSpringDataTreatsItAsNew() {
        assertThat(new Alpha().getVersion()).isNull();
    }

    private static void setId(BaseEntity entity, java.util.UUID id) {
        try {
            var field = BaseEntity.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(entity, id);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
