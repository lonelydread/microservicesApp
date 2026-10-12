package ru.microservicesApp.inventoryService.dto.product;

import java.math.BigDecimal;

public record ProductAvailability(boolean available,
                                  BigDecimal price,
                                  BigDecimal sale) {
}
