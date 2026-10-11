package com.cballestas.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class BookRequestDto {

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 60,
            message = "El nombre debe tener entre {min} y {max} caracteres")
    private String name;

    @NotNull(message = "El precio es obligatorio")
    private BigDecimal price;

    @Min(value = 1, message = "El stock debe ser mayor o igual a {value}")
    private Integer stock;
}
