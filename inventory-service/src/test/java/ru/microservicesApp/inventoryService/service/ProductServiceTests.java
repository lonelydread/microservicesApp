package ru.microservicesApp.inventoryService.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.microservicesApp.inventoryService.controller.ProductController;
import ru.microservicesApp.inventoryService.dto.product.ProductRequest;
import ru.microservicesApp.inventoryService.entity.Product;
import ru.microservicesApp.inventoryService.mapper.ProductMapper;
import ru.microservicesApp.inventoryService.repository.ProductRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceTests {
    private ProductRepository repository;
    private ProductController controller;

    @BeforeEach
    void setUp() {
        repository = mock(ProductRepository.class);
        controller = new ProductController(new ProductService(repository), new ProductMapper());
    }

    @Test
    void returnsEmptyListWhenThereAreNoProducts() {
        when(repository.findAll()).thenReturn(List.of());
        assertTrue(controller.getAllProducts().isEmpty());
    }

    @Test
    void returnsProductFromRepository() {
        Product product = new Product();
        product.setId(7L);
        product.setName("Keyboard");
        when(repository.findById(7L)).thenReturn(Optional.of(product));
        var response = controller.getProductById(7L);
        assertEquals(7L, response.id());
        assertEquals("Keyboard", response.name());
    }

    @Test
    void createsProductAndReturnsGeneratedIdAndLocation() {
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            assertNull(product.getId());
            assertEquals("Keyboard", product.getName());
            assertEquals("USB keyboard", product.getDescription());
            assertEquals(new BigDecimal("25.50"), product.getPrice());
            assertEquals(3, product.getQuantity());
            product.setId(7L);
            return product;
        });
        var response = controller.createProduct(
                new ProductRequest("Keyboard", "USB keyboard", new BigDecimal("25.50"), 3));
        assertEquals(201, response.getStatusCode().value());
        assertEquals("/api/products/7", response.getHeaders().getLocation().toString());
        assertEquals(7L, response.getBody().id());
        assertEquals(new BigDecimal("25.50"), response.getBody().price());
        verify(repository).save(any(Product.class));
    }

    @Test
    void deletesExistingProductAndReturnsNoContent() {
        Product product = new Product();
        when(repository.findById(7L)).thenReturn(Optional.of(product));
        var response = controller.deleteProduct(7L);
        assertEquals(204, response.getStatusCode().value());
        assertNull(response.getBody());
        verify(repository).delete(product);
    }

    @Test
    void missingProductMapsToNotFound() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        var exception = assertThrows(ProductNotFoundException.class,
                () -> controller.getProductById(7L));
        var response = new GlobalExceptionHandler().handleProductNotFound(exception);
        assertEquals(404, response.getStatusCode().value());
        assertEquals("Product with id 7 not found", response.getBody().get("message"));
    }

    @Test
    void doesNotDeleteWhenProductIsMissing() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        assertThrows(ProductNotFoundException.class, () -> controller.deleteProduct(7L));
        verify(repository, never()).delete(any(Product.class));
    }
}
