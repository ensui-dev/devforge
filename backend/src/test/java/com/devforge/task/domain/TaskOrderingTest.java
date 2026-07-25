package com.devforge.task.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ordering is the part of a kanban board most prone to silent corruption, so it is
 * tested exhaustively and directly — no mocks, no database.
 */
class TaskOrderingTest {

    private static final UUID BOARD_ID = UUID.randomUUID();
    private static final UUID COLUMN_A = UUID.randomUUID();
    private static final UUID COLUMN_B = UUID.randomUUID();

    @Test
    void appendPositionIsTheCurrentCount() {
        assertThat(TaskOrdering.appendPosition(0)).isZero();
        assertThat(TaskOrdering.appendPosition(3)).isEqualTo(3);
    }

    @ParameterizedTest(name = "moving \"C\" to position {0} yields {1}")
    @CsvSource({
            "0, C|A|B|D",
            "1, A|C|B|D",
            "2, A|B|C|D",
            "3, A|B|D|C"
    })
    void repositionsWithinAColumn(int targetPosition, String expectedOrder) {
        List<Task> tasks = column(COLUMN_A, "A", "B", "C", "D");
        Task moved = byTitle(tasks, "C");

        TaskOrdering.repositionWithinColumn(tasks, moved, targetPosition);

        assertThat(titlesInOrder(tasks)).containsExactly(expectedOrder.split("\\|"));
        assertPositionsAreContiguous(tasks);
    }

    @Test
    void clampsAPositionPastTheEndToLast() {
        List<Task> tasks = column(COLUMN_A, "A", "B", "C");
        Task moved = byTitle(tasks, "A");

        TaskOrdering.repositionWithinColumn(tasks, moved, 99);

        assertThat(titlesInOrder(tasks)).containsExactly("B", "C", "A");
        assertPositionsAreContiguous(tasks);
    }

    @Test
    void movingATaskOntoItsOwnPositionChangesNothing() {
        List<Task> tasks = column(COLUMN_A, "A", "B", "C");
        Task moved = byTitle(tasks, "B");

        TaskOrdering.repositionWithinColumn(tasks, moved, 1);

        assertThat(titlesInOrder(tasks)).containsExactly("A", "B", "C");
    }

    @Test
    void movesAcrossColumnsAndRenumbersBothSides() {
        List<Task> source = column(COLUMN_A, "A1", "A2", "A3");
        List<Task> target = column(COLUMN_B, "B1", "B2");
        Task moved = byTitle(source, "A2");

        TaskOrdering.moveAcrossColumns(source, target, moved, COLUMN_B, 1);

        assertThat(moved.getColumnId()).isEqualTo(COLUMN_B);
        assertThat(moved.getPosition()).isEqualTo(1);

        // The gap left behind is closed.
        List<Task> remaining = new ArrayList<>(source);
        remaining.removeIf(task -> task.getId().equals(moved.getId()));
        assertThat(titlesInOrder(remaining)).containsExactly("A1", "A3");
        assertPositionsAreContiguous(remaining);

        assertThat(byTitle(target, "B1").getPosition()).isZero();
        assertThat(byTitle(target, "B2").getPosition()).isEqualTo(2);
    }

    @Test
    void movesIntoAnEmptyColumn() {
        List<Task> source = column(COLUMN_A, "only");
        List<Task> target = new ArrayList<Task>();
        Task moved = byTitle(source, "only");

        TaskOrdering.moveAcrossColumns(source, target, moved, COLUMN_B, 0);

        assertThat(moved.getColumnId()).isEqualTo(COLUMN_B);
        assertThat(moved.getPosition()).isZero();
    }

    @Test
    void appendsWhenTargetPositionExceedsDestinationSize() {
        List<Task> source = column(COLUMN_A, "A1");
        List<Task> target = column(COLUMN_B, "B1", "B2");
        Task moved = byTitle(source, "A1");

        TaskOrdering.moveAcrossColumns(source, target, moved, COLUMN_B, 50);

        assertThat(moved.getPosition()).isEqualTo(2);
    }

    @Test
    void compactClosesGapsLeftByADeletion() {
        List<Task> tasks = new ArrayList<>(List.of(
                task(COLUMN_A, "kept-1", 0),
                task(COLUMN_A, "kept-2", 4),
                task(COLUMN_A, "kept-3", 9)
        ));

        TaskOrdering.compact(tasks);

        assertThat(tasks).extracting(Task::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    void compactOnAnEmptyColumnIsSafe() {
        List<Task> empty = new ArrayList<Task>();

        TaskOrdering.compact(empty);

        assertThat(empty).isEmpty();
    }

    @Test
    void toleratesDuplicatePositionsByProducingAStableContiguousOrder() {
        // A column left inconsistent by an earlier bug must still be repairable.
        List<Task> tasks = new ArrayList<>(List.of(
                task(COLUMN_A, "x", 2),
                task(COLUMN_A, "y", 2),
                task(COLUMN_A, "z", 2)
        ));

        TaskOrdering.compact(tasks);

        assertThat(tasks).extracting(Task::getPosition).containsExactlyInAnyOrder(0, 1, 2);
    }

    private static List<Task> column(UUID columnId, String... titles) {
        List<Task> tasks = new ArrayList<>();
        for (int index = 0; index < titles.length; index++) {
            tasks.add(task(columnId, titles[index], index));
        }
        return tasks;
    }

    private static Task task(UUID columnId, String title, int position) {
        return new Task(BOARD_ID, columnId, title, null, position, TaskPriority.MEDIUM, null);
    }

    private static Task byTitle(List<Task> tasks, String title) {
        return tasks.stream()
                .filter(task -> task.getTitle().equals(title))
                .findFirst()
                .orElseThrow();
    }

    private static List<String> titlesInOrder(List<Task> tasks) {
        return tasks.stream()
                .sorted(Comparator.comparingInt(Task::getPosition))
                .map(Task::getTitle)
                .toList();
    }

    private static void assertPositionsAreContiguous(List<Task> tasks) {
        assertThat(tasks.stream().map(Task::getPosition).sorted().toList())
                .as("positions form a contiguous 0..n-1 run")
                .containsExactlyElementsOf(java.util.stream.IntStream.range(0, tasks.size()).boxed().toList());
    }
}
