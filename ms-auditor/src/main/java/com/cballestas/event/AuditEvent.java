package com.cballestas.event;

public record AuditEvent(
        String action,
        String resource,
        String request,
        String response
) {
}
