package com.cballestas.resources;

import com.cballestas.service.JwtService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/api/v1/jwt")
@ApplicationScoped
public class JwtResource {

    private final JwtService jwtService;

    public JwtResource(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJwt(){
        return Response.ok(jwtService.generateJwt()).build();
    }
}
