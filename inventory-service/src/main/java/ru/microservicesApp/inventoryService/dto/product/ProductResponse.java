package ru.microservicesApp.inventoryService.dto.product;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductResponse(UUID id,
                              String name,
                              String description,
                              BigDecimal price,
                              BigDecimal sale,
                              Integer quantity) {
}
