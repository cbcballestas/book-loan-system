package com.cballestas.exception;

public class CustomDuplicateException extends CustomBusinessException {

    public CustomDuplicateException(String message) {
        super(message, 409);
    }
}