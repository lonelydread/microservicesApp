package ru.microservicesApp.inventoryService.dto.productavailability;

import java.math.BigDecimal;

public record ProductAvailabilityResponse(boolean available,
                                          BigDecimal price,
                                          BigDecimal sale) {
}
