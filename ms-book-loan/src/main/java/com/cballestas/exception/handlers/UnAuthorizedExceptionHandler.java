package com.cballestas.exception.handlers;

import com.cballestas.exception.ErrorResponse;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class UnAuthorizedExceptionHandler implements ExceptionMapper<UnauthorizedException> {

    @Override
    public Response toResponse(UnauthorizedException exception) {

        return Response
                .status(Response.Status.UNAUTHORIZED)
                .entity(new ErrorResponse(
                        401,
                        "Acceso no autorizado: Token JWT ausente, inválido o expirado.",
                        null
                ))
                .build();
    }
}