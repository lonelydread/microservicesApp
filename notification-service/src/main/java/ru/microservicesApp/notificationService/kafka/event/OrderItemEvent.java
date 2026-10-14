package ru.microservicesApp.notificationService.kafka.event;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

public record OrderItemEvent(
        @NotNull UUID productId,
        @NotNull @Positive Integer quantity,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal price,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal sale,
        @NotNull @PositiveOrZero @Digits(integer = 27, fraction = 2) BigDecimal totalPrice) {
}
