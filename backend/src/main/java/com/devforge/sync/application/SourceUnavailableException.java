package com.devforge.sync.application;

/**
 * The repository could not be read.
 *
 * <p>Its message is shown to the operator on the settings screen, so it says what
 * went wrong in terms they can act on — a wrong branch, a private repository with no
 * token — rather than repeating an HTTP status.
 */
public class SourceUnavailableException extends RuntimeException {

    public SourceUnavailableException(String message) {
        super(message);
    }

    public SourceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
