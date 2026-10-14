package ru.microservicesApp.notificationService.kafka.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record OrderCreatedEvent(
        @NotNull UUID orderId,
        @NotNull UUID userId,
        @NotEmpty List<@NotNull @Valid OrderItemEvent> items,
        @NotNull Instant createdAt) {
}
