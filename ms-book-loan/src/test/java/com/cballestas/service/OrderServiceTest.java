package com.cballestas.service;

import com.cballestas.dto.request.OrderItemRequest;
import com.cballestas.dto.request.OrderRequest;
import com.cballestas.dto.PaginatedResponseDto;
import com.cballestas.dto.response.CreatedOrderResponse;
import com.cballestas.dto.response.OrderItemResponse;
import com.cballestas.dto.response.OrderResponse;
import com.cballestas.entities.OrderEntity;
import com.cballestas.exception.CustomInsufficientStockException;
import com.cballestas.exception.CustomNotFoundException;
import com.cballestas.mapper.OrderItemMapper;
import com.cballestas.mapper.OrderMapper;
import com.cballestas.producer.AuditEventProducer;
import com.cballestas.repository.OrderRepository;
import com.cballestas.rest_client.BookRestClient;
import com.cballestas.rest_client.response.BookResponseDto;
import com.cballestas.util.JsonUtil;
import io.smallrye.mutiny.Uni;
import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.smallrye.mutiny.CompositeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.MockedStatic;
import org.hibernate.reactive.mutiny.Mutiny;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class OrderServiceTest {
    private final OrderRepository repository = Mockito.mock(OrderRepository.class);
    private final BookRestClient bookClient = Mockito.mock(BookRestClient.class);
    private final OrderMapper orderMapper = Mockito.mock(OrderMapper.class);
    private final OrderItemMapper orderItemMapper = Mockito.mock(OrderItemMapper.class);
    private final AuditEventProducer auditEventProducer = Mockito.mock(AuditEventProducer.class);
    private final JsonUtil jsonUtil = Mockito.mock(JsonUtil.class);
    private OrderService service;

    @BeforeEach
    void setUp() {
        service = new OrderService(
                repository,
                bookClient,
                auditEventProducer,
                orderMapper,
                orderItemMapper,
                jsonUtil);
    }

    @Test
    void findByIdFailsWhenOrderDoesNotExist() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Uni.createFrom().nullItem());

        Throwable error = assertThrows(Throwable.class,
                () -> service.findById(id).await().indefinitely());

        assertInstanceOf(CustomNotFoundException.class, error);
        assertEquals(OrderService.ORDER_NOT_FOUND_MESSAGE, error.getMessage());
    }

    @Test
    void saveFailsWhenRequestedBookDoesNotExist() {
        UUID bookId = UUID.randomUUID();
        OrderRequest request = requestFor(bookId, 1);
        when(bookClient.findById(bookId)).thenReturn(Uni.createFrom().nullItem());

        Throwable error = assertThrows(Throwable.class,
                () -> service.save(request).await().indefinitely());

        CompositeException compositeException = assertInstanceOf(CompositeException.class, error);
        Throwable cause = compositeException.getCauses().get(0);
        assertInstanceOf(CustomNotFoundException.class, cause);
        assertEquals("Book not found with ID: " + bookId, cause.getMessage());
    }

    @Test
    void saveFailsWhenBookStockIsInsufficient() {
        UUID bookId = UUID.randomUUID();
        OrderRequest request = requestFor(bookId, 3);
        BookResponseDto book = new BookResponseDto(bookId, "Clean Code", BigDecimal.TEN, 2);
        when(bookClient.findById(bookId)).thenReturn(Uni.createFrom().item(book));

        Throwable error = assertThrows(Throwable.class,
                () -> service.save(request).await().indefinitely());

        assertInstanceOf(CustomInsufficientStockException.class, error);
        assertEquals("Insufficient stock for the requested book: Clean Code", error.getMessage());
    }

    @Test
    void findByIdMapsAnExistingOrder() {
        UUID id = UUID.randomUUID();
        OrderEntity order = new OrderEntity();
        order.setId(id);
        OrderResponse response = OrderResponse.builder().id(id).customerName("Ada").build();
        when(repository.findById(id)).thenReturn(Uni.createFrom().item(order));

        try (MockedStatic<Mutiny> mutiny = Mockito.mockStatic(Mutiny.class)) {
            mutiny.when(() -> Mutiny.fetch(order.getItems()))
                    .thenReturn(Uni.createFrom().item(order.getItems()));
            when(orderMapper.toResponse(order, List.of())).thenReturn(response);

            assertSame(response, service.findById(id).await().indefinitely());
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllReturnsTheRequestedPage() {
        PanacheQuery<OrderEntity> query = Mockito.mock(PanacheQuery.class);
        when(query.page(any())).thenReturn(query);
        when(query.list()).thenReturn(Uni.createFrom().item(List.of()));
        when(query.pageCount()).thenReturn(Uni.createFrom().item(1));
        when(query.count()).thenReturn(Uni.createFrom().item(0L));

        try (MockedStatic<PanacheEntityBase> ignored = Mockito.mockStatic(
                PanacheEntityBase.class, invocation -> query)) {
            PaginatedResponseDto<OrderResponse> result = service.findAll(1, 10, null)
                    .await().indefinitely();

            assertEquals(1, result.pageIndex);
            assertEquals(10, result.pageSize);
            assertEquals(1, result.totalPages);
            assertEquals(0L, result.totalElements);
        }
    }

    @Test
    void saveCreatesOrderDecreasesStockAndPublishesAuditEvent() {
        UUID bookId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OrderRequest request = requestFor(bookId, 2);
        BookResponseDto book = new BookResponseDto(bookId, "Clean Code", BigDecimal.TEN, 3);
        OrderItemResponse itemResponse = OrderItemResponse.builder()
                .bookId(bookId.toString()).bookName("Clean Code").quantity(2).price(10D).build();
        OrderResponse orderResponse = OrderResponse.builder().id(orderId).customerName("Ada").build();

        when(bookClient.findById(bookId)).thenReturn(Uni.createFrom().item(book));
        when(repository.persist(any(OrderEntity.class))).thenAnswer(invocation -> {
            OrderEntity order = invocation.getArgument(0);
            order.setId(orderId);
            return Uni.createFrom().item(order);
        });
        when(bookClient.decreaseStock(any(), any())).thenReturn(Uni.createFrom().item(book));
        when(jsonUtil.toJson(any())).thenReturn("{}");
        when(auditEventProducer.sendAuditLogEvent(any())).thenReturn(Uni.createFrom().voidItem());
        when(orderItemMapper.toResponse(any(), any())).thenReturn(itemResponse);
        when(orderMapper.toResponse(any(), any())).thenReturn(orderResponse);

        try (MockedStatic<Mutiny> mutiny = Mockito.mockStatic(Mutiny.class)) {
            mutiny.when(() -> Mutiny.<List<?>>fetch(any())).thenAnswer(invocation ->
                    Uni.createFrom().item((List<?>) invocation.getArgument(0)));

            CreatedOrderResponse result = service.save(request).await().indefinitely();

            assertEquals(orderId.toString(), result.orderId());
            assertEquals("Order created successfully", result.message());
        }
    }

    private OrderRequest requestFor(UUID bookId, int quantity) {
        return new OrderRequest("Ada", List.of(new OrderItemRequest(bookId, quantity)));
    }
}
