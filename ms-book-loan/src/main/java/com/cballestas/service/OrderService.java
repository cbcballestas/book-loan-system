package com.cballestas.service;

import com.cballestas.dto.PaginatedResponseDto;
import com.cballestas.dto.StockDecreaseRequestDto;
import com.cballestas.dto.request.OrderItemRequest;
import com.cballestas.dto.request.OrderRequest;
import com.cballestas.dto.response.CreatedOrderResponse;
import com.cballestas.dto.response.OrderItemResponse;
import com.cballestas.dto.response.OrderResponse;
import com.cballestas.entities.OrderEntity;
import com.cballestas.entities.OrderItemEntity;
import com.cballestas.exception.CustomBusinessException;
import com.cballestas.exception.CustomInsufficientStockException;
import com.cballestas.exception.CustomNotFoundException;
import com.cballestas.mapper.OrderItemMapper;
import com.cballestas.mapper.OrderMapper;
import com.cballestas.producer.AuditEvent;
import com.cballestas.producer.AuditEventProducer;
import com.cballestas.repository.OrderRepository;
import com.cballestas.rest_client.BookRestClient;
import com.cballestas.rest_client.response.BookResponseDto;
import com.cballestas.util.JsonUtil;
import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.faulttolerance.CircuitBreaker;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.hibernate.reactive.mutiny.Mutiny;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@ApplicationScoped
public class OrderService {
    private final OrderRepository orderRepository;
    private final BookRestClient bookRestClient;
    private final AuditEventProducer auditEventProducer;
    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final JsonUtil jsonUtil;

    public static final String ORDER_NOT_FOUND_MESSAGE = "Order not found";
    private static final String BOOK_NOT_FOUND_MESSAGE = "Book not found with ID: ";
    private static final String INSUFFICIENT_STOCK_MESSAGE = "Insufficient stock for the requested book: %s";

    public OrderService(OrderRepository orderRepository, @RestClient BookRestClient bookRestClient, AuditEventProducer auditEventProducer, OrderMapper orderMapper, OrderItemMapper orderItemMapper, JsonUtil jsonUtil) {
        this.orderRepository = orderRepository;
        this.bookRestClient = bookRestClient;
        this.auditEventProducer = auditEventProducer;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.jsonUtil = jsonUtil;
    }

    @WithSession
    @Timed(value = "orders.get.all.timer", description = "Tiempo de ejecución para obtener todas las órdenes")
    @Counted(value = "orders.get.all.count", description = "Número de consultas de todas las órdenes")
    public Uni<PaginatedResponseDto<OrderResponse>> findAll(int pageIndex, int pageSize, String sortBy) {
        Page page = createPage(pageIndex, pageSize);
        PanacheQuery<OrderEntity> orders = findPaginatedOrders(page, createSort(sortBy));
        return toPaginatedResponse(orders, pageIndex, pageSize);
    }

    @WithSession
    @Timed(value = "orders.get.byid.timer", description = "Tiempo de ejecución para obtener una orden por ID")
    @Counted(value = "orders.get.byid.count", description = "Número de consultas de una orden por ID")
    public Uni<OrderResponse> findById(UUID id) {
        return orderRepository.findById(id)
                .flatMap(existingOrder -> {
                    if (existingOrder == null) {
                        return Uni.createFrom().failure(new CustomNotFoundException(ORDER_NOT_FOUND_MESSAGE));
                    } else {
                        return toResponse(existingOrder);
                    }
                });
    }

    @WithTransaction
    @Timeout(10_000)
    @CircuitBreaker(
            requestVolumeThreshold = 10,
            failureRatio = 0.5,
            delay = 10_000,
            skipOn = CustomBusinessException.class)
    @Timed(value = "orders.save.timer", description = "Tiempo de ejecución para guardar una orden")
    @Counted(value = "orders.save.count", description = "Número de veces que se guarda una orden")
    public Uni<CreatedOrderResponse> save(OrderRequest request) {
        Log.infov("Saving Order Request: %s", request);
        return fetchBooks(request)
                .map(books -> createOrder(request, books))
                .flatMap(orderRepository::persist)
                .flatMap(this::decreaseStock)
                .flatMap(order -> publishOrderCreatedEvent(order, request))
                .flatMap(this::toCreatedOrderResponse);
    }

