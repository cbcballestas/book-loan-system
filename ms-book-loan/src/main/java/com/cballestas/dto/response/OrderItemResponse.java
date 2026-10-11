package com.cballestas.dto.response;

import lombok.Builder;

@Builder
public record OrderItemResponse(
       String bookId,
       String bookName,
       Integer quantity,
       Double price
) {
}
