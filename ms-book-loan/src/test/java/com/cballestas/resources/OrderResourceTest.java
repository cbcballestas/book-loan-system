package com.cballestas.resources;

import com.cballestas.dto.PaginatedResponseDto;
import com.cballestas.dto.response.OrderItemResponse;
import com.cballestas.dto.response.OrderResponse;
import com.cballestas.dto.response.CreatedOrderResponse;
import com.cballestas.service.OrderService;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@QuarkusTest
class OrderResourceTest {

    @InjectMock
    OrderService orderService;

    @BeforeEach
    void setUp() {
        reset(orderService);
    }

    @Test
    void findAllDelegatesPaginationToService() {
        OrderItemResponse item = OrderItemResponse.builder()
                .bookId(UUID.randomUUID().toString())
                .bookName("Dune")
                .quantity(2)
                .price(19.99)
                .build();
        OrderResponse order = OrderResponse.builder()
                .id(UUID.randomUUID())
                .customerName("Ada")
                .totalAmount(39.98)
                .items(List.of(item))
                .build();
        PaginatedResponseDto<OrderResponse> page = new PaginatedResponseDto<>(List.of(order), 2, 5, 4, 20);
        when(orderService.findAll(2, 5, "customerName")).thenReturn(Uni.createFrom().item(page));

        given()
                .queryParam("page", 2)
                .queryParam("size", 5)
                .queryParam("sort", "customerName")
                .when().get("/api/v1/orders")
                .then()
                .statusCode(200)
                .body("pageIndex", equalTo(2))
                .body("pageSize", equalTo(5))
                .body("totalPages", equalTo(4))
                .body("totalElements", equalTo(20))
                .body("content", hasSize(1))
                .body("content[0].customerName", equalTo("Ada"));

        verify(orderService).findAll(2, 5, "customerName");
    }

    @Test
    @TestSecurity(user = "customer", roles = {"user"})
    void findByIdReturnsTheOrderFromTheService() {
        UUID id = UUID.randomUUID();
        OrderResponse order = OrderResponse.builder()
                .id(id)
                .customerName("Ada")
                .totalAmount(19.99)
                .items(List.of())
                .build();
        when(orderService.findById(id)).thenReturn(Uni.createFrom().item(order));

        given()
                .when().get("/api/v1/orders/{id}", id)
                .then()
                .statusCode(200)
                .body("id", equalTo(id.toString()))
                .body("customerName", equalTo("Ada"));

        verify(orderService).findById(id);
    }

    @Test
    @TestSecurity(user = "administrator", roles = {"admin"})
    void saveReturnsCreatedResponseWithServiceResult() {
        CreatedOrderResponse created = new CreatedOrderResponse(UUID.randomUUID().toString(), "Order created successfully");
        when(orderService.save(any())).thenReturn(Uni.createFrom().item(created));

        String body = """
                {
                  "customerName": "Ada",
                  "items": [
                    { "bookId": "%s", "quantity": 2 }
                  ]
                }
                """.formatted(UUID.randomUUID());

        given()
                .contentType(ContentType.JSON)
                .body(body)
                .when().post("/api/v1/orders")
                .then()
                .statusCode(201)
                .body("orderId", equalTo(created.orderId()))
                .body("message", equalTo("Order created successfully"));

        verify(orderService).save(any());
    }

    @Test
    @TestSecurity(user = "administrator", roles = {"admin"})
    void saveReturnsBadRequestWhenRequestIsInvalid() {
        String invalidBody = """
                {
                  "customerName": "",
                  "items": []
                }
                """;

        given()
                .contentType(ContentType.JSON)
                .body(invalidBody)
                .when().post("/api/v1/orders")
                .then()
                .statusCode(400);

        verify(orderService, never()).save(any());
    }
}
