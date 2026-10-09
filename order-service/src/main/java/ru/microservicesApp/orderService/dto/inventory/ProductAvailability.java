package ru.microservicesApp.orderService.dto.inventory;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductAvailability(
        boolean available,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal price,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal sale) {
}
