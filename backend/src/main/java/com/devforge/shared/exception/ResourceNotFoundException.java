package com.devforge.shared.exception;

/** A requested resource does not exist, or is not visible to the caller. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, Object identifier) {
        super("%s not found: %s".formatted(resource, identifier));
    }
}
