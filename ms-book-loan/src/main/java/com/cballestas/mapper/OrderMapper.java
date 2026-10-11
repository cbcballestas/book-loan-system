package com.cballestas.mapper;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import com.cballestas.dto.response.OrderItemResponse;
import com.cballestas.dto.response.OrderResponse;
import com.cballestas.entities.OrderEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI, uses = {OrderItemMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface OrderMapper {

    @Mapping(source = "orderEntity.id", target = "id")
    @Mapping(source = "orderEntity.customerName", target = "customerName")
    @Mapping(source = "orderEntity.totalAmount", target = "totalAmount")
    @Mapping(source = "items", target = "items")
    OrderResponse toResponse(OrderEntity orderEntity, List<OrderItemResponse> items);
}
