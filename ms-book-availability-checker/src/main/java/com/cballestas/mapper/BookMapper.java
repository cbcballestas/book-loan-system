package com.cballestas.mapper;

import com.cballestas.dto.BookDto;
import com.cballestas.dto.BookRequestDto;
import com.cballestas.entities.BookEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.CDI)
public interface BookMapper {
    BookDto toDto(BookEntity entity);

    @Mapping(target = "id", ignore = true)
    BookEntity toEntity(BookRequestDto dto);
}
