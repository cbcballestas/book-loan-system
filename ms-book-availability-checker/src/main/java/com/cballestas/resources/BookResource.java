package com.cballestas.resources;

import com.cballestas.dto.BookDto;
import com.cballestas.dto.BookRequestDto;
import com.cballestas.dto.PaginatedResponseDto;
import com.cballestas.dto.StockDecreaseRequestDto;
import com.cballestas.service.BookService;
import io.smallrye.mutiny.Uni;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.UUID;

@Path("/api/v1/books")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookResource {
    private final BookService bookService;

    public BookResource(BookService bookService) {
        this.bookService = bookService;
    }

    @GET
    public Uni<PaginatedResponseDto<BookDto>> findAll(
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("sort") @DefaultValue("name") String sort
    ) {
        return bookService.findAll(page, size, sort);
    }

    @GET
    @Path("/{id}")
    public Uni<Response> findById(
            @PathParam("id")
            @NotNull(message = "El id es obligatorio")
            UUID id) {

        return bookService.findById(id)
                .map(bookDto -> Response.ok(bookDto).build());
    }
    @POST
    public Uni<Response> save(@Valid BookRequestDto bookRequestDto) {

        return bookService
                .save(bookRequestDto)
                .replaceWith(
                        Response.status(Response.Status.CREATED).build()
                );
    }

    @PUT
    @Path("/{id}")
    public Uni<Response> update(
            @PathParam("id")
            @NotNull(message = "El id es obligatorio")
            UUID id,
            @Valid BookRequestDto bookRequestDto) {

        return bookService
                .update(id, bookRequestDto)
                .replaceWith(
                        Response.ok().build()
                );
    }

    @PATCH
    @Path("/{id}/stock")
    public Uni<BookDto> decreaseStock(
            @PathParam("id")
            @NotNull(message = "El id es obligatorio")
            UUID id,
            @Valid StockDecreaseRequestDto stockDecreaseRequest) {

        return bookService.decreaseStock(id, stockDecreaseRequest);
    }

    @DELETE
    @Path("/{id}")
    public Uni<Response> delete(

            @PathParam("id")
            @NotNull(message = "El id es obligatorio")
            UUID id) {

        return bookService
                .delete(id)
                .replaceWith(
                        Response.noContent().build()
                );
    }
}
