package com.devforge.shared.exception;

/** A uniqueness rule would be violated by the requested change. */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
