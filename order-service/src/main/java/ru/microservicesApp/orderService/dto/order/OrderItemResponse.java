package ru.microservicesApp.orderService.dto.order;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemResponse(UUID productId,
                                int quantity,
                                BigDecimal price,
                                BigDecimal sale,
                                BigDecimal totalPrice) {
}
