package ru.microservicesApp.orderService.kafka.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemEvent(UUID productId,
                             int quantity,
                             BigDecimal price,
                             BigDecimal sale,
                             BigDecimal totalPrice) {
}
