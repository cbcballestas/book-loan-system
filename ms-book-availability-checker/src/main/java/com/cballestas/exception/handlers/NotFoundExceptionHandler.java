package com.cballestas.exception.handlers;

import com.cballestas.exception.ErrorResponse;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class NotFoundExceptionHandler implements ExceptionMapper<NotFoundException> {

    @Override
    public Response toResponse(NotFoundException exception) {

        return Response
                .status(Response.Status.NOT_FOUND)
                .entity(new ErrorResponse(
                        404,
                        "Recurso no encontrado",
                        null
                ))
                .build();
    }
}