package com.migratehero.exception;

/**
 * 认证异常
 */
public class AuthenticationException extends BusinessException {

    public AuthenticationException(String message) {
        super("AUTH_ERROR", message);
    }

    public AuthenticationException(String errorCode, String message) {
        super(errorCode, message);
    }
}
