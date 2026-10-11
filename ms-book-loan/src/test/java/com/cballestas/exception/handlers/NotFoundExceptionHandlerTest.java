package com.cballestas.exception.handlers;

import com.cballestas.exception.ErrorResponse;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class NotFoundExceptionHandlerTest {

    @Test
    void toResponseReturnsStandardNotFoundError() {
        Response response = new NotFoundExceptionHandler().toResponse(new NotFoundException());

        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals(404, error.status());
        assertEquals("Recurso no encontrado", error.message());
        assertNull(error.errors());
    }
}
