package com.cballestas.resources;

import com.cballestas.dto.response.JwtResponse;
import com.cballestas.service.JwtService;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JwtResourceTest {

    @Test
    void getJwtReturnsTheTokenGeneratedByTheService() {
        JwtService jwtService = mock(JwtService.class);
        JwtResource resource = new JwtResource(jwtService);
        String token = "signed.jwt.token";
        when(jwtService.generateJwt()).thenReturn(new JwtResponse(token));

        Response response = resource.getJwt();

        assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());
        assertEquals(token, ((JwtResponse) response.getEntity()).token());
        verify(jwtService).generateJwt();
    }
}
