package ru.microservicesApp.orderService.dto.order;

import java.math.BigDecimal;

public record OrderItemResponse(long productId,
                                int quantity,
                                BigDecimal price,
                                BigDecimal sale,
                                BigDecimal totalPrice) {
}
