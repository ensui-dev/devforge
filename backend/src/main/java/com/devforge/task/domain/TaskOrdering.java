package com.devforge.task.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Position arithmetic for the tasks of a column.
 *
 * <p>Isolated here, free of persistence and Spring, because ordering is the part
 * of a kanban board most likely to drift: it is easy to get right for the happy
 * path and easy to leave gaps on delete or collide on move. As a pure function
 * over lists it can be exhaustively unit tested.
 *
 * <p>All operations renumber the affected column to a contiguous {@code 0..n-1}
 * run, which is why the schema carries no unique constraint on
 * {@code (column_id, position)} — the invariant is restored before the
 * transaction commits, but intermediate states inside it are not unique.
 */
public final class TaskOrdering {

    private TaskOrdering() {
    }

    /**
     * Moves {@code task} within its own column to {@code targetPosition}.
     *
     * @param tasksInColumn every task currently in the column, including {@code task}
     */
    public static void repositionWithinColumn(List<Task> tasksInColumn, Task task, int targetPosition) {
        List<Task> ordered = sorted(tasksInColumn);
        ordered.removeIf(candidate -> candidate.getId().equals(task.getId()));
        ordered.add(clampInsertionIndex(targetPosition, ordered.size()), task);
        renumber(ordered);
    }

    /**
     * Moves {@code task} out of {@code sourceTasks} and into {@code targetTasks} at
     * {@code targetPosition}, renumbering both columns.
     *
     * @param sourceTasks tasks of the origin column, including {@code task}
     * @param targetTasks tasks of the destination column, excluding {@code task}
     */
    public static void moveAcrossColumns(
            List<Task> sourceTasks,
            List<Task> targetTasks,
            Task task,
            UUID targetColumnId,
            int targetPosition
    ) {
        List<Task> source = sorted(sourceTasks);
        source.removeIf(candidate -> candidate.getId().equals(task.getId()));
        renumber(source);

        List<Task> target = sorted(targetTasks);
        int index = clampInsertionIndex(targetPosition, target.size());
        task.moveTo(targetColumnId, index);
        target.add(index, task);
        renumber(target);
    }

    /**
     * Closes the gap left by a task that has been removed.
     *
     * @param remainingTasks the column's tasks after the removal
     */
    public static void compact(List<Task> remainingTasks) {
        renumber(sorted(remainingTasks));
    }

    /** @return the position a task appended to a column of this size should take */
    public static int appendPosition(int currentCount) {
        return currentCount;
    }

    /**
     * Clamps rather than rejects: a drag beyond either end of a lane is a normal
     * gesture meaning "first" or "last", not a client error.
     */
    private static int clampInsertionIndex(int requested, int size) {
        return Math.clamp(requested, 0, size);
    }

    private static List<Task> sorted(List<Task> tasks) {
        List<Task> copy = new ArrayList<>(tasks);
        copy.sort(Comparator.comparingInt(Task::getPosition));
        return copy;
    }

    private static void renumber(List<Task> ordered) {
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).reposition(index);
        }
    }
}
