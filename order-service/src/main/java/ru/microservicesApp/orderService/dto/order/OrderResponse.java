package ru.microservicesApp.orderService.dto.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(UUID orderId,
                            UUID userId,
                            List<OrderItemResponse> items,
                            BigDecimal totalPrice,
                            Instant createdAt) {
}
