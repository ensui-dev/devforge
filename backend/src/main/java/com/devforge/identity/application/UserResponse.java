package com.devforge.identity.application;

import com.devforge.identity.contract.UserRef;
import com.devforge.identity.domain.User;

import java.util.UUID;

public record UserResponse(UUID id, String email, String displayName) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getDisplayName());
    }

    public static UserResponse from(UserRef user) {
        return new UserResponse(user.id(), user.email(), user.displayName());
    }
}
