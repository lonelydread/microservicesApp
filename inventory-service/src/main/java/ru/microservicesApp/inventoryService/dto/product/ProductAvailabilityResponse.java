package ru.microservicesApp.inventoryService.dto.product;

import java.math.BigDecimal;

public record ProductAvailabilityResponse(boolean available,
                                          BigDecimal price,
                                          BigDecimal sale) {
}
