package ru.microservicesApp.inventoryService.service;

import java.util.List;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;
import ru.microservicesApp.inventoryService.dto.product.ProductAvailability;
import ru.microservicesApp.inventoryService.entity.Product;
import ru.microservicesApp.inventoryService.repository.ProductRepository;

@Service
@Transactional(readOnly = true)
@Validated
public class ProductService {
    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "product not found"));
    }

    @Transactional
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(Long productId) {
        Product product = getProductById(productId);
        productRepository.delete(product);
    }

    public ProductAvailability checkAvailability(
            @NotNull @Positive Long productId,
            @Positive int requestedQuantity) {

        Product product = getProductById(productId);
        boolean available = product.getQuantity() != null && product.getQuantity() >= requestedQuantity;
        return new ProductAvailability(available, product.getPrice(), product.getSale());
    }
}
