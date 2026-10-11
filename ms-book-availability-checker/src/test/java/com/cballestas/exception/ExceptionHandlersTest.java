package com.cballestas.exception;

import com.cballestas.exception.handlers.BusinessExceptionHandler;
import com.cballestas.exception.handlers.ConstraintViolationExceptionHandler;
import com.cballestas.exception.handlers.NotFoundExceptionHandler;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExceptionHandlersTest {

    @Test
    void customBusinessExceptionsPreserveMessageAndExpectedStatus() {
        assertBusinessException(new CustomBusinessException("Business error", 422), "Business error", 422);
        assertBusinessException(new CustomDuplicateException("Duplicate"), "Duplicate", 409);
        assertBusinessException(new CustomInsufficientStockException("Insufficient"), "Insufficient", 409);
        assertBusinessException(new CustomNotFoundException("Missing"), "Missing", 404);
    }

    @Test
    void errorResponseRecordsExposeSuppliedValues() {
        ValidationErrorResponse validationError = new ValidationErrorResponse("must not be blank", "name");
        ErrorResponse error = new ErrorResponse(400, "Validation error", List.of(validationError));

        assertEquals("must not be blank", validationError.message());
        assertEquals("name", validationError.field());
        assertEquals(400, error.status());
        assertEquals("Validation error", error.message());
        assertEquals(List.of(validationError), error.errors());
    }

    @Test
    void businessExceptionHandlerUsesExceptionStatusAndMessage() {
        Response response = new BusinessExceptionHandler()
                .toResponse(new CustomBusinessException("Invalid operation", 422));

        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals(422, response.getStatus());
        assertEquals(422, body.status());
        assertEquals("Invalid operation", body.message());
        assertNull(body.errors());
    }

    @Test
    void constraintViolationHandlerReturnsAllValidationErrors() {
        ConstraintViolation<?> firstViolation = violation("name", "El nombre es obligatorio");
        ConstraintViolation<?> secondViolation = violation("stock", "El stock debe ser mayor o igual a 1");

        Response response = new ConstraintViolationExceptionHandler()
                .toResponse(new ConstraintViolationException(Set.of(firstViolation, secondViolation)));

        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals(Response.Status.BAD_REQUEST.getStatusCode(), response.getStatus());
        assertEquals(400, body.status());
        assertEquals("Validation error", body.message());
        assertEquals(2, body.errors().size());
        assertEquals(
                Set.of(
                        new ValidationErrorResponse("El nombre es obligatorio", "name"),
                        new ValidationErrorResponse("El stock debe ser mayor o igual a 1", "stock")),
                Set.copyOf(body.errors()));
    }

    @Test
    void notFoundExceptionHandlerReturnsStandardNotFoundResponse() {
        Response response = new NotFoundExceptionHandler().toResponse(new NotFoundException());

        ErrorResponse body = (ErrorResponse) response.getEntity();
        assertEquals(Response.Status.NOT_FOUND.getStatusCode(), response.getStatus());
        assertEquals(404, body.status());
        assertEquals("Recurso no encontrado", body.message());
        assertNull(body.errors());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ConstraintViolation<?> violation(String field, String message) {
        ConstraintViolation violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(violation.getMessage()).thenReturn(message);
        when(violation.getPropertyPath()).thenReturn(path);
        when(path.toString()).thenReturn(field);
        return violation;
    }

    private static void assertBusinessException(CustomBusinessException exception, String message, int status) {
        assertEquals(message, exception.getMessage());
        assertEquals(status, exception.getStatus());
    }
}
