package com.thirdeye.backend.exception;

/**
 * Thrown when a requested resource (Project, Review, Finding) does not exist.
 * Mapped to HTTP 404 by GlobalExceptionHandler.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
