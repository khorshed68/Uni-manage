package com.university.ums.exception;

/** Mapped to HTTP 409, e.g. a duplicate username or email. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
