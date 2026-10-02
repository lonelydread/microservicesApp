package ru.microservicesApp.inventoryService.controller;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.microservicesApp.inventoryService.dto.product.ProductRequest;
import ru.microservicesApp.inventoryService.dto.product.ProductResponse;
import ru.microservicesApp.inventoryService.mapper.ProductMapper;
import ru.microservicesApp.inventoryService.service.ProductService;

@RestController
@RequestMapping("/api/products")
public class ProductController {
    private final ProductService productService;
    private final ProductMapper productMapper;

    public ProductController(ProductService productService, ProductMapper productMapper) {
        this.productService = productService;
        this.productMapper = productMapper;
    }

    @GetMapping
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts().stream()
                .map(productMapper::toResponse)
                .toList();
    }

    @GetMapping("/{product_id}")
    public ProductResponse getProductById(@PathVariable("product_id") @Positive() Long productId) {
        return productMapper.toResponse(productService.getProductById(productId));
    }

    @PostMapping
    public void createProduct(@Valid @RequestBody ProductRequest request) {
        productMapper.toResponse(productService.createProduct(
                productMapper.toEntity(request)));
    }

    @DeleteMapping("/{product_id}")
    public void deleteProduct(@PathVariable("product_id") @Positive() Long productId) {
        productService.deleteProduct(productId);
    }
}
