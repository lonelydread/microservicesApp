package ru.microservicesApp.orderService.dto.order;

import java.time.Instant;
import java.util.UUID;

public record OrderResponse(UUID orderId, Instant createdAt) {
}
