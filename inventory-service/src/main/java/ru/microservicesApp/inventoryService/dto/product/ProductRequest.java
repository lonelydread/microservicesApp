package ru.microservicesApp.inventoryService.dto.product;

import java.math.BigDecimal;

import jakarta.validation.constraints.*;

public record ProductRequest(
        @NotBlank()
        @Size(max = 255)
        String name,

        @Size(max = 255)
        String description,

        @NotNull()
        @Positive()
        @Digits(integer = 17, fraction = 2)
        BigDecimal price,

        @NotNull
        @DecimalMin("0")
        @DecimalMax("100")
        @Digits(integer = 3, fraction = 2)
        BigDecimal sale,

        @NotNull()
        @Positive()
        Integer quantity) {
}
