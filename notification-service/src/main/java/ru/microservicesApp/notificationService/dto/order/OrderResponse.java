package ru.microservicesApp.notificationService.dto.order;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderResponse(Long id,
                            UUID orderId,
                            UUID productId,
                            Integer quantity,
                            BigDecimal price,
                            BigDecimal sale,
                            BigDecimal totalPrice,
                            UUID userId) {
}
