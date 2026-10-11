package com.cballestas.service;

import com.cballestas.dto.response.JwtResponse;
import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class JwtServiceTest {

    @Inject
    JwtService jwtService;

    @Test
    void generateJwtCreatesSignedTokenWithServiceClaimsAndRoles() {
        long beforeGeneration = System.currentTimeMillis() / 1000;

        JwtResponse jwtResponse = jwtService.generateJwt();
        String token = jwtResponse.token();

        String[] tokenParts = token.split("\\.");
        assertEquals(3, tokenParts.length);

        String payload = new String(
                Base64.getUrlDecoder().decode(tokenParts[1]),
                StandardCharsets.UTF_8);
        JsonObject claims = new JsonObject(payload);

        assertEquals("jwt-service", claims.getString("iss"));
        assertEquals("jwt-service", claims.getString("sub"));
        assertEquals(Set.of("admin", "user"), Set.copyOf(claims.getJsonArray("groups").getList()));

        long expiresAt = claims.getLong("exp");
        assertTrue(expiresAt >= beforeGeneration + 3_600);
        assertTrue(expiresAt <= beforeGeneration + 3_601);
    }
}
