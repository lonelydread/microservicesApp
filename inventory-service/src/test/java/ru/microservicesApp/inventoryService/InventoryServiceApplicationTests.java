package ru.microservicesApp.inventoryService;

import java.math.BigDecimal;

import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ru.microservicesApp.inventoryService.dto.productavailability.ProductAvailabilityRequest;
import ru.microservicesApp.inventoryService.entity.Product;
import ru.microservicesApp.inventoryService.repository.ProductRepository;
import ru.microservicesApp.inventoryService.service.ProductService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = {
        "spring.datasource.url=jdbc:h2:mem:inventory-uuid;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.liquibase.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.grpc.server.enabled=false"
})
class InventoryServiceApplicationTests {
    @Autowired ProductService productService;
    @Autowired ProductRepository repository;

    @Test
    void createsFindsChecksAndDeletesAProductByUuid() {
        Product product = new Product();
        product.setName("Keyboard");
        product.setPrice(new BigDecimal("25.50"));
        product.setSale(new BigDecimal("12.50"));
        product.setQuantity(5);
        Product saved = productService.createProduct(product);
        var productId = saved.getId();

        assertNotNull(productId);
        assertTrue(repository.findById(productId).isPresent());
        assertEquals(productId, productService.getProductById(productId).getId());
        var availability = productService.checkAvailability(new ProductAvailabilityRequest(productId, 2));
        assertTrue(availability.available());
        assertEquals(new BigDecimal("25.50"), availability.price());
        assertEquals(new BigDecimal("12.50"), availability.sale());
        assertThrows(ConstraintViolationException.class,
                () -> productService.checkAvailability(new ProductAvailabilityRequest(null, 1)));
        assertThrows(ConstraintViolationException.class,
                () -> productService.checkAvailability(new ProductAvailabilityRequest(productId, 0)));

        productService.deleteProduct(productId);
        assertFalse(repository.existsById(productId));
    }
}
