package com.cballestas.exception.handlers;

import com.cballestas.exception.ErrorResponse;
import com.cballestas.exception.ValidationErrorResponse;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class ConstraintViolationExceptionHandler implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {

        List<ValidationErrorResponse> errors = exception
                .getConstraintViolations()
                .stream()
                .map(violation -> new ValidationErrorResponse(violation.getMessage(), violation.getPropertyPath().toString()))
                .toList();

        return Response
                .status(Response.Status.BAD_REQUEST)
                .entity(new ErrorResponse(
                        400,
                        "Validation error",
                        errors
                ))
                .build();
    }
}