package ru.microservicesApp.inventoryService.exception;

import java.util.UUID;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(UUID productId) {
        super("Product with id " + productId + " not found");
    }
}
