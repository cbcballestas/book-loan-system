package com.cballestas.resources;

import com.cballestas.dto.BookDto;
import com.cballestas.dto.BookRequestDto;
import com.cballestas.dto.PaginatedResponseDto;
import com.cballestas.dto.StockDecreaseRequestDto;
import com.cballestas.exception.CustomInsufficientStockException;
import com.cballestas.exception.CustomNotFoundException;
import com.cballestas.service.BookService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class BookResourceTest {

    @InjectMock
    BookService bookService;

    @BeforeEach
    void setUp() {
        reset(bookService);
    }

    @Test
    void findAllDelegatesParametersAndReturnsServiceResult() {
        BookDto book = dto();
        PaginatedResponseDto<BookDto> page = new PaginatedResponseDto<>(List.of(book), 2, 5, 4, 20);
        when(bookService.findAll(2, 5, "price")).thenReturn(Uni.createFrom().item(page));

        given()
                .queryParam("page", 2)
                .queryParam("size", 5)
                .queryParam("sort", "price")
                .when().get("/api/v1/books")
                .then()
                .statusCode(200)
                .body("pageIndex", equalTo(2))
                .body("pageSize", equalTo(5))
                .body("totalPages", equalTo(4))
                .body("totalElements", equalTo(20))
                .body("content", hasSize(1))
                .body("content[0].name", equalTo("Dune"));

        verify(bookService).findAll(2, 5, "price");
    }

    @Test
    void findByIdReturnsOkResponseContainingBook() {
        UUID id = UUID.randomUUID();
        BookDto book = dto();
        when(bookService.findById(id)).thenReturn(Uni.createFrom().item(book));

        given()
                .when().get("/api/v1/books/{id}", id)
                .then()
                .statusCode(200)
                .body("name", equalTo("Dune"));

        verify(bookService).findById(id);
    }

    @Test
    void findByIdReturnsNotFoundWhenServiceFails() {
        UUID id = UUID.randomUUID();
        when(bookService.findById(id)).thenReturn(
                Uni.createFrom().failure(new CustomNotFoundException("Book not found")));

        given()
                .when().get("/api/v1/books/{id}", id)
                .then()
                .statusCode(404)
                .body("message", equalTo("Book not found"));
    }

    @Test
    void saveReturnsCreatedAfterServiceSucceeds() {
        when(bookService.save(any())).thenReturn(Uni.createFrom().item(dto()));

        given()
                .contentType(ContentType.JSON)
                .body(requestJson("Dune", "19.99", 8))
                .when().post("/api/v1/books")
                .then()
                .statusCode(201);

        verify(bookService).save(any());
    }

    @Test
    void saveReturnsBadRequestWhenRequestIsInvalid() {
        given()
                .contentType(ContentType.JSON)
                .body(requestJson("", "19.99", 8))
                .when().post("/api/v1/books")
                .then()
                .statusCode(400);

        verify(bookService, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void updateReturnsOkAfterServiceSucceeds() {
        UUID id = UUID.randomUUID();
        when(bookService.update(eq(id), any())).thenReturn(Uni.createFrom().item(dto()));

        given()
                .contentType(ContentType.JSON)
                .body(requestJson("Dune", "19.99", 8))
                .when().put("/api/v1/books/{id}", id)
                .then()
                .statusCode(200);

        verify(bookService).update(eq(id), any());
    }

    @Test
    void decreaseStockDelegatesAndReturnsServiceResult() {
        UUID id = UUID.randomUUID();
        BookDto book = dto();
        when(bookService.decreaseStock(eq(id), any())).thenReturn(Uni.createFrom().item(book));

        given()
                .contentType(ContentType.JSON)
                .body("{\"quantity\": 2}")
                .when().patch("/api/v1/books/{id}/stock", id)
                .then()
                .statusCode(200)
                .body("name", equalTo("Dune"));

        verify(bookService).decreaseStock(eq(id), any());
    }

    @Test
    void decreaseStockPropagatesInsufficientStockAsConflict() {
        UUID id = UUID.randomUUID();
        when(bookService.decreaseStock(eq(id), any())).thenReturn(
                Uni.createFrom().failure(new CustomInsufficientStockException("Insufficient stock")));

        given()
                .contentType(ContentType.JSON)
                .body("{\"quantity\": 2}")
                .when().patch("/api/v1/books/{id}/stock", id)
                .then()
                .statusCode(409)
                .body("message", equalTo("Insufficient stock"));
    }

    @Test
    void deleteReturnsNoContentAfterServiceSucceeds() {
        UUID id = UUID.randomUUID();
        when(bookService.delete(id)).thenReturn(Uni.createFrom().voidItem());

        given()
                .when().delete("/api/v1/books/{id}", id)
                .then()
                .statusCode(204);

        verify(bookService).delete(id);
    }

    private static BookDto dto() {
        return new BookDto(UUID.randomUUID(), "Dune", new BigDecimal("19.99"), 8);
    }

    private static String requestJson(String name, String price, int stock) {
        return """
                {
                  "name": "%s",
                  "price": %s,
                  "stock": %d
                }
                """.formatted(name, price, stock);
    }
}
