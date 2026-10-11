package com.cballestas.exception.handlers;

import com.cballestas.exception.CustomBusinessException;
import com.cballestas.exception.ErrorResponse;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BusinessExceptionHandler
        implements ExceptionMapper<CustomBusinessException> {

    @Override
    public Response toResponse(CustomBusinessException exception) {

        ErrorResponse error = new ErrorResponse(
                exception.getStatus(),
                exception.getMessage(),
                null
        );
        return Response
                .status(exception.getStatus())
                .entity(error)
                .build();
    }
}