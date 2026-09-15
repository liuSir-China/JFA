package com.jfa.common;

public class JfaException extends RuntimeException {
    private final ErrorCode errorCode;

    public JfaException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public JfaException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public JfaException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
