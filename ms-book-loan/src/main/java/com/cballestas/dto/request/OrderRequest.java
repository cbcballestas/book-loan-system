package com.cballestas.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Builder;

import java.util.List;

@Builder
public record OrderRequest(
        @NotBlank(message = "Customer Name is required")
        String customerName,

        @Valid
        @NotEmpty(message = "Order must have at least one item")
        List<OrderItemRequest> items
) {
}
