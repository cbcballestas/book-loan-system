package com.cballestas.exception.handlers;

import com.cballestas.exception.ErrorResponse;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UnAuthorizedExceptionHandlerTest {

    @Test
    void toResponseReturnsUnauthorizedError() {
        Response response = new UnAuthorizedExceptionHandler()
                .toResponse(new UnauthorizedException("invalid token"));

        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals(Response.Status.UNAUTHORIZED.getStatusCode(), response.getStatus());
        assertEquals(401, error.status());
        assertEquals("Acceso no autorizado: Token JWT ausente, inválido o expirado.", error.message());
        assertNull(error.errors());
    }
}
