package ru.microservicesApp.inventoryService.dto.product;

import java.math.BigDecimal;

public record ProductResponse(Long id,
                              String name,
                              String description,
                              BigDecimal price,
                              Integer quantity) {
}
