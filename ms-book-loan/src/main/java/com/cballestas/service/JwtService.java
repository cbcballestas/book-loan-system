package com.cballestas.service;

import com.cballestas.dto.response.JwtResponse;
import io.smallrye.jwt.build.Jwt;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class JwtService {

    public JwtResponse generateJwt() {

        Set<String> roles = new HashSet<String>(
                Arrays.asList("admin", "user")
        );

        long duration = System.currentTimeMillis() / 1000 + 3600;

        String jwt = Jwt.issuer("jwt-service")
                .subject("jwt-service")
                .groups(roles)
                .expiresAt(duration) // Expira en 1 hora
                .sign();
        return new JwtResponse(jwt);
    }
}
