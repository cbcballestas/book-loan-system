package com.cballestas.dto.response;

import java.util.List;
import java.util.UUID;
import lombok.Builder;

@Builder
public record OrderResponse(
    UUID id,
    String customerName,
    Double totalAmount,
    List<OrderItemResponse> items
) {
}
