package com.cballestas.dto.response;

import lombok.Builder;

@Builder
public record CreatedOrderResponse(
        String orderId,
        String message
) {
}
