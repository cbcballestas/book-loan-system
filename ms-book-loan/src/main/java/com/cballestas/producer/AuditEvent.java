package com.cballestas.producer;

public record AuditEvent(
        String action,
        String resource,
        String request,
        String response
) {
}
