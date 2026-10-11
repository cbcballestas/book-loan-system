package com.cballestas.exception;

public class CustomNotFoundException extends CustomBusinessException {

    public CustomNotFoundException(String message) {
        super(message, 404);
    }
}