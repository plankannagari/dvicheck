package com.dvicheck.backend.exception;

public class DvicheckException extends RuntimeException {

    private final String errorCode;

    public DvicheckException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public static DvicheckException notFound(String entity) {
        return new DvicheckException("NOT_FOUND", entity + " not found");
    }

    public static DvicheckException unauthorized() {
        return new DvicheckException("UNAUTHORIZED", "Unauthorized");
    }

    public static DvicheckException badRequest(String msg) {
        return new DvicheckException("BAD_REQUEST", msg);
    }

    public static DvicheckException serviceUnavailable(String msg) {
        return new DvicheckException("SERVICE_UNAVAILABLE", msg);
    }
}
