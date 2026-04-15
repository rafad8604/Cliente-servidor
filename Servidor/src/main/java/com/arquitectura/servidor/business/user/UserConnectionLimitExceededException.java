package com.arquitectura.servidor.business.user;

public class UserConnectionLimitExceededException extends Exception {

    public UserConnectionLimitExceededException(String message) {
        super(message);
    }

    public UserConnectionLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}

