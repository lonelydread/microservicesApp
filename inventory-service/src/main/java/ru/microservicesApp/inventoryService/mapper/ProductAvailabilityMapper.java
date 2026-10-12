package ru.microservicesApp.inventoryService.mapper;

import org.springframework.stereotype.Component;
import ru.microservicesApp.inventoryService.dto.productavailability.ProductAvailabilityRequest;
import ru.microservicesApp.inventoryService.dto.productavailability.ProductAvailabilityResponse;
import ru.microservicesApp.inventoryService.entity.Product;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityRequest;
import ru.microservicesApp.inventoryService.grpc.generated.CheckAvailabilityResponse;

@Component
public class ProductAvailabilityMapper {
    public ProductAvailabilityRequest toRequest(CheckAvailabilityRequest request) {
        return new ProductAvailabilityRequest(request.getProductId(), request.getQuantity());
    }

    public ProductAvailabilityResponse toResponse(Product product, boolean available) {
        return new ProductAvailabilityResponse(available, product.getPrice(), product.getSale());
    }

    public CheckAvailabilityResponse toGrpcResponse(ProductAvailabilityResponse response) {
        return CheckAvailabilityResponse.newBuilder().setAvailable(response.available())
                .setPrice(response.price().toPlainString()).setSale(response.sale().toPlainString()).build();
    }
}
