package ru.microservicesApp.inventoryService.dto.productavailability;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ProductAvailabilityRequest(@NotNull UUID productId,
                                         @Positive int quantity) {
}