    private Uni<List<BookResponseDto>> fetchBooks(OrderRequest request) {
        return Uni.join().all(
                        request.items().stream()
                                .map(this::findBook)
                                .toList())
                .andCollectFailures();
    }

    private Uni<BookResponseDto> findBook(OrderItemRequest item) {
        return bookRestClient.findById(item.bookId())
                .onItem().ifNull().failWith(() -> new CustomNotFoundException(BOOK_NOT_FOUND_MESSAGE + item.bookId()));
    }

    private OrderEntity createOrder(OrderRequest request, List<BookResponseDto> books) {
        OrderEntity order = new OrderEntity();
        order.setCustomerName(request.customerName());

        for (int index = 0; index < request.items().size(); index++) {
            order.addItem(createOrderItem(request.items().get(index), books.get(index)));
        }
        order.calculateTotalAmount();
        return order;
    }

    private OrderItemEntity createOrderItem(OrderItemRequest request, BookResponseDto book) {
        validateStock(book, request.quantity());

        OrderItemEntity item = new OrderItemEntity();
        item.setBookId(request.bookId().toString());
        item.setQuantity(request.quantity());
        item.setPrice(book.getPrice().doubleValue());
        return item;
    }

    private void validateStock(BookResponseDto book, int requestedQuantity) {
        if (book.getStock() == null || book.getStock() < requestedQuantity) {
            throw new CustomInsufficientStockException(String.format(INSUFFICIENT_STOCK_MESSAGE, book.getName()));
        }
    }

    private Uni<OrderEntity> decreaseStock(OrderEntity order) {
        return Uni.join().all(
                        order.getItems().stream()
                                .map(this::decreaseItemStock)
                                .toList())
                .andCollectFailures()
                .replaceWith(order);
    }

    private Uni<BookResponseDto> decreaseItemStock(OrderItemEntity item) {
        return bookRestClient.decreaseStock(
                UUID.fromString(item.getBookId()),
                new StockDecreaseRequestDto(item.getQuantity()));
    }

    private Uni<OrderEntity> publishOrderCreatedEvent(OrderEntity order, OrderRequest request) {
        AuditEvent event = new AuditEvent(
                "CREATE",
                "ORDER",
                jsonUtil.toJson(request),
                jsonUtil.toJson(Map.of(
                        "orderId", order.getId().toString(),
                        "customerName", order.getCustomerName(),
                        "totalAmount", order.getTotalAmount())));

        return auditEventProducer.sendAuditLogEvent(event)
                .replaceWith(order);
    }

    private Uni<CreatedOrderResponse> toCreatedOrderResponse(OrderEntity order) {
        return toResponse(order)
                .map(response -> new CreatedOrderResponse(response.id().toString(), "Order created successfully"));
    }

    private Page createPage(int pageIndex, int pageSize) {
        return Page.of(pageIndex, pageSize);
    }

    private Sort createSort(String sortBy) {
        return Sort.by(sortBy != null ? sortBy : "id");
    }

    private PanacheQuery<OrderEntity> findPaginatedOrders(Page page, Sort sort) {
        return OrderEntity.<OrderEntity>findAll(sort).page(page);
    }

    private Uni<PaginatedResponseDto<OrderResponse>> toPaginatedResponse(
            PanacheQuery<OrderEntity> orders,
            int pageIndex,
            int pageSize) {
        return Uni.combine().all().unis(
                        orders.list(),
                        orders.pageCount(),
                        orders.count())
                .asTuple()
                .flatMap(result -> toResponses(result.getItem1())
                        .map(responses -> new PaginatedResponseDto<>(
                                responses,
                                pageIndex,
                                pageSize,
                                result.getItem2(),
                                result.getItem3())));
    }

    private Uni<List<OrderResponse>> toResponses(List<OrderEntity> orders) {
        return Uni.join().all(orders.stream().map(this::toResponse).toList())
                .andCollectFailures();
    }

    private Uni<OrderResponse> toResponse(OrderEntity order) {
        return Mutiny.fetch(order.getItems())
                .flatMap(items -> {
                    List<Uni<OrderItemResponse>> itemResponses = items.stream()
                            .map(item -> bookRestClient.findById(UUID.fromString(item.getBookId()))
                                    .map(bookResponseDto -> orderItemMapper.toResponse(item, bookResponseDto)))
                            .toList();

                    return Uni.join().all(itemResponses)
                            .andCollectFailures()
                            .map(responses -> orderMapper.toResponse(order, responses));
                });
    }
}