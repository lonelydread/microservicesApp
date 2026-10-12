package ru.microservicesApp.inventoryService.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.microservicesApp.inventoryService.controller.ProductController;
import ru.microservicesApp.inventoryService.dto.product.ProductRequest;
import ru.microservicesApp.inventoryService.entity.Product;
import ru.microservicesApp.inventoryService.exception.ProductNotFoundException;
import ru.microservicesApp.inventoryService.mapper.ProductMapper;
import ru.microservicesApp.inventoryService.mapper.ProductAvailabilityMapper;
import ru.microservicesApp.inventoryService.repository.ProductRepository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductServiceTests {
    private static final UUID PRODUCT_ID = UUID.randomUUID();
    private ProductRepository repository;
    private ProductController controller;

    @BeforeEach
    void setUp() {
        repository = mock(ProductRepository.class);
        controller = new ProductController(new ProductService(repository, new ProductAvailabilityMapper()), new ProductMapper());
    }

    @Test
    void returnsEmptyListWhenThereAreNoProducts() {
        when(repository.findAll()).thenReturn(List.of());
        assertTrue(controller.getAllProducts().isEmpty());
    }

    @Test
    void returnsProductFromRepository() {
        Product product = new Product();
        product.setId(PRODUCT_ID);
        product.setName("Keyboard");
        product.setSale(new BigDecimal("12.50"));
        when(repository.findById(7L)).thenReturn(Optional.of(product));
        var response = controller.getProductById(7L);
        assertEquals(PRODUCT_ID, response.id());
        assertEquals("Keyboard", response.name());
        assertEquals(new BigDecimal("12.50"), response.sale());
    }

    @Test
    void createsProductWithSale() {
        when(repository.save(any(Product.class))).thenAnswer(invocation -> {
            Product product = invocation.getArgument(0);
            assertNull(product.getId());
            assertEquals("Keyboard", product.getName());
            assertEquals("USB keyboard", product.getDescription());
            assertEquals(new BigDecimal("25.50"), product.getPrice());
            assertEquals(new BigDecimal("12.50"), product.getSale());
            assertEquals(3, product.getQuantity());
            product.setId(PRODUCT_ID);
            return product;
        });
        controller.createProduct(new ProductRequest("Keyboard", "USB keyboard",
                new BigDecimal("25.50"), new BigDecimal("12.50"), 3));
        verify(repository).save(any(Product.class));
    }

    @Test
    void deletesExistingProduct() {
        Product product = new Product();
        when(repository.findById(7L)).thenReturn(Optional.of(product));
        controller.deleteProduct(7L);
        verify(repository).delete(product);
    }

    @Test
    void missingProductThrowsProductNotFoundException() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        var exception = assertThrows(ProductNotFoundException.class,
                () -> controller.getProductById(7L));
        assertEquals("Product with id 7 not found", exception.getMessage());
    }

    @Test
    void doesNotDeleteWhenProductIsMissing() {
        when(repository.findById(7L)).thenReturn(Optional.empty());
        assertThrows(ProductNotFoundException.class, () -> controller.deleteProduct(7L));
        verify(repository, never()).delete(any(Product.class));
    }
}
