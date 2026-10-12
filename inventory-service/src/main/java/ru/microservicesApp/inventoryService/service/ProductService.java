package ru.microservicesApp.inventoryService.service;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import ru.microservicesApp.inventoryService.dto.productavailability.ProductAvailabilityRequest;
import ru.microservicesApp.inventoryService.dto.productavailability.ProductAvailabilityResponse;
import ru.microservicesApp.inventoryService.entity.Product;
import ru.microservicesApp.inventoryService.exception.ProductNotFoundException;
import ru.microservicesApp.inventoryService.repository.ProductRepository;
import ru.microservicesApp.inventoryService.mapper.ProductAvailabilityMapper;

@Service
@Transactional(readOnly = true)
@Validated
public class ProductService {
    private final ProductRepository productRepository;
    private final ProductAvailabilityMapper productAvailabilityMapper;

    public ProductService(ProductRepository productRepository, ProductAvailabilityMapper productAvailabilityMapper) {
        this.productRepository = productRepository;
        this.productAvailabilityMapper = productAvailabilityMapper;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProductById(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    @Transactional
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public void deleteProduct(UUID productId) {
        Product product = getProductById(productId);
        productRepository.delete(product);
    }

    public ProductAvailabilityResponse checkAvailability(@Valid ProductAvailabilityRequest request) {
        Product product = getProductById(request.productId());
        boolean available = product.getQuantity() != null && product.getQuantity() >= request.quantity();
        return productAvailabilityMapper.toResponse(product, available);
    }
}
