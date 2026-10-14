package ru.microservicesApp.inventoryService.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.microservicesApp.inventoryService.entity.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {
}
