package com.devforge.task.application;

import com.devforge.task.domain.BoardColumn;

import java.util.List;
import java.util.UUID;

public record BoardColumnResponse(
        UUID id,
        String name,
        int position,
        Integer wipLimit,
        List<TaskResponse> tasks
) {

    public static BoardColumnResponse of(BoardColumn column, List<TaskResponse> tasks) {
        return new BoardColumnResponse(
                column.getId(),
                column.getName(),
                column.getPosition(),
                column.getWipLimit(),
                tasks
        );
    }
}
