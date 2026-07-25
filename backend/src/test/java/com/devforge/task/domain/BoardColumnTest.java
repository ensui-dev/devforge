package com.devforge.task.domain;

import com.devforge.shared.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardColumnTest {

    private final Board board = new Board(UUID.randomUUID(), "Delivery");

    @Test
    void allowsAnyCountWhenNoLimitIsSet() {
        BoardColumn column = board.addColumn("Backlog", null);

        assertThatCode(() -> column.requireCapacityFor(10_000)).doesNotThrowAnyException();
    }

    @Test
    void allowsCountUpToTheLimit() {
        BoardColumn column = board.addColumn("In Progress", 3);

        assertThatCode(() -> column.requireCapacityFor(3)).doesNotThrowAnyException();
    }

    @Test
    void rejectsExceedingTheLimit() {
        BoardColumn column = board.addColumn("In Progress", 3);

        assertThatThrownBy(() -> column.requireCapacityFor(4))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("work-in-progress limit of 3");
    }

    @Test
    void renamingCanClearTheLimit() {
        BoardColumn column = board.addColumn("In Progress", 3);

        column.rename("Doing", null);

        assertThat(column.getName()).isEqualTo("Doing");
        assertThat(column.getWipLimit()).isNull();
        assertThatCode(() -> column.requireCapacityFor(99)).doesNotThrowAnyException();
    }
}
