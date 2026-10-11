package com.cballestas.resources;

import com.cballestas.dto.PaginatedResponseDto;
import com.cballestas.dto.request.OrderRequest;
import com.cballestas.dto.response.OrderResponse;
import com.cballestas.service.OrderService;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/api/v1/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OrderResource {
    private final OrderService orderService;

    public OrderResource(OrderService orderService) {
        this.orderService = orderService;
    }

    @GET
    @PermitAll
    public Uni<PaginatedResponseDto<OrderResponse>> findAll(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("sort") @DefaultValue("id") String sort
    ) {
        return orderService.findAll(page, size, sort);
    }

    @GET
    @RolesAllowed({"admin", "user"})
    @Path("/{id}")
    public Uni<OrderResponse> findById(
            @PathParam("id") @NotNull(message = "El id es obligatorio") UUID id
    ) {
        return orderService.findById(id);
    }

    @POST
    @RolesAllowed("admin")
    public Uni<Response> save(@Valid OrderRequest request) {
        return orderService.save(request)
                .map(response -> Response.status(Response.Status.CREATED).entity(response).build());
    }
}
