package com.devforge.task.domain;

import com.devforge.shared.domain.BaseEntity;
import com.devforge.shared.exception.DomainValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A lane on a board. Part of the {@link Board} aggregate — created, reordered,
 * and removed through the board, never on its own.
 */
@Entity
@Table(name = "board_columns")
public class BoardColumn extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "board_id", nullable = false, updatable = false)
    private Board board;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false)
    private int position;

    /** Optional work-in-progress cap; {@code null} means unlimited. */
    @Column(name = "wip_limit")
    private Integer wipLimit;

    protected BoardColumn() {
    }

    BoardColumn(Board board, String name, int position, Integer wipLimit) {
        this.board = board;
        this.name = name;
        this.position = position;
        this.wipLimit = wipLimit;
    }

    public Board getBoard() {
        return board;
    }

    public String getName() {
        return name;
    }

    public int getPosition() {
        return position;
    }

    public Integer getWipLimit() {
        return wipLimit;
    }

    void moveTo(int position) {
        this.position = position;
    }

    public void rename(String name, Integer wipLimit) {
        this.name = name;
        this.wipLimit = wipLimit;
    }

    /**
     * Enforces the WIP cap.
     *
     * <p>Checked when a task arrives rather than when the limit is set, so an
     * existing over-capacity lane can still be drained after a limit is lowered.
     *
     * @param resultingCount how many tasks the column would hold afterwards
     */
    public void requireCapacityFor(int resultingCount) {
        if (wipLimit != null && resultingCount > wipLimit) {
            throw new DomainValidationException(
                    "Column '%s' has a work-in-progress limit of %d".formatted(name, wipLimit));
        }
    }
}
