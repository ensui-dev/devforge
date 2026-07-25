package com.devforge.shared.exception;

/**
 * A business rule rejected the request.
 *
 * <p>Domain rules raise this rather than {@link IllegalArgumentException} so that
 * the error handler can map genuine client mistakes to 400 without also
 * swallowing programming errors — an unexpected {@code IllegalArgumentException}
 * from a library must surface as a 500, not be reported to the caller as their
 * fault.
 */
public class DomainValidationException extends RuntimeException {

    public DomainValidationException(String message) {
        super(message);
    }
}
