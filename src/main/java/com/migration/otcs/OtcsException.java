package com.migration.otcs;

public class OtcsException extends RuntimeException {

    private final int status;

    public OtcsException(String message, int status) {
        super(message);
        this.status = status;
    }

    public OtcsException(String message, Throwable cause) {
        super(message, cause);
        this.status = -1;
    }

    /** HTTP status, or -1 when the call did not get a response. */
    public int status() {
        return status;
    }
}
