package ru.microservicesApp.orderService.kafka.event;

import java.math.BigDecimal;

public record OrderItemEvent(long productId,
                             int quantity,
                             BigDecimal price,
                             BigDecimal sale,
                             BigDecimal totalPrice) {
}
