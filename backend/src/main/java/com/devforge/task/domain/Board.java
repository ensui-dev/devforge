package com.devforge.task.domain;

import com.devforge.shared.domain.BaseEntity;
import com.devforge.shared.exception.DomainValidationException;
import com.devforge.shared.exception.ResourceNotFoundException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for a board and its columns.
 *
 * <p>Column structure is manipulated only through this class, so the "positions
 * are contiguous and start at zero" invariant has exactly one owner. The previous
 * design left position arithmetic in the service layer, which is why moves and
 * deletes could leave gaps.
 *
 * <p>Tasks are deliberately <em>not</em> part of this aggregate. A board may hold
 * thousands of them, and loading all of them to move one would be wasteful — so
 * {@link Task} is its own aggregate that references a column by id. That also
 * means this entity has a single collection, avoiding the nested-collection fetch
 * that Hibernate rejects as {@code MultipleBagFetchException}.
 */
@Entity
@Table(name = "boards")
public class Board extends BaseEntity {

    private static final List<String> DEFAULT_COLUMN_NAMES = List.of("Backlog", "In Progress", "Review", "Done");
    private static final int MAX_COLUMNS = 20;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String name;

    @OneToMany(mappedBy = "board", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<BoardColumn> columns = new ArrayList<>();

    protected Board() {
    }

    public Board(UUID workspaceId, String name) {
        this.workspaceId = workspaceId;
        this.name = name;
    }

    /** A new board starts with the conventional delivery lanes. */
    public static Board withDefaultColumns(UUID workspaceId, String name) {
        Board board = new Board(workspaceId, name);
        DEFAULT_COLUMN_NAMES.forEach(columnName -> board.addColumn(columnName, null));
        return board;
    }

    public UUID getWorkspaceId() {
        return workspaceId;
    }

    public String getName() {
        return name;
    }

    public void rename(String name) {
        this.name = name;
    }

    /**
     * @return the columns in display order; mutate via this class only
     *
     * <p>Sorted here rather than relying on {@code @OrderBy}, which Hibernate
     * applies only when loading the collection. After a move within the same
     * transaction the underlying list still holds insertion order, so an
     * unsorted accessor would hand the client stale positions.
     */
    public List<BoardColumn> getColumns() {
        return columns.stream()
                .sorted(Comparator.comparingInt(BoardColumn::getPosition))
                .toList();
    }

    public BoardColumn addColumn(String name, Integer wipLimit) {
        if (columns.size() >= MAX_COLUMNS) {
            throw new DomainValidationException("A board cannot have more than %d columns".formatted(MAX_COLUMNS));
        }
        BoardColumn column = new BoardColumn(this, name, columns.size(), wipLimit);
        columns.add(column);
        return column;
    }

    public BoardColumn requireColumn(UUID columnId) {
        return columns.stream()
                .filter(column -> column.getId().equals(columnId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Column", columnId));
    }

    /**
     * Moves a column to {@code targetPosition}, shifting the others to close and
     * open the gap. Out-of-range targets are clamped rather than rejected, so a
     * drag past either end behaves as the user intends.
     */
    public void moveColumn(UUID columnId, int targetPosition) {
        BoardColumn column = requireColumn(columnId);

        List<BoardColumn> ordered = new ArrayList<>(getColumns());
        ordered.remove(column);
        ordered.add(Math.clamp(targetPosition, 0, ordered.size()), column);
        renumber(ordered);
    }

    /**
     * Removes a column. Its tasks are removed with it by the database cascade, so
     * callers must confirm intent before reaching here.
     */
    public void removeColumn(UUID columnId) {
        if (columns.size() <= 1) {
            throw new DomainValidationException("A board must keep at least one column");
        }
        BoardColumn column = requireColumn(columnId);
        columns.remove(column);
        renumber(getColumns());
    }

    public boolean belongsTo(UUID workspaceId) {
        return this.workspaceId.equals(workspaceId);
    }

    private static void renumber(List<BoardColumn> ordered) {
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).moveTo(index);
        }
    }
}
