package com.cballestas.exception;

public record ValidationErrorResponse(
        String message,
        String field
) {
}
