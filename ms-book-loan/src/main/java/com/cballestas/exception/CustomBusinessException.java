package com.cballestas.exception;

import lombok.Getter;

public class CustomBusinessException extends RuntimeException {

    @Getter
    private final int status;

    public CustomBusinessException(String message, int status) {
        super(message);
        this.status = status;
    }
}