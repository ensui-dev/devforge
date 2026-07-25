package com.devforge.task.application;

import com.devforge.identity.contract.UserRef;

import java.util.UUID;

/**
 * The task module's own view of an assignee, built from the identity module's
 * {@code UserRef}. Declaring it here rather than reusing identity's response DTO
 * keeps the two modules' API shapes free to change independently.
 */
public record TaskAssigneeResponse(UUID id, String displayName, String email) {

    public static TaskAssigneeResponse from(UserRef user) {
        return user == null ? null : new TaskAssigneeResponse(user.id(), user.displayName(), user.email());
    }
}
