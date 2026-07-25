package com.devforge.task.domain;

import com.devforge.shared.exception.DomainValidationException;
import com.devforge.shared.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The Board aggregate owns column structure; these tests pin its invariants. */
class BoardTest {

    private static final UUID WORKSPACE_ID = UUID.randomUUID();

    @Test
    void seedsTheConventionalDeliveryLanes() {
        Board board = Board.withDefaultColumns(WORKSPACE_ID, "Delivery");

        assertThat(columnNames(board)).containsExactly("Backlog", "In Progress", "Review", "Done");
        assertThat(board.getColumns()).extracting(BoardColumn::getPosition).containsExactly(0, 1, 2, 3);
    }

    @Test
    void appendsNewColumnsAtTheEnd() {
        Board board = Board.withDefaultColumns(WORKSPACE_ID, "Delivery");

        BoardColumn added = board.addColumn("Blocked", 3);

        assertThat(added.getPosition()).isEqualTo(4);
        assertThat(added.getWipLimit()).isEqualTo(3);
    }

    @Test
    void movingAColumnRenumbersItsSiblings() {
        Board board = Board.withDefaultColumns(WORKSPACE_ID, "Delivery");
        UUID doneId = board.getColumns().get(3).getId();

        board.moveColumn(doneId, 0);

        assertThat(columnNames(board)).containsExactly("Done", "Backlog", "In Progress", "Review");
        assertThat(board.getColumns()).extracting(BoardColumn::getPosition).containsExactly(0, 1, 2, 3);
    }

    @Test
    void clampsAColumnMovePastTheEnd() {
        Board board = Board.withDefaultColumns(WORKSPACE_ID, "Delivery");
        UUID backlogId = board.getColumns().getFirst().getId();

        board.moveColumn(backlogId, 99);

        assertThat(columnNames(board)).containsExactly("In Progress", "Review", "Done", "Backlog");
    }

    @Test
    void removingAColumnClosesThePositionGap() {
        Board board = Board.withDefaultColumns(WORKSPACE_ID, "Delivery");
        UUID inProgressId = board.getColumns().get(1).getId();

        board.removeColumn(inProgressId);

        assertThat(columnNames(board)).containsExactly("Backlog", "Review", "Done");
        assertThat(board.getColumns()).extracting(BoardColumn::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    void refusesToRemoveTheLastColumn() {
        Board board = new Board(WORKSPACE_ID, "Minimal");
        board.addColumn("Only", null);

        UUID onlyId = board.getColumns().getFirst().getId();

        assertThatThrownBy(() -> board.removeColumn(onlyId))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("at least one column");
    }

    @Test
    void rejectsAnUnknownColumn() {
        Board board = Board.withDefaultColumns(WORKSPACE_ID, "Delivery");
        UUID unknown = UUID.randomUUID();

        assertThatThrownBy(() -> board.requireColumn(unknown))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void capsTheNumberOfColumns() {
        Board board = new Board(WORKSPACE_ID, "Wide");
        for (int index = 0; index < 20; index++) {
            board.addColumn("Column " + index, null);
        }

        assertThatThrownBy(() -> board.addColumn("One too many", null))
                .isInstanceOf(DomainValidationException.class)
                .hasMessageContaining("more than 20 columns");
    }

    @Test
    void exposesColumnsAsAnUnmodifiableView() {
        Board board = Board.withDefaultColumns(WORKSPACE_ID, "Delivery");

        assertThatThrownBy(() -> board.getColumns().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void reportsWorkspaceOwnership() {
        Board board = new Board(WORKSPACE_ID, "Delivery");

        assertThat(board.belongsTo(WORKSPACE_ID)).isTrue();
        assertThat(board.belongsTo(UUID.randomUUID())).isFalse();
    }

    private static List<String> columnNames(Board board) {
        return board.getColumns().stream().map(BoardColumn::getName).toList();
    }
}
