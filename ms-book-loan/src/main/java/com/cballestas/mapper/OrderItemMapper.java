package com.cballestas.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import com.cballestas.dto.response.OrderItemResponse;
import com.cballestas.entities.OrderItemEntity;
import com.cballestas.rest_client.response.BookResponseDto;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI,
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderItemMapper {

    @Mapping(source = "bookResponseDto.id", target = "bookId")
    @Mapping(source = "bookResponseDto.name", target = "bookName")
    @Mapping(source = "orderItemEntity.price", target = "price")
    @Mapping(source = "orderItemEntity.quantity", target = "quantity")
    OrderItemResponse toResponse(OrderItemEntity orderItemEntity, BookResponseDto bookResponseDto);
}
