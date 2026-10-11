package com.cballestas.exception;

public class CustomInsufficientStockException extends CustomBusinessException {

    public CustomInsufficientStockException(String message) {
        super(message, 409);
    }
}
