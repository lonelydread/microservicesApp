package ru.microservicesApp.inventoryService.mapper;

import org.springframework.stereotype.Component;
import ru.microservicesApp.inventoryService.dto.product.ProductRequest;
import ru.microservicesApp.inventoryService.dto.product.ProductResponse;
import ru.microservicesApp.inventoryService.entity.Product;

@Component
public class ProductMapper {
    public Product toEntity(ProductRequest request) {
        Product product = new Product();
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setSale(request.sale());
        product.setQuantity(request.quantity());
        return product;
    }

    public ProductResponse toResponse(Product product) {
        return new ProductResponse(product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getSale(), product.getQuantity());
    }
}
