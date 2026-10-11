package com.cballestas.exception;

import com.cballestas.exception.handlers.BusinessExceptionHandler;
import com.cballestas.exception.handlers.ConstraintViolationExceptionHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExceptionHandlersTest {

    @Test
    void businessExceptionsExposeTheirExpectedHttpStatuses() {
        assertEquals(404, new CustomNotFoundException("missing").getStatus());
        assertEquals(409, new CustomDuplicateException("duplicate").getStatus());
        assertEquals(409, new CustomInsufficientStockException("stock").getStatus());
    }

    @Test
    void businessExceptionHandlerPreservesStatusAndMessage() {
        Response response = new BusinessExceptionHandler()
                .toResponse(new CustomBusinessException("invalid order", 422));

        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals(422, response.getStatus());
        assertEquals(422, error.status());
        assertEquals("invalid order", error.message());
        assertNull(error.errors());
    }

    @Test
    @SuppressWarnings("unchecked")
    void constraintViolationHandlerMapsViolationsToValidationErrors() {
        ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(violation.getMessage()).thenReturn("must not be blank");
        when(violation.getPropertyPath()).thenReturn(path);
        when(path.toString()).thenReturn("save.request.customerName");

        Response response = new ConstraintViolationExceptionHandler()
                .toResponse(new ConstraintViolationException(Set.of(violation)));

        ErrorResponse error = (ErrorResponse) response.getEntity();
        assertEquals(400, response.getStatus());
        assertEquals("Validation error", error.message());
        assertEquals(1, error.errors().size());
        assertEquals("must not be blank", error.errors().get(0).message());
        assertEquals("save.request.customerName", error.errors().get(0).field());
    }
}
