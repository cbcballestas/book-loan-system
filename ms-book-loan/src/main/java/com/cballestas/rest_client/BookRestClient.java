package com.cballestas.rest_client;

import com.cballestas.dto.StockDecreaseRequestDto;
import com.cballestas.rest_client.response.BookResponseDto;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.math.BigDecimal;
import java.util.UUID;

@Path("/api/v1/books")
@RegisterRestClient(configKey = "book-availability-checker-api")
@Produces(MediaType.APPLICATION_JSON)
public interface BookRestClient {

    @GET
    @Path("/{id}")
    @Timeout(value = 2000) // Tiempo límite de 2 segundos
    @CircuitBreaker(requestVolumeThreshold = 4, failureRatio = 0.5, delay = 10000)
    @Fallback(fallbackMethod = "getFallbackBook")
    Uni<BookResponseDto> findById(@PathParam("id") UUID id);

    @PATCH
    @Path("/{id}/stock")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<BookResponseDto> decreaseStock(@PathParam("id") UUID id, StockDecreaseRequestDto request);


    // Método Fallback en caso de que el servicio falle o pase el timeout
    default Uni<BookResponseDto> getFallbackBook(UUID id) {
        BookResponseDto fallbackBook = new BookResponseDto();
        fallbackBook.setId(id);
        fallbackBook.setName("Libro No Disponible (Fallback)");
        fallbackBook.setPrice(BigDecimal.ZERO);
        return Uni.createFrom().item(fallbackBook);
    }

}