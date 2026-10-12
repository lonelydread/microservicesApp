package ru.microservicesApp.inventoryService.dto.productavailability;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductAvailabilityRequest(@NotNull @Positive Long productId,
                                         @Positive int quantity) {
}
